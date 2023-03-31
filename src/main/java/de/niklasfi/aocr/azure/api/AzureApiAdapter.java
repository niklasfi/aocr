package de.niklasfi.aocr.azure.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.niklasfi.aocr.azure.core.concurrent.CloseableReentrantLock;
import de.niklasfi.aocr.azure.core.http.HttpAcceptedResponseHandler;
import de.niklasfi.aocr.azure.core.http.HttpEntityResponseHandler;
import de.niklasfi.aocr.azure.core.http.HttpRetryResponseHandler;
import de.niklasfi.aocr.azure.core.http.HttpRetryResponseHandler.HttpResponseRetryException;
import de.niklasfi.aocr.azure.dto.Language;
import de.niklasfi.aocr.azure.dto.ReadResultHeader;
import de.niklasfi.aocr.azure.dto.Status;
import lombok.RequiredArgsConstructor;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;

import java.io.IOException;
import java.time.Duration;
import java.time.ZonedDateTime;

@RequiredArgsConstructor
public class AzureApiAdapter {
    private final AzureUriBuilder uriBuilder;
    private final String subscriptionKey;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private final CloseableReentrantLock sharedLock = new CloseableReentrantLock();


    private void interceptSetContentType(HttpRequest request, ContentType contentType) {
        request.setHeader("Content-Type", contentType);
    }

    private void interceptSetSubscriptionKey(HttpRequest request) {
        request.setHeader("Ocp-Apim-Subscription-Key", subscriptionKey);
    }

    public OperationLocation analyze(byte[] data, ContentType contentType) throws IOException {
        return analyze(data, contentType, null);
    }

    public OperationLocation analyze(byte[] data, ContentType contentType, Language language) throws IOException {
        final var request = ClassicRequestBuilder
                .post(uriBuilder.readAnalyze(language))
                .setEntity(data, contentType)
                .build();
        interceptSetSubscriptionKey(request);
        final var locationHeader = httpClient.execute(request, new HttpAcceptedResponseHandler("Operation-Location"));
        return OperationLocation.fromFullUrl(locationHeader);
    }

    public OperationLocation waitAnalyze(byte[] data, ContentType contentType, Language language, Duration timeout) throws IOException {
        final var begin = ZonedDateTime.now();

        do {
            // ensure other analyze requests are locked out
            try (final var lock = sharedLock.lockAsResource()) {
                try {
                    return analyze(data, contentType, language);
                } catch (HttpAcceptedResponseHandler.HttpResponseRetryException e) {
                    try {
                        Thread.sleep(e.getRetryAfter());
                    } catch (InterruptedException e2) {
                        throw new RuntimeException(e2);
                    }
                } catch (IOException e){
                    throw e;
                }
            }
        } while (Duration.between(begin, ZonedDateTime.now()).compareTo(timeout) <= 0);
        return null;
    }

    public ReadResultHeader result(OperationLocation operationLocation) throws IOException {
        final var request = ClassicRequestBuilder
                .get(uriBuilder.readResult(operationLocation.operationId()))
                .build();
        interceptSetSubscriptionKey(request);
        interceptSetContentType(request, ContentType.APPLICATION_JSON);

        try {
            return httpClient.execute(request, new HttpEntityResponseHandler<>(objectMapper, ReadResultHeader.class));
        } catch (HttpResponseRetryException e){
            throw e;
        } catch (IOException e){
            throw e;
        }
    }

    public ReadResultHeader waitResult(OperationLocation operationLocation, Duration timeout) throws IOException {
        final var begin = ZonedDateTime.now();
        var delay = Duration.ofSeconds(1);

        ReadResultHeader result = null;
        do {
            try (final var lock = sharedLock.lockAsResource()) {
                try {
                    result = result(operationLocation);
                } catch (HttpRetryResponseHandler.HttpResponseRetryException e) {
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException e2) {
                        throw new RuntimeException(e2);
                    }
                    continue;
                }
            }
            if (result.status() != Status.RUNNING) {
                return result;
            }
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            delay = delay.multipliedBy(2);
        } while (Duration.between(begin, ZonedDateTime.now()).compareTo(timeout) <= 0);
        return result;
    }
}

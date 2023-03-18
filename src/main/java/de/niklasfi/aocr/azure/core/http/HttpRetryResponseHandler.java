package de.niklasfi.aocr.azure.core.http;

import lombok.Getter;
import org.apache.hc.client5.http.HttpResponseException;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

public abstract class HttpRetryResponseHandler<T> implements HttpClientResponseHandler<T> {
    @Override
    public T handleResponse(ClassicHttpResponse response) throws HttpException, IOException {
        if (response.getCode() == HttpStatus.SC_TOO_MANY_REQUESTS) {
            final var retryAfter = Optional.of(response)
                    .map(r -> r.getFirstHeader("Retry-After"))
                    .map(NameValuePair::getValue)
                    .map(Integer::parseInt)
                    .map(Duration::ofSeconds);

            if (retryAfter.isPresent()) {
                throw new HttpRetryResponseHandler.HttpResponseRetryException(
                        response.getCode(),
                        retryAfter.get(),
                        "retry after duration has passed"
                );
            } else {
                throw new HttpResponseException(response.getCode(), "could not parse retry-after");
            }
        }
        return handleResponseNo429(response);
    }

    protected abstract T handleResponseNo429(ClassicHttpResponse response) throws HttpException, IOException;

    public static class HttpResponseRetryException extends HttpResponseException {

        @Getter
        private final Duration retryAfter;

        public HttpResponseRetryException(int statusCode, Duration retryAfter, String reasonPhrase) {
            super(statusCode, reasonPhrase);
            this.retryAfter = retryAfter;
        }
    }
}
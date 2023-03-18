package de.niklasfi.aocr.azure.core.http;

import lombok.RequiredArgsConstructor;
import org.apache.hc.client5.http.HttpResponseException;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.NameValuePair;

import java.util.Optional;

@RequiredArgsConstructor
public class HttpAcceptedResponseHandler extends HttpRetryResponseHandler<String> {

    private final String headerField;

    @Override
    public String handleResponseNo429(ClassicHttpResponse response) throws HttpResponseException, HttpException {
        if (response.getCode() != HttpStatus.SC_ACCEPTED) {
            throw new HttpResponseException(response.getCode(), "response is not accepted");
        }
        return Optional.of(response)
                .map(r -> r.getFirstHeader(headerField))
                .map(NameValuePair::getValue)
                .orElseThrow(() -> new HttpException("response does not have specified header"));
    }
}

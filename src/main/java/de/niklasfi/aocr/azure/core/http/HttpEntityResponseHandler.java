package de.niklasfi.aocr.azure.core.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RequiredArgsConstructor
public class HttpEntityResponseHandler<T> extends HttpRetryResponseHandler<T> {
    private final ObjectMapper objectMapper;

    private final Class<? extends T> cls;
    @Override
    public T handleResponseNo429(ClassicHttpResponse response) throws HttpException, IOException {
        if(response.getCode() != HttpStatus.SC_OK){
            throw new HttpException();
        }
        final var str = new String(response.getEntity().getContent().readAllBytes(), StandardCharsets.UTF_8);
        return objectMapper.readValue(str, cls);
    }
}

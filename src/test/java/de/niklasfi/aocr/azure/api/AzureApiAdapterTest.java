package de.niklasfi.aocr.azure.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.niklasfi.aocr.azure.dto.Language;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;

@Slf4j
class AzureApiAdapterTest {
    @Test
    public void analyzeWaitResult() {
        final String endpoint = System.getenv("AOCR_AZURE_ENDPOINT");
        final String key = System.getenv("AOCR_AZURE_KEY");

        if (endpoint == null || key == null) {
            log.warn("not executing test. Environment variables AOCR_AZURE_ENDPOINT and AOCR_AZURE_KEY must be set");
            return;
        }

        final AzureUriBuilder uriBuilder = new AzureUriBuilder(endpoint);
        final ObjectMapper objectMapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();
        final AzureApiAdapter api = new AzureApiAdapter(uriBuilder, key, HttpClients.createDefault(), objectMapper);

        final byte[] pdfBytes;
        try (final var is = getClass().getResourceAsStream("/LaTeXTemplates_tufte-essay_v2.0.pdf")) {
            pdfBytes = Objects.requireNonNull(is).readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try {
            final var location = api.analyze(pdfBytes, ContentType.APPLICATION_PDF, Language.GERMAN);
            final var result = api.waitResult(location, Duration.ofSeconds(30));
            System.out.println(location.operationId());
            System.out.println(result.status());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
package de.niklasfi.aocr.azure.api;

import de.niklasfi.aocr.azure.dto.Language;
import lombok.RequiredArgsConstructor;
import org.apache.commons.text.StringSubstitutor;
import org.apache.hc.core5.net.URIBuilder;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

@RequiredArgsConstructor
public class AzureUriBuilder {

    private final String endpoint;

    private URIBuilder builder(String url, Map<String, String> substitution) {
        final var ss = new StringSubstitutor(substitution);

        try {
            return new URIBuilder(ss.replace(url));
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public URI readAnalyze(Language language) {
        final var builder = builder("${endpoint}/vision/v3.2/read/analyze", Map.ofEntries(
                Map.entry("endpoint", endpoint)
        ));
        if (language != null) {
            builder.addParameter("language", language.code());
        }
        try {
            return builder.build();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public URI readResult(String operationId) {
        final var builder = builder("${endpoint}/vision/v3.2/read/analyzeResults/${operationId}", Map.ofEntries(
                Map.entry("endpoint", endpoint),
                Map.entry("operationId", operationId)
        ));
        try {
            return builder.build();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}

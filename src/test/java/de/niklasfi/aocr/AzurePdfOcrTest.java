package de.niklasfi.aocr;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.niklasfi.aocr.azure.api.AzureApiAdapter;
import de.niklasfi.aocr.azure.api.AzureUriBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.rendering.ImageType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Objects;

@Slf4j
class AzurePdfOcrTest {
    @Test
    void ocr() {
        final String endpoint = System.getenv("AOCR_AZURE_ENDPOINT");
        final String key = System.getenv("AOCR_AZURE_KEY");

        if (endpoint == null || key == null) {
            log.warn("not executing test. Environment variables AOCR_AZURE_ENDPOINT and AOCR_AZURE_KEY must be set");
            return;
        }

        final var apiAdapter = new AzureApiAdapter(
                new AzureUriBuilder(endpoint),
                key,
                HttpClients.createDefault(),
                JsonMapper.builder().addModule(new JavaTimeModule()).build()
        );

        final AzurePdfOcr azurePdfOcr = new AzurePdfOcr(
                apiAdapter,
                new PdfImageRenderer(300, ImageType.BINARY),
                new FileUtil(),
                (doc) -> PDType1Font.HELVETICA
        );
        azurePdfOcr.ocr("src/test/resources/LaTeXTemplates_tufte-essay_v2.0.pdf", "/tmp/out.pdf");
    }
}

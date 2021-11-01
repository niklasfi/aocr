package de.niklasfi.aocr;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.rendering.ImageType;
import org.junit.jupiter.api.Test;

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

        final AzurePdfOcr azurePdfOcr = new AzurePdfOcr(
                new AzureApiHandler(endpoint, key),
                new PdfImageRenderer(300, ImageType.BINARY),
                new PdfIoUtil(),
                new FileUtil()
        );
        azurePdfOcr.ocr("src/test/resources/LaTeXTemplates_tufte-essay_v2.0.pdf", "/tmp/out.pdf");
    }
}
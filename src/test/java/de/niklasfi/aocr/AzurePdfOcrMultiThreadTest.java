package de.niklasfi.aocr;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.rendering.ImageType;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
class AzurePdfOcrMultiThreadTest {

    final private ExecutorService executor = Executors.newFixedThreadPool(5);

    @Test
    void ocr() {
        final String endpoint = System.getenv("AOCR_AZURE_ENDPOINT");
        final String key = System.getenv("AOCR_AZURE_KEY");

        if (endpoint == null || key == null) {
            log.warn("not executing test. Environment variables AOCR_AZURE_ENDPOINT and AOCR_AZURE_KEY must be set");
            return;
        }

        final var apiHandler = new AzureApiHandler(endpoint, key);

        final var futures = IntStream.range(0, 10).mapToObj((i) ->
                executor.submit(() -> {
                    final AzurePdfOcr azurePdfOcr = new AzurePdfOcr(
                            apiHandler,
                            new PdfImageRenderer(300, ImageType.RGB),
                            new PdfIoUtil(),
                            new FileUtil()
                    );
                    log.info("thread {}: submitting job", i);
                    azurePdfOcr.ocr("src/test/resources/LaTeXTemplates_tufte-essay_v2.0.pdf", "/tmp/out-%s.pdf".formatted(i));
                    log.info("thread {}: job complete", i);
                })).collect(Collectors.toList());
        futures.forEach(fut -> {
            try {
                fut.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
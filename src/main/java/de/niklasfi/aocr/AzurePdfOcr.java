package de.niklasfi.aocr;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.UUID;

@Slf4j
public class AzurePdfOcr {
    private final AzureApiHandler apiHandler;
    private final PdfImageRetriever pdfImageRetriever;
    private final PdfIoUtil pdfUtil;
    private final FileUtil fileUtil;

    public AzurePdfOcr(AzureApiHandler apiHandler, PdfImageRetriever pdfImageRetriever, PdfIoUtil pdfUtil, FileUtil fileUtil) {
        this.apiHandler = apiHandler;
        this.pdfImageRetriever = pdfImageRetriever;
        this.pdfUtil = pdfUtil;
        this.fileUtil = fileUtil;
    }

    public byte[] ocr(byte[] inputPdf) {
        final var trace = new TracebackInfo(UUID.randomUUID(), null);
        final var run = new AzurePdfAnnotator();

        log.trace("{} parsing input pdf", trace);
        final var pdDocIn = pdfUtil.readPdf(inputPdf);
        log.debug("{} parsed input pdf", trace);

        log.trace("{} retrieving images from input pdf", trace);
        final var images = pdfImageRetriever.getImages(pdDocIn, trace)
                .filter(cont -> cont.data().isPresent())
                .map(cont -> new TracebackContainer<>(cont.trace(), cont.data().get()));
        log.debug("{} retrieved all images from input pdf", trace);

        log.trace("{} calling azure read api for annotations", trace);
        final var annotations = images.map(contImg -> apiHandler.getAnnotationsForImageWithRetry(contImg.data(), 5, contImg.trace()));
        log.debug("{} received all annotations", trace);

        log.trace("{} rendering output pdf", trace);
        final var pdDocOut = annotations.reduce(new PDDocument(), (acc, cur) -> {
            run.addPageToDocument(acc, cur);
            return acc;
        }, (acc1, acc2) -> {
            throw new RuntimeException("%s cannot operate on parallel streams".formatted(trace));
        });
        log.debug("{} pdf rendered successfully", trace);

        log.trace("{} saving output pdf into buffer", trace);
        final var bytesOut = pdfUtil.saveAndClosePdf(pdDocOut);
        log.debug("{} saved output pdf into buffer successful", trace);

        return bytesOut;
    }

    public InputStream ocr(InputStream inputStream) {
        final var bytesIn = fileUtil.readStream(inputStream);
        final var bytesOut = ocr(bytesIn);
        return new ByteArrayInputStream(bytesOut);
    }

    public void ocr(String inputFilePath, String outputFilePath) {
        final var bytesIn = fileUtil.readFile(inputFilePath);
        final var bytesOut = ocr(bytesIn);

        fileUtil.writeFile(outputFilePath, bytesOut);
    }
}

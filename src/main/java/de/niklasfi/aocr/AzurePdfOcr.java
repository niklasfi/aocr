package de.niklasfi.aocr;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Optional;

@Slf4j
public class AzurePdfOcr {
    private final String azureEndpoint;
    private final String azureSubscriptionKey;

    private final PdfImageRetriever pdfImageRetriever;
    private final PdfIoUtil pdfUtil;
    private final FileUtil fileUtil;

    public AzurePdfOcr(String azureEndpoint, String azureSubscriptionKey, PdfImageRetriever pdfImageRetriever, PdfIoUtil pdfUtil, FileUtil fileUtil) {
        this.azureEndpoint = azureEndpoint;
        this.azureSubscriptionKey = azureSubscriptionKey;
        this.pdfImageRetriever = pdfImageRetriever;
        this.pdfUtil = pdfUtil;
        this.fileUtil = fileUtil;
    }

    public byte[] ocr(byte[] inputPdf) {
        final var run = new AzurePdfAnnotator(azureEndpoint, azureSubscriptionKey);

        log.trace("parsing input pdf");
        final var pdDocIn = pdfUtil.readPdf(inputPdf);
        log.debug("parsed input pdf");

        log.trace("retrieving images from input pdf");
        final var images = pdfImageRetriever.getImages(pdDocIn).filter(Optional::isPresent).map(Optional::get);
        log.debug("retrieved all images from input pdf");

        log.trace("calling azure read api for annotations");
        final var annotations = images.map(img -> run.getAnnotationsForImageWithRetry(img, 5));
        log.debug("received all annotations");

        log.trace("rendering output pdf");
        final var pdDocOut = annotations.reduce(new PDDocument(), (acc, cur) -> {
            run.addPageToDocument(acc, cur);
            return acc;
        }, (acc1, acc2) -> {
            throw new RuntimeException("cannot operate on parallel streams");
        });
        log.debug("pdf rendered successfully");

        log.trace("saving output pdf into buffer");
        final var bytesOut = pdfUtil.savePdf(pdDocOut);
        log.debug("saved output pdf into buffer successful");

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

        log.trace("writing buffer to file");
        fileUtil.writeFile(outputFilePath, bytesOut);
        log.debug("buffer successfully written to file");
    }
}

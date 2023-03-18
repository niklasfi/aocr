package de.niklasfi.aocr;

import de.niklasfi.aocr.azure.api.AzureApiAdapter;
import de.niklasfi.aocr.azure.dto.ReadResultHeader;
import de.niklasfi.aocr.azure.dto.Status;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.core5.http.ContentType;
import org.apache.pdfbox.pdmodel.PDDocument;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
public class AzurePdfOcr {
    private final AzureApiAdapter apiAdapter;
    private final PdfImageRetriever pdfImageRetriever;
    private final PdfIoUtil pdfUtil;
    private final FileUtil fileUtil;

    public byte[] ocr(byte[] inputPdf) {
        final var run = new AzurePdfAnnotator();

        log.trace("parsing input pdf");
        final var pdDocIn = pdfUtil.readPdf(inputPdf);
        log.debug("parsed input pdf");

        log.trace("retrieving images from input pdf");
        final var images = pdfImageRetriever.getImages(pdDocIn)
                .filter(Optional::isPresent)
                .map(Optional::get);
        log.debug("retrieved all images from input pdf");

        log.trace("calling azure read api for annotations");
        final var annotations = images.map(contImg -> {
                    final byte[] png;
                    try (final var is = new ByteArrayOutputStream()) {
                        ImageIO.write(contImg, "png", is);
                        png = is.toByteArray();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    final ReadResultHeader resultHeader;
                    try {
                        final var loc = apiAdapter.waitAnalyze(png, ContentType.IMAGE_PNG, null, Duration.ofSeconds(300));
                        resultHeader = apiAdapter.waitResult(loc, Duration.ofSeconds(30));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    if (resultHeader != null && resultHeader.status() == Status.SUCCEEDED) {
                        return new AnnotatedImage(contImg, resultHeader.analyzeResult());
                    }
                    return null;
                })
                .filter(Objects::nonNull);
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
        final var bytesOut = pdfUtil.saveAndClosePdf(pdDocOut);
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

        fileUtil.writeFile(outputFilePath, bytesOut);
    }
}

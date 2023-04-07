package de.niklasfi.aocr;

import de.niklasfi.aocr.azure.api.AzureApiAdapter;
import de.niklasfi.aocr.azure.dto.AnalyzeResult;
import de.niklasfi.aocr.azure.dto.ReadResultHeader;
import de.niklasfi.aocr.azure.dto.Status;
import java.util.function.Function;
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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.pdfbox.pdmodel.font.PDFont;

@Slf4j
@RequiredArgsConstructor
public class AzurePdfOcr {
    private final AzureApiAdapter apiAdapter;
    private final PdfImageRetriever pdfImageRetriever;
    private final FileUtil fileUtil;
    private final Function<PDDocument, PDFont> fontLoader;

    public record PdfAndAnnotations(byte[] pdfData, List<AnalyzeResult> analyzeResults) {

    }

    public AnalyzeResult analyzeResultOnly(byte[] inputPdf) {
        try {
            final var loc = apiAdapter.waitAnalyze(inputPdf, ContentType.APPLICATION_PDF, null, Duration.ofSeconds(300));
            final var resultHeader = apiAdapter.waitResult(loc, Duration.ofSeconds(30));
            return resultHeader.analyzeResult();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public PdfAndAnnotations ocrGetAnalyzeResults(byte[] inputPdf) {
        final var run = new AzurePdfAnnotator();

        try (
                final var pdDocIn = PDDocument.load(inputPdf);
                final var pdDocOut = new PDDocument();
                final var os = new ByteArrayOutputStream()
        ) {
            // load font
            final var font = fontLoader.apply(pdDocOut);

            // process pages
            final var analyzeResults = pdfImageRetriever
                    // get images
                    .getImages(pdDocIn)
                    // process using azure
                    .map(pageContainer -> {
                        final var contImgOpt = pageContainer.data();
                        if (contImgOpt.isEmpty()) {
                            log.error("pdfImageRetriever failed to get image for page {}", pageContainer.page());
                            return new PageContainer<>(pageContainer.page(), Optional.<AnnotatedImage>empty());
                        }
                        final var contImg = contImgOpt.get();

                        final byte[] png;
                        try (final var is = new ByteArrayOutputStream()) {
                            ImageIO.write(contImg, "png", is);
                            png = is.toByteArray();
                        } catch (IOException e) {
                            log.error("failed to generate png image from BufferedImage");
                            return new PageContainer<>(
                                    pageContainer.page(),
                                    Optional.of(new AnnotatedImage(contImg, Optional.empty()))
                            );
                        }
                        final ReadResultHeader resultHeader;
                        try {
                            final var loc = apiAdapter.waitAnalyze(png, ContentType.IMAGE_PNG, null, Duration.ofSeconds(300));
                            resultHeader = apiAdapter.waitResult(loc, Duration.ofSeconds(30));
                        } catch (IOException e) {
                            log.error("azure api call failed. not adding annotations to page {}", pageContainer.page());
                            return new PageContainer<>(
                                    pageContainer.page(),
                                    Optional.of(new AnnotatedImage(contImg, Optional.empty()))
                            );
                        }
                        if (resultHeader != null && resultHeader.status() == Status.SUCCEEDED) {
                            return new PageContainer<>(
                                    pageContainer.page(),
                                    Optional.of(new AnnotatedImage(contImg, Optional.of(resultHeader.analyzeResult())))
                            );
                        }
                        log.error("azure api call did not return results. not adding annotations to page {}", pageContainer.page());
                        return new PageContainer<>(
                                pageContainer.page(),
                                Optional.of(new AnnotatedImage(contImg, Optional.empty()))
                        );
                    })
                    .map(pageContainer -> {
                        if (pageContainer.data().isPresent()) {
                            run.addPageToDocument(pdDocOut, font, pageContainer.data().get());
                        } else {
                            log.error("skipping page {} in output generation as we don't have a source image", pageContainer.page());
                        }
                        return pageContainer.data()
                                .orElse(null);
                    })
                    // filter out all missing images
                    .filter(Objects::nonNull)
                    // add null values, if annotation result is not present
                    .map(a -> a.analyzeResult().orElse(null))
                    .collect(Collectors.toList());
            pdDocOut.save(os);
            return new PdfAndAnnotations(os.toByteArray(), analyzeResults);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public byte[] ocr(byte[] inputPdf) {
        return ocrGetAnalyzeResults(inputPdf).pdfData();
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

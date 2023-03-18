package de.niklasfi.aocr;

import de.niklasfi.aocr.azure.api.AzureApiAdapter;
import de.niklasfi.aocr.azure.dto.ReadResultHeader;
import de.niklasfi.aocr.azure.dto.Status;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.core5.http.ContentType;
import org.apache.pdfbox.pdmodel.PDDocument;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
public class AzurePdfOcr {
    private final AzureApiAdapter apiAdapter;
    private final PdfImageRetriever pdfImageRetriever;
    private final FileUtil fileUtil;

    public byte[] ocr(byte[] inputPdf) {
        final var run = new AzurePdfAnnotator();

        final List<PageContainer<Optional<BufferedImage>>> images;
        log.trace("parsing input pdf");
        try (
                final var pdDocIn = PDDocument.load(inputPdf)
        ) {
            log.debug("parsed input pdf");

            log.trace("retrieving images from input pdf");
            images = pdfImageRetriever.getImages(pdDocIn).toList();
            log.debug("retrieved all images from input pdf");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        log.trace("calling azure read api for annotations");
        final var annotations = images.stream()
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
                });
        log.debug("received all annotations");

        log.trace("rendering output pdf");
        try (
                final var pdDocOut = new PDDocument();
                final var os = new ByteArrayOutputStream()
        ) {
            annotations.forEachOrdered(pageContainer -> {
                if (pageContainer.data().isPresent()) {
                    run.addPageToDocument(pdDocOut, pageContainer.data().get());
                } else {
                    log.error("skipping page {} in output generation as we don't have a source image", pageContainer.page());
                }
            });
            pdDocOut.save(os);
            return os.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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

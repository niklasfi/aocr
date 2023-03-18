package de.niklasfi.aocr;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class PdfImageRenderer implements PdfImageRetriever {
    private final int dpi;
    private final ImageType imageType;

    public PdfImageRenderer(int dpi, ImageType imageType) {
        this.dpi = dpi;
        this.imageType = imageType;
    }

    @Override
    public Stream<PageContainer<Optional<BufferedImage>>> getImages(PDDocument document) {
        final var renderer = new PDFRenderer(document);

        return IntStream.range(0, document.getNumberOfPages()).mapToObj(pageIdx -> {
            final BufferedImage bufferedImg;
            try {
                bufferedImg = renderer.renderImageWithDPI(pageIdx, dpi, imageType);
            } catch (IOException e) {
                return new PageContainer<>(pageIdx, Optional.empty());
            }
            return new PageContainer<>(pageIdx, Optional.of(bufferedImg));
        });
    }
}

package de.niklasfi.aocr;

import io.reactivex.rxjava3.core.Flowable;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.io.IOException;

public class PdfImageRenderer implements PdfImageGetter {
    @Override
    public Flowable<BufferedImage> getImages(PDDocument document) {
        final var renderer = new PDFRenderer(document);

        return Flowable
                .range(0, document.getNumberOfPages())
                .map(pageIdx -> {
                    final BufferedImage bufferedImg;
                    try {
                        bufferedImg = renderer.renderImageWithDPI(pageIdx, 72, ImageType.RGB);
                    } catch (IOException e) {
                        throw new RuntimeException("could not render image of page %s".formatted(pageIdx + 1), e);
                    }
                    return bufferedImg;
                });
    }
}

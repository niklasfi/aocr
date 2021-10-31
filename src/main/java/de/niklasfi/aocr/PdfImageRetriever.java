package de.niklasfi.aocr;

import org.apache.pdfbox.pdmodel.PDDocument;

import java.awt.image.BufferedImage;
import java.util.Optional;
import java.util.stream.Stream;

public interface PdfImageRetriever {
    Stream<Optional<BufferedImage>> getImages(PDDocument document);
}

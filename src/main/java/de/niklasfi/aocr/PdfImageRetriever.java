package de.niklasfi.aocr;

import io.reactivex.rxjava3.core.Flowable;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.awt.image.BufferedImage;

public interface PdfImageRetriever {
    Flowable<BufferedImage> getImages(PDDocument document);
}

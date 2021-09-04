package de.niklasfi.aocr;

import io.reactivex.rxjava3.core.Flowable;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.awt.image.BufferedImage;

public interface PdfImageGetter {
    Flowable<BufferedImage> getImages(PDDocument document);
}

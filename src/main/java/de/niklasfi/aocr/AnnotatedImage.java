package de.niklasfi.aocr;

import de.niklasfi.aocr.azure.dto.AnalyzeResult;

import java.awt.image.BufferedImage;

public record AnnotatedImage(BufferedImage bufferedImage, AnalyzeResult analyzeResult) {

}

package de.niklasfi.aocr;

import de.niklasfi.aocr.azure.dto.AnalyzeResult;

import java.awt.image.BufferedImage;
import java.util.Optional;

public record AnnotatedImage(BufferedImage bufferedImage, Optional<AnalyzeResult> analyzeResult) {

}

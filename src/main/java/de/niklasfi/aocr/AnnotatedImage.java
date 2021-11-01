package de.niklasfi.aocr;

import com.microsoft.azure.cognitiveservices.vision.computervision.models.AnalyzeResults;

import java.awt.image.BufferedImage;

public record AnnotatedImage(BufferedImage bufferedImage, AnalyzeResults analyzeResults) {

}

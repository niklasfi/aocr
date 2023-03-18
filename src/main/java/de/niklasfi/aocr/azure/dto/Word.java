package de.niklasfi.aocr.azure.dto;

import java.util.List;

public record Word(
        List<Double> boundingBox,
        double confidence,
        String text
) {
}

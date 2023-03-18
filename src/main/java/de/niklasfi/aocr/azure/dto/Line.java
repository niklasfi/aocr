package de.niklasfi.aocr.azure.dto;

import java.util.List;

public record Line(
        List<Double> boundingBox,
        String language,
        String text,
        List<Word> words,
        Appearance appearance
) {
}

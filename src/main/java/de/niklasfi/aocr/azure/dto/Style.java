package de.niklasfi.aocr.azure.dto;

public record Style(
        StyleEnum name,
        float confidence
) {
}

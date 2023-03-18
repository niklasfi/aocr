package de.niklasfi.aocr.azure.dto;

import java.util.List;

public record ReadResult(
        int page,
        float angle,
        float width,
        float height,
        Unit unit,
        Language language,
        List<Line> lines
) {
}

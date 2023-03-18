package de.niklasfi.aocr.azure.dto;

import java.util.List;

public record AnalyzeResult(
        List<ReadResult> readResults,
        String version,
        String modelVersion
) {
}

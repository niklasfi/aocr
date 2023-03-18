package de.niklasfi.aocr.azure.dto;

import java.time.ZonedDateTime;

public record ReadResultHeader(
        Status status,
        ZonedDateTime createdDateTime,
        ZonedDateTime lastUpdatedDateTime,
        AnalyzeResult analyzeResult
) {
}

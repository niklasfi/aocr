package de.niklasfi.aocr;

import de.niklasfi.aocr.azure.dto.Language;
import java.time.Duration;

public record AzurePdfOcrParameters(
        Duration timeoutAnalyze,
        Duration timeoutResult,
        Language language
) {
    public static AzurePdfOcrParameters buildDefault(){
        return new AzurePdfOcrParameters(
                Duration.ofSeconds(300),
                Duration.ofSeconds(300),
                null
        );
    }
}

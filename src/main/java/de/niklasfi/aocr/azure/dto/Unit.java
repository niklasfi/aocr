package de.niklasfi.aocr.azure.dto;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum Unit {
    PIXEL("pixel"),
    INCH("inch");

    private final String code;

    @JsonValue
    public String code() {
        return code;
    }
}

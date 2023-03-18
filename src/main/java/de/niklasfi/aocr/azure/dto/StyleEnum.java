package de.niklasfi.aocr.azure.dto;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum StyleEnum {
    OTHER("other"),
    HANDWRITING("handwriting");

    private final String code;

    @JsonValue
    public String code() {
        return code;
    }
}

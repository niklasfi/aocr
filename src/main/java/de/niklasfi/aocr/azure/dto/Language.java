package de.niklasfi.aocr.azure.dto;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum Language {
    ENGLISH("en"),
    CHINESE_SIMPLIFIED("zh-Hans"),
    FRENCH("fr"),
    GERMAN("de"),
    ITALIAN("it"),
    JAPANESE("ja"),
    KOREAN("ko"),
    PORTUGUESE("pt"),
    SPANISH("es");

    private final String value;

    @JsonValue
    public String code() {
        return value;
    }
}
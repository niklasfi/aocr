package de.niklasfi.aocr.azure.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum Status {
    @JsonProperty("notStarted")
    NOT_STARTED,
    @JsonProperty("running")
    RUNNING,
    @JsonProperty("failed")
    FAILED,
    @JsonProperty("succeeded")
    SUCCEEDED
}

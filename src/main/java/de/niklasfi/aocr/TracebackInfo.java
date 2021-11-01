package de.niklasfi.aocr;

import java.util.UUID;

public record TracebackInfo(UUID job, Integer page) {
    @Override
    public String toString() {
        return page == null ? "job: %s".formatted(job) : "job: %s, page: %s".formatted(job, page);
    }
}

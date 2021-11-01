package de.niklasfi.aocr;

public record TracebackContainer<T>(
        TracebackInfo trace,
        T data
) {
}

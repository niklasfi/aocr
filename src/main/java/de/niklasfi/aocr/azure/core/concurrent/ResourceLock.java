package de.niklasfi.aocr.azure.core.concurrent;

public interface ResourceLock extends AutoCloseable {

    /**
     * Unlocking doesn't throw any checked exception.
     */
    @Override
    void close();
}
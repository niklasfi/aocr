package de.niklasfi.aocr.azure.core.concurrent;

import java.util.concurrent.locks.ReentrantLock;

public class CloseableReentrantLock extends ReentrantLock {

    /**
     * @return an {@link AutoCloseable} once the lock has been acquired.
     */
    public ResourceLock lockAsResource() {
        lock();
        return this::unlock;
    }
}

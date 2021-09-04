package de.niklasfi.rx;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.TemporalAmount;

public class ThrottlerBase {
    private final TemporalAmount minInterval;
    private OffsetDateTime nextSlot;

    public ThrottlerBase(TemporalAmount minInterval) {
        this.minInterval = minInterval;
    }

    protected final long getNextSlotDelay(){
        final var now = OffsetDateTime.now();
        final long delayMs;

        if (nextSlot == null || nextSlot.isBefore(now)) {
            nextSlot = now.plus(minInterval);
            delayMs = 0;
        } else {
            delayMs = Duration.between(now, nextSlot).toMillis();
            nextSlot = nextSlot.plus(minInterval);
        }

        //System.out.printf("now: %s, delay: %s, next: %s%n", now, delayMs, nextSlot);
        return delayMs;
    }
}

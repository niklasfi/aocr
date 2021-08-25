package de.niklasfi.rx;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.functions.Function;
import org.reactivestreams.Publisher;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.TemporalAmount;
import java.util.concurrent.TimeUnit;

public class Throttler<R> implements Function<R, Publisher<? extends R>> {
    private final TemporalAmount minInterval;
    private OffsetDateTime nextSlot;

    public Throttler(TemporalAmount minInterval) {
        this.minInterval = minInterval;
    }

    @Override
    public Publisher<? extends R> apply(R r) {
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
        return Flowable.just(r).delay(delayMs, TimeUnit.MILLISECONDS);
    }
}

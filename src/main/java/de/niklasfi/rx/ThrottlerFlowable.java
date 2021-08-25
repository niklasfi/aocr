package de.niklasfi.rx;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.functions.Function;
import org.reactivestreams.Publisher;

import java.time.temporal.TemporalAmount;
import java.util.concurrent.TimeUnit;

public class ThrottlerFlowable<R> extends ThrottlerBase implements Function<R, Publisher<? extends R>> {
    public ThrottlerFlowable(TemporalAmount minInterval) {
        super(minInterval);
    }

    @Override
    public Publisher<? extends R> apply(R r) {
        final var delayMs = getNextSlotDelay();
        return Flowable.just(r).delay(delayMs, TimeUnit.MILLISECONDS);
    }
}

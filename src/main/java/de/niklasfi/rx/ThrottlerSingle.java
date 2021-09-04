package de.niklasfi.rx;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.core.SingleSource;
import io.reactivex.rxjava3.functions.Function;

import java.time.temporal.TemporalAmount;
import java.util.concurrent.TimeUnit;

public class ThrottlerSingle<R> extends ThrottlerBase implements Function<R, SingleSource<? extends R>> {

    public ThrottlerSingle(TemporalAmount minInterval) {
        super(minInterval);
    }

    @Override
    public SingleSource<? extends R> apply(R r) {
        final var delayMs = getNextSlotDelay();
        return Single.just(r).delay(delayMs, TimeUnit.MILLISECONDS);
    }
}

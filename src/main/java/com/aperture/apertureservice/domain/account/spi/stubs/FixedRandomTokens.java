package com.aperture.apertureservice.domain.account.spi.stubs;

import com.aperture.apertureservice.ddd.RandomTokens;
import com.aperture.apertureservice.ddd.Stub;

import java.util.concurrent.atomic.AtomicInteger;

@Stub
public class FixedRandomTokens implements RandomTokens {
    private final AtomicInteger counter = new AtomicInteger();

    @Override
    public String token(String prefix) {
        return prefix + "tok" + counter.incrementAndGet();
    }

    @Override
    public String hash(String value) {
        return "#" + value;
    }
}

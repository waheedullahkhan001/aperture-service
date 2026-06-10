package com.aperture.apertureservice.domain.account.spi.stubs;

import com.aperture.apertureservice.ddd.Stub;
import com.aperture.apertureservice.domain.account.VerificationCode;
import com.aperture.apertureservice.domain.account.spi.VerificationCodes;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Stub
public class InMemoryVerificationCodes implements VerificationCodes {
    private final Map<String, VerificationCode> store = new ConcurrentHashMap<>();

    private String key(UUID userId, VerificationCode.Purpose p) {
        return userId + "/" + p;
    }

    @Override
    public Optional<VerificationCode> find(UUID userId, VerificationCode.Purpose purpose) {
        return Optional.ofNullable(store.get(key(userId, purpose)));
    }

    @Override
    public void save(VerificationCode code) {
        store.put(key(code.userId(), code.purpose()), code);
    }

    @Override
    public void delete(UUID userId, VerificationCode.Purpose purpose) {
        store.remove(key(userId, purpose));
    }
}

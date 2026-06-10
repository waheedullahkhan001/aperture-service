package com.aperture.apertureservice.ddd;

public final class Conflict extends DomainException {
    public Conflict(String code, String message) {
        super(code, message);
    }
}

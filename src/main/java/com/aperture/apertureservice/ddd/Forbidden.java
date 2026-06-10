package com.aperture.apertureservice.ddd;

public final class Forbidden extends DomainException {
    public Forbidden(String code, String message) {
        super(code, message);
    }
}

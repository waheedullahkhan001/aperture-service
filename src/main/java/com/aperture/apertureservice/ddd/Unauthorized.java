package com.aperture.apertureservice.ddd;

public final class Unauthorized extends DomainException {
    public Unauthorized(String code, String message) {
        super(code, message);
    }
}

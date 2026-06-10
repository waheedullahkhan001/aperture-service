package com.aperture.apertureservice.ddd;

public final class BadRequest extends DomainException {
    public BadRequest(String code, String message) {
        super(code, message);
    }
}

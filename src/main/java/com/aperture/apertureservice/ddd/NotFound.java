package com.aperture.apertureservice.ddd;

public final class NotFound extends DomainException {
    public NotFound(String code, String message) {
        super(code, message);
    }
}

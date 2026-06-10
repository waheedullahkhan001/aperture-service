package com.aperture.apertureservice.ddd;

public abstract sealed class DomainException extends RuntimeException
        permits BadRequest, Unauthorized, Forbidden, NotFound, Conflict {

    private final String code;

    protected DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}

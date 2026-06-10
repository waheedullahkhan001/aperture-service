package com.aperture.apertureservice.domain.account.api;

public interface VerifyEmail {
    void verify(String email, String code);
}

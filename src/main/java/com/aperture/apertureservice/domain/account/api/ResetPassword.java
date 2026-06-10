package com.aperture.apertureservice.domain.account.api;

public interface ResetPassword {
    void reset(String email, String code, String newPassword);
}

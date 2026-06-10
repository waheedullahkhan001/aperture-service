package com.aperture.apertureservice.domain.account.api;

public interface RegisterAccount {
    void register(String email, String fullname, String rawPassword);
}

package com.aperture.apertureservice.domain.account.api;

import com.aperture.apertureservice.domain.account.AuthTokens;

public interface LogIn {
    AuthTokens logIn(String email, String rawPassword, String sessionLabel);
}

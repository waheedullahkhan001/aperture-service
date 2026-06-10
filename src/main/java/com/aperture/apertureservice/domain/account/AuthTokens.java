package com.aperture.apertureservice.domain.account;

public record AuthTokens(String accessToken, String refreshToken, long expiresInSeconds) {}

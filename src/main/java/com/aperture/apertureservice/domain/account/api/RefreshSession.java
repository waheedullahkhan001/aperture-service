package com.aperture.apertureservice.domain.account.api;

import com.aperture.apertureservice.domain.account.AuthTokens;

public interface RefreshSession {
    AuthTokens refresh(String refreshToken);
}

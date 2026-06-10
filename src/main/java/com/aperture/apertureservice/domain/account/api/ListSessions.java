package com.aperture.apertureservice.domain.account.api;

import com.aperture.apertureservice.domain.account.Session;

import java.util.List;
import java.util.UUID;

public interface ListSessions {
    List<Session> list(UUID userId);
}

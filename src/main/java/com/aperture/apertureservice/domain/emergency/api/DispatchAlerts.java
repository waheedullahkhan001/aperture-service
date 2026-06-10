package com.aperture.apertureservice.domain.emergency.api;

import java.util.UUID;

public interface DispatchAlerts {
    void dispatch(UUID recordingId);
}

package com.aperture.apertureservice.domain.account.api;

import com.aperture.apertureservice.domain.account.DeviceIdentity;

public interface IdentifyDevice {
    DeviceIdentity identify(String deviceToken);
}

package com.aperture.apertureservice.domain.account.api;

import com.aperture.apertureservice.domain.account.Device;

import java.util.List;
import java.util.UUID;

public interface ListDevices {
    List<Device> list(UUID userId);
}

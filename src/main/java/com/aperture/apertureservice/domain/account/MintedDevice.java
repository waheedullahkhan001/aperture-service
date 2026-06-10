package com.aperture.apertureservice.domain.account;

import java.util.UUID;

public record MintedDevice(UUID id, String name, String token) {}

package com.aperture.apertureservice.infrastructure.controller.internal;

public final class HookDtos {
    private HookDtos() {}

    /** MediaMTX authHTTPAddress callback body (extra fields ignored by Jackson). */
    public record AuthRequest(String user, String password, String ip, String action,
                              String path, String protocol, String id, String query) {}

    public record PublishStart(String path, String query) {}

    public record PublishEnd(String path) {}

    public record SegmentComplete(String path, String segmentPath, String duration) {}
}

package com.aperture.apertureservice.infrastructure.controller.internal;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class StreamPaths {
    private static final Pattern APERTURE = Pattern.compile("^aperture/([0-9a-fA-F-]{36})$");

    private StreamPaths() {}

    public static Optional<UUID> recordingId(String path) {
        if (path == null) return Optional.empty();
        Matcher m = APERTURE.matcher(path);
        if (!m.matches()) return Optional.empty();
        try {
            return Optional.of(UUID.fromString(m.group(1)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public static Optional<String> queryParam(String query, String name) {
        if (query == null || query.isBlank()) return Optional.empty();
        // mediamtx passes $MTX_QUERY with structural chars URL-encoded (e.g. "token%3Dvalue")
        // while auth callback JSON has them raw — decode first so both forms work
        String decoded = URLDecoder.decode(query, StandardCharsets.UTF_8);
        for (String pair : decoded.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(name)) {
                return Optional.of(URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
            }
        }
        return Optional.empty();
    }

    public static double durationSeconds(String raw) {
        if (raw == null || raw.isBlank()) return 0.0;
        String s = raw.trim();
        if (s.endsWith("s")) s = s.substring(0, s.length() - 1);
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}

package com.aperture.apertureservice.domain.account;

import com.aperture.apertureservice.ddd.BadRequest;

public final class PasswordPolicy {
    private PasswordPolicy() {}

    /** SRS-004: at least 8 characters, one number, one special character. */
    public static void check(String raw) {
        boolean ok = raw != null
                && raw.length() >= 8
                // Unicode digits intentionally count as "a number" (SRS-004 doesn't restrict charset; don't "fix" to ASCII-only).
                && raw.chars().anyMatch(Character::isDigit)
                && raw.chars().anyMatch(c -> !Character.isLetterOrDigit(c));
        if (!ok) {
            throw new BadRequest("PASSWORD_POLICY",
                    "Password must be at least 8 characters and include a number and a special character");
        }
    }
}

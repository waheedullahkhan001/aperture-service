package com.aperture.apertureservice.domain.account;

import com.aperture.apertureservice.ddd.BadRequest;

import java.util.Locale;
import java.util.regex.Pattern;

public record Email(String value) {
    private static final Pattern SHAPE = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public Email {
        value = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!SHAPE.matcher(value).matches()) {
            throw new BadRequest("EMAIL_INVALID", "Invalid email address");
        }
    }
}

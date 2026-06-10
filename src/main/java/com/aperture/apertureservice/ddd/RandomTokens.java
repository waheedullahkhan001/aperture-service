package com.aperture.apertureservice.ddd;

public interface RandomTokens {
    /** New high-entropy token, e.g. token("apd_") -> "apd_<43 base64url chars>". */
    String token(String prefix);
    /** Stable one-way hash (hex) for storage/lookup. */
    String hash(String value);
}

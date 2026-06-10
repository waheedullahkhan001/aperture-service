package com.aperture.apertureservice.infrastructure.security;

import com.aperture.apertureservice.domain.account.AccessClaims;
import com.aperture.apertureservice.domain.account.spi.TokenIssuer;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

public class JwtTokenIssuer implements TokenIssuer {

    private final SecretKey key;
    private final Clock clock;

    public JwtTokenIssuer(String base64Secret, Clock clock) {
        this.key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(base64Secret));
        this.clock = clock;
    }

    @Override
    public String issue(UUID userId, UUID sessionId, Duration ttl) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("sid", sessionId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    @Override
    public Optional<AccessClaims> validate(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(new AccessClaims(
                    UUID.fromString(claims.getSubject()),
                    UUID.fromString(claims.get("sid", String.class))));
        } catch (JwtException | IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }
}

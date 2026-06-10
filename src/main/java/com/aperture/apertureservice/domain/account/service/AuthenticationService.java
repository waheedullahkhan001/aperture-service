package com.aperture.apertureservice.domain.account.service;

import com.aperture.apertureservice.ddd.DomainService;
import com.aperture.apertureservice.ddd.Forbidden;
import com.aperture.apertureservice.ddd.RandomTokens;
import com.aperture.apertureservice.ddd.Unauthorized;
import com.aperture.apertureservice.domain.account.AuthTokens;
import com.aperture.apertureservice.domain.account.Email;
import com.aperture.apertureservice.domain.account.Session;
import com.aperture.apertureservice.domain.account.User;
import com.aperture.apertureservice.domain.account.api.LogIn;
import com.aperture.apertureservice.domain.account.api.LogOut;
import com.aperture.apertureservice.domain.account.api.RefreshSession;
import com.aperture.apertureservice.domain.account.spi.PasswordHasher;
import com.aperture.apertureservice.domain.account.spi.Sessions;
import com.aperture.apertureservice.domain.account.spi.TokenIssuer;
import com.aperture.apertureservice.domain.account.spi.Users;
import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.transaction.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@DomainService
public class AuthenticationService implements LogIn, RefreshSession, LogOut {

    private final Users users;
    private final Sessions sessions;
    private final PasswordHasher hasher;
    private final TokenIssuer tokenIssuer;
    private final RandomTokens tokens;
    private final Clock clock;
    private final Duration accessTtl;
    private final Duration refreshTtl;

    public AuthenticationService(Users users, Sessions sessions, PasswordHasher hasher,
                                 TokenIssuer tokenIssuer, RandomTokens tokens, Clock clock,
                                 Duration accessTtl, Duration refreshTtl) {
        this.users = users;
        this.sessions = sessions;
        this.hasher = hasher;
        this.tokenIssuer = tokenIssuer;
        this.tokens = tokens;
        this.clock = clock;
        this.accessTtl = accessTtl;
        this.refreshTtl = refreshTtl;
    }

    @Override
    @Transactional
    public AuthTokens logIn(String rawEmail, String rawPassword, String sessionLabel) {
        User user = users.byEmail(new Email(rawEmail))
                .filter(u -> hasher.matches(rawPassword, u.passwordHash().value()))
                .orElseThrow(() -> new Unauthorized("INVALID_CREDENTIALS", "Invalid email or password"));
        if (!user.verified()) {
            throw new Forbidden("EMAIL_NOT_VERIFIED", "Verify your email before logging in");
        }
        Instant now = clock.instant();
        String refresh = tokens.token("aprt_");
        Session session = new Session(UuidCreator.getTimeOrderedEpoch(), user.id(),
                sessionLabel == null ? "" : sessionLabel.substring(0, Math.min(120, sessionLabel.length())),
                tokens.hash(refresh), null, now, now, now.plus(refreshTtl));
        sessions.save(session);
        return new AuthTokens(tokenIssuer.issue(user.id(), session.id(), accessTtl),
                refresh, accessTtl.toSeconds());
    }

    @Override
    @Transactional
    public AuthTokens refresh(String presented) {
        Instant now = clock.instant();
        String h = tokens.hash(presented);
        Optional<Session> current = sessions.byRefreshTokenHash(h);
        if (current.isEmpty()) {
            sessions.byPreviousTokenHash(h).ifPresent(reused -> {
                sessions.deleteAllForUser(reused.userId());
                throw new Unauthorized("REFRESH_REUSED", "Refresh token reuse detected; all sessions revoked");
            });
            throw new Unauthorized("INVALID_REFRESH_TOKEN", "Invalid refresh token");
        }
        Session session = current.get();
        if (session.expiresAt().isBefore(now)) {
            sessions.delete(session.id());
            throw new Unauthorized("INVALID_REFRESH_TOKEN", "Session expired");
        }
        String next = tokens.token("aprt_");
        sessions.save(session.rotated(tokens.hash(next), now, now.plus(refreshTtl)));
        return new AuthTokens(tokenIssuer.issue(session.userId(), session.id(), accessTtl),
                next, accessTtl.toSeconds());
    }

    @Override
    @Transactional
    public void logOut(UUID sessionId) {
        sessions.delete(sessionId);
    }
}

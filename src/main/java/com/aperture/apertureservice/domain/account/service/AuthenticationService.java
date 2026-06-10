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

    static final Duration REUSE_GRACE = Duration.ofSeconds(30);

    private final Users users;
    private final Sessions sessions;
    private final PasswordHasher hasher;
    private final TokenIssuer tokenIssuer;
    private final RandomTokens tokens;
    private final Clock clock;
    private final Duration accessTtl;
    private final Duration refreshTtl;
    private final String timingDummyHash;

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
        this.timingDummyHash = hasher.hash("aperture-timing-equalizer");
    }

    @Override
    @Transactional
    public AuthTokens logIn(String rawEmail, String rawPassword, String sessionLabel) {
        Optional<User> found = users.byEmail(new Email(rawEmail));
        if (found.isEmpty()) {
            hasher.matches(rawPassword, timingDummyHash); // constant-time-ish: pay the hash cost anyway
            throw new Unauthorized("INVALID_CREDENTIALS", "Invalid email or password");
        }
        User user = found.get();
        if (!hasher.matches(rawPassword, user.passwordHash().value())) {
            throw new Unauthorized("INVALID_CREDENTIALS", "Invalid email or password");
        }
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

    // session deletions on the reuse/expired paths must survive the Unauthorized that follows them
    @Override
    @Transactional(dontRollbackOn = Unauthorized.class)
    public AuthTokens refresh(String presented) {
        Instant now = clock.instant();
        String h = tokens.hash(presented);
        Optional<Session> current = sessions.byRefreshTokenHash(h);
        if (current.isEmpty()) {
            Optional<Session> rotatedAway = sessions.byPreviousTokenHash(h);
            if (rotatedAway.isEmpty()) {
                throw new Unauthorized("INVALID_REFRESH_TOKEN", "Invalid refresh token");
            }
            Session reused = rotatedAway.get();
            if (reused.expiresAt().isBefore(now)) {
                // zombie session long past expiry: clean it up, don't treat as an attack
                sessions.delete(reused.id());
                throw new Unauthorized("INVALID_REFRESH_TOKEN", "Invalid refresh token");
            }
            if (reused.lastUsedAt().isAfter(now.minus(REUSE_GRACE))) {
                // The rotation happened moments ago — almost certainly a client retry whose
                // response was lost in transit, not an attack. Re-rotate instead of revoking
                // everything (an emergency product must not nuke sessions on a network blip).
                current = rotatedAway;
            } else {
                sessions.deleteAllForUser(reused.userId());
                throw new Unauthorized("REFRESH_REUSED", "Refresh token reuse detected; all sessions revoked");
            }
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

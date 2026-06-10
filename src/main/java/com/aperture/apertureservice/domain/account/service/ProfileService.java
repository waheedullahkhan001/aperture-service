package com.aperture.apertureservice.domain.account.service;

import com.aperture.apertureservice.ddd.BadRequest;
import com.aperture.apertureservice.ddd.DomainService;
import com.aperture.apertureservice.ddd.NotFound;
import com.aperture.apertureservice.domain.account.Session;
import com.aperture.apertureservice.domain.account.User;
import com.aperture.apertureservice.domain.account.api.ChangeProfile;
import com.aperture.apertureservice.domain.account.api.DeleteAccount;
import com.aperture.apertureservice.domain.account.api.GetProfile;
import com.aperture.apertureservice.domain.account.api.ListSessions;
import com.aperture.apertureservice.domain.account.api.RevokeSession;
import com.aperture.apertureservice.domain.account.spi.AccountCleanup;
import com.aperture.apertureservice.domain.account.spi.Sessions;
import com.aperture.apertureservice.domain.account.spi.Users;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.UUID;

@DomainService
public class ProfileService implements GetProfile, ChangeProfile, DeleteAccount, ListSessions, RevokeSession {

    private final Users users;
    private final Sessions sessions;
    private final AccountCleanup cleanup;

    public ProfileService(Users users, Sessions sessions, AccountCleanup cleanup) {
        this.users = users;
        this.sessions = sessions;
        this.cleanup = cleanup;
    }

    @Override
    public User get(UUID userId) {
        return users.byId(userId).orElseThrow(() -> new NotFound("USER_NOT_FOUND", "User not found"));
    }

    @Override
    @Transactional
    public User changeFullname(UUID userId, String fullname) {
        if (fullname == null || fullname.isBlank()) {
            throw new BadRequest("FULLNAME_REQUIRED", "Full name is required");
        }
        User updated = get(userId).withFullname(fullname.trim());
        users.save(updated);
        return updated;
    }

    @Override
    @Transactional
    public void delete(UUID userId) {
        get(userId);
        cleanup.purgeUserData(userId);   // recordings + segment files (infra orchestrator, later task)
        sessions.deleteAllForUser(userId);
        users.delete(userId);            // remaining child rows handled by cleanup
    }

    @Override
    public List<Session> list(UUID userId) {
        return sessions.byUser(userId);
    }

    @Override
    @Transactional
    public void revoke(UUID userId, UUID sessionId) {
        Session s = sessions.byId(sessionId)
                .filter(x -> x.userId().equals(userId))
                .orElseThrow(() -> new NotFound("SESSION_NOT_FOUND", "Session not found"));
        sessions.delete(s.id());
    }
}

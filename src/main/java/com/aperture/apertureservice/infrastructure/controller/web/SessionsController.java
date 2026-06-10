package com.aperture.apertureservice.infrastructure.controller.web;

import com.aperture.apertureservice.domain.account.api.ListSessions;
import com.aperture.apertureservice.domain.account.api.RevokeSession;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/me/sessions")
public class SessionsController {

    private final ListSessions listSessions;
    private final RevokeSession revokeSession;

    public SessionsController(ListSessions listSessions, RevokeSession revokeSession) {
        this.listSessions = listSessions;
        this.revokeSession = revokeSession;
    }

    @GetMapping
    public List<AccountDtos.SessionResponse> list(Authentication auth) {
        return listSessions.list(MeController.userId(auth)).stream()
                .map(AccountDtos.SessionResponse::from).toList();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(Authentication auth, @PathVariable UUID id) {
        revokeSession.revoke(MeController.userId(auth), id);
    }
}

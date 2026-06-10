package com.aperture.apertureservice.infrastructure.controller.web;

import com.aperture.apertureservice.domain.account.Email;
import com.aperture.apertureservice.domain.account.HashedPassword;
import com.aperture.apertureservice.domain.account.Session;
import com.aperture.apertureservice.domain.account.User;
import com.aperture.apertureservice.domain.account.api.ChangeProfile;
import com.aperture.apertureservice.domain.account.api.DeleteAccount;
import com.aperture.apertureservice.domain.account.api.GetProfile;
import com.aperture.apertureservice.domain.account.api.ListSessions;
import com.aperture.apertureservice.domain.account.api.RevokeSession;
import com.aperture.apertureservice.infrastructure.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {MeController.class, SessionsController.class})
@AutoConfigureMockMvc(addFilters = false)
class MeControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean GetProfile getProfile;
    @MockitoBean ChangeProfile changeProfile;
    @MockitoBean DeleteAccount deleteAccount;
    @MockitoBean ListSessions listSessions;
    @MockitoBean RevokeSession revokeSession;

    private final UUID userId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();

    private Authentication asUser() {
        return new UsernamePasswordAuthenticationToken(new AuthenticatedUser(userId, sessionId), null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    @Test
    void getMeReturnsProfile() throws Exception {
        when(getProfile.get(userId)).thenReturn(new User(userId, new Email("u@example.com"), "U",
                new HashedPassword("h"), true, Instant.parse("2026-06-07T12:00:00Z")));
        mvc.perform(get("/api/v1/me").principal(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("u@example.com"))
                .andExpect(jsonPath("$.verified").value(true));
    }

    @Test
    void patchMeChangesFullname() throws Exception {
        when(changeProfile.changeFullname(userId, "New")).thenReturn(
                new User(userId, new Email("u@example.com"), "New", new HashedPassword("h"), true, Instant.now()));
        mvc.perform(patch("/api/v1/me").principal(asUser()).contentType("application/json")
                        .content("""
                                {"fullname":"New"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullname").value("New"));
    }

    @Test
    void deleteMeCascades() throws Exception {
        mvc.perform(delete("/api/v1/me").principal(asUser())).andExpect(status().isNoContent());
        verify(deleteAccount).delete(userId);
    }

    @Test
    void sessionsListAndRevoke() throws Exception {
        Instant t = Instant.parse("2026-06-07T12:00:00Z");
        when(listSessions.list(userId)).thenReturn(List.of(
                new Session(sessionId, userId, "Firefox", "#h", null, t, t, t.plusSeconds(3600))));
        mvc.perform(get("/api/v1/me/sessions").principal(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].label").value("Firefox"));
        mvc.perform(delete("/api/v1/me/sessions/" + sessionId).principal(asUser()))
                .andExpect(status().isNoContent());
        verify(revokeSession).revoke(userId, sessionId);
    }
}

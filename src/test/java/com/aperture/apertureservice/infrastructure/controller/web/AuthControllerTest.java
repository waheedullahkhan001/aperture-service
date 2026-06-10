package com.aperture.apertureservice.infrastructure.controller.web;

import com.aperture.apertureservice.ddd.Conflict;
import com.aperture.apertureservice.domain.account.AuthTokens;
import com.aperture.apertureservice.domain.account.api.LogIn;
import com.aperture.apertureservice.domain.account.api.LogOut;
import com.aperture.apertureservice.domain.account.api.RefreshSession;
import com.aperture.apertureservice.domain.account.api.RegisterAccount;
import com.aperture.apertureservice.domain.account.api.RequestPasswordReset;
import com.aperture.apertureservice.domain.account.api.ResendVerification;
import com.aperture.apertureservice.domain.account.api.ResetPassword;
import com.aperture.apertureservice.domain.account.api.VerifyEmail;
import com.aperture.apertureservice.infrastructure.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean RegisterAccount registerAccount;
    @MockitoBean VerifyEmail verifyEmail;
    @MockitoBean ResendVerification resendVerification;
    @MockitoBean LogIn logIn;
    @MockitoBean RefreshSession refreshSession;
    @MockitoBean LogOut logOut;
    @MockitoBean RequestPasswordReset requestPasswordReset;
    @MockitoBean ResetPassword resetPassword;

    @Test
    void registerReturns202AndDelegates() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType("application/json")
                        .content("""
                                {"email":"u@example.com","fullname":"U","password":"abcdef1!"}"""))
                .andExpect(status().isAccepted());
        verify(registerAccount).register("u@example.com", "U", "abcdef1!");
    }

    @Test
    void registerValidatesBody() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType("application/json")
                        .content("""
                                {"email":"","fullname":"","password":""}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void loginReturnsTokensAndUsesUserAgentAsLabel() throws Exception {
        when(logIn.logIn("u@example.com", "abcdef1!", "TestAgent"))
                .thenReturn(new AuthTokens("at", "rt", 900));
        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                        .header("User-Agent", "TestAgent")
                        .content("""
                                {"email":"u@example.com","password":"abcdef1!"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("at"))
                .andExpect(jsonPath("$.refreshToken").value("rt"))
                .andExpect(jsonPath("$.expiresIn").value(900));
    }

    @Test
    void domainErrorsPassThroughAdvice() throws Exception {
        doThrow(new Conflict("EMAIL_TAKEN", "taken")).when(registerAccount)
                .register(anyString(), anyString(), anyString());
        mvc.perform(post("/api/v1/auth/register").contentType("application/json")
                        .content("""
                                {"email":"u@example.com","fullname":"U","password":"abcdef1!"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
    }

    @Test
    void verifyResendRefreshResetEndpointsRespond() throws Exception {
        when(refreshSession.refresh("rt")).thenReturn(new AuthTokens("at2", "rt2", 900));
        mvc.perform(post("/api/v1/auth/verify-email").contentType("application/json")
                        .content("""
                                {"email":"u@example.com","code":"123456"}"""))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/resend-verification").contentType("application/json")
                        .content("""
                                {"email":"u@example.com"}"""))
                .andExpect(status().isAccepted());
        mvc.perform(post("/api/v1/auth/refresh").contentType("application/json")
                        .content("""
                                {"refreshToken":"rt"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("at2"));
        mvc.perform(post("/api/v1/auth/password-reset/request").contentType("application/json")
                        .content("""
                                {"email":"u@example.com"}"""))
                .andExpect(status().isAccepted());
        mvc.perform(post("/api/v1/auth/password-reset/confirm").contentType("application/json")
                        .content("""
                                {"email":"u@example.com","code":"123456","newPassword":"abcdef1!"}"""))
                .andExpect(status().isNoContent());
    }

    @Test
    void logoutDelegatesWithSessionIdFromPrincipal() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        var auth = new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(userId, sessionId), null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        mvc.perform(post("/api/v1/auth/logout").principal(auth))
                .andExpect(status().isNoContent());
        verify(logOut).logOut(sessionId);
    }
}

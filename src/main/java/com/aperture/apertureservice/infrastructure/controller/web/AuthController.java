package com.aperture.apertureservice.infrastructure.controller.web;

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
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final RegisterAccount registerAccount;
    private final VerifyEmail verifyEmail;
    private final ResendVerification resendVerification;
    private final LogIn logIn;
    private final RefreshSession refreshSession;
    private final LogOut logOut;
    private final RequestPasswordReset requestPasswordReset;
    private final ResetPassword resetPassword;

    public AuthController(RegisterAccount registerAccount, VerifyEmail verifyEmail,
                          ResendVerification resendVerification, LogIn logIn,
                          RefreshSession refreshSession, LogOut logOut,
                          RequestPasswordReset requestPasswordReset, ResetPassword resetPassword) {
        this.registerAccount = registerAccount;
        this.verifyEmail = verifyEmail;
        this.resendVerification = resendVerification;
        this.logIn = logIn;
        this.refreshSession = refreshSession;
        this.logOut = logOut;
        this.requestPasswordReset = requestPasswordReset;
        this.resetPassword = resetPassword;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void register(@Valid @RequestBody AuthDtos.RegisterRequest body) {
        registerAccount.register(body.email(), body.fullname(), body.password());
    }

    @PostMapping("/verify-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verify(@Valid @RequestBody AuthDtos.VerifyEmailRequest body) {
        verifyEmail.verify(body.email(), body.code());
    }

    @PostMapping("/resend-verification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void resend(@Valid @RequestBody AuthDtos.ResendRequest body) {
        resendVerification.resend(body.email());
    }

    @PostMapping("/login")
    public AuthDtos.TokenResponse login(@Valid @RequestBody AuthDtos.LoginRequest body,
                                        @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        AuthTokens t = logIn.logIn(body.email(), body.password(),
                userAgent == null ? "unknown" : userAgent);
        return new AuthDtos.TokenResponse(t.accessToken(), t.refreshToken(), t.expiresInSeconds());
    }

    @PostMapping("/refresh")
    public AuthDtos.TokenResponse refresh(@Valid @RequestBody AuthDtos.RefreshRequest body) {
        AuthTokens t = refreshSession.refresh(body.refreshToken());
        return new AuthDtos.TokenResponse(t.accessToken(), t.refreshToken(), t.expiresInSeconds());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(Authentication authentication) {
        logOut.logOut(((AuthenticatedUser) authentication.getPrincipal()).sessionId());
    }

    @PostMapping("/password-reset/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void resetRequest(@Valid @RequestBody AuthDtos.ResetRequest body) {
        requestPasswordReset.request(body.email());
    }

    @PostMapping("/password-reset/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetConfirm(@Valid @RequestBody AuthDtos.ResetConfirmRequest body) {
        resetPassword.reset(body.email(), body.code(), body.newPassword());
    }
}

package com.aperture.apertureservice.infrastructure.controller.web;

import com.aperture.apertureservice.domain.account.api.ChangeProfile;
import com.aperture.apertureservice.domain.account.api.DeleteAccount;
import com.aperture.apertureservice.domain.account.api.GetProfile;
import com.aperture.apertureservice.infrastructure.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final GetProfile getProfile;
    private final ChangeProfile changeProfile;
    private final DeleteAccount deleteAccount;

    public MeController(GetProfile getProfile, ChangeProfile changeProfile, DeleteAccount deleteAccount) {
        this.getProfile = getProfile;
        this.changeProfile = changeProfile;
        this.deleteAccount = deleteAccount;
    }

    static UUID userId(Authentication auth) {
        return ((AuthenticatedUser) auth.getPrincipal()).userId();
    }

    @GetMapping
    public AccountDtos.ProfileResponse me(Authentication auth) {
        return AccountDtos.ProfileResponse.from(getProfile.get(userId(auth)));
    }

    @PatchMapping
    public AccountDtos.ProfileResponse update(Authentication auth,
                                              @Valid @RequestBody AccountDtos.UpdateProfileRequest body) {
        return AccountDtos.ProfileResponse.from(changeProfile.changeFullname(userId(auth), body.fullname()));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication auth) {
        deleteAccount.delete(userId(auth));
    }
}

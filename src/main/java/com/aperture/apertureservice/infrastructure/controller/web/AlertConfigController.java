package com.aperture.apertureservice.infrastructure.controller.web;

import com.aperture.apertureservice.domain.emergency.api.GetAlertConfiguration;
import com.aperture.apertureservice.domain.emergency.api.UpdateAlertConfiguration;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/alert-config")
public class AlertConfigController {

    private final GetAlertConfiguration getConfig;
    private final UpdateAlertConfiguration updateConfig;

    public AlertConfigController(GetAlertConfiguration getConfig, UpdateAlertConfiguration updateConfig) {
        this.getConfig = getConfig;
        this.updateConfig = updateConfig;
    }

    @GetMapping
    public ContactDtos.AlertConfigResponse get(Authentication auth) {
        return ContactDtos.AlertConfigResponse.from(getConfig.get(MeController.userId(auth)));
    }

    @PutMapping
    public ContactDtos.AlertConfigResponse put(Authentication auth,
                                               @Valid @RequestBody ContactDtos.AlertConfigRequest body) {
        return ContactDtos.AlertConfigResponse.from(updateConfig.update(MeController.userId(auth),
                body.countdownDurationSeconds(), body.messageTemplate()));
    }
}

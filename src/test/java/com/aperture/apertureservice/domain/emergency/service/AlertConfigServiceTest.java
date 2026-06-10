package com.aperture.apertureservice.domain.emergency.service;

import com.aperture.apertureservice.ddd.BadRequest;
import com.aperture.apertureservice.domain.emergency.AlertConfiguration;
import com.aperture.apertureservice.domain.emergency.spi.stubs.InMemoryAlertConfigurations;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AlertConfigServiceTest {

    private final InMemoryAlertConfigurations configs = new InMemoryAlertConfigurations();
    private final AlertConfigService service = new AlertConfigService(configs);
    private final UUID userId = UUID.randomUUID();

    @Test
    void getReturnsDefaultsWhenUnset() {
        AlertConfiguration c = service.get(userId);
        assertThat(c.countdownDurationSeconds()).isEqualTo(30);
        assertThat(c.messageTemplate()).contains("{{streamUrl}}");
    }

    @Test
    void updatePersistsAndValidatesRange() {
        AlertConfiguration c = service.update(userId, 0, "Help! {{streamUrl}}");
        assertThat(c.countdownDurationSeconds()).isZero();
        assertThat(service.get(userId).messageTemplate()).isEqualTo("Help! {{streamUrl}}");

        assertThatThrownBy(() -> service.update(userId, -1, "x"))
                .isInstanceOf(BadRequest.class).hasFieldOrPropertyWithValue("code", "COUNTDOWN_RANGE");
        assertThatThrownBy(() -> service.update(userId, 3601, "x"))
                .isInstanceOf(BadRequest.class).hasFieldOrPropertyWithValue("code", "COUNTDOWN_RANGE");
        assertThatThrownBy(() -> service.update(userId, 30, "  "))
                .isInstanceOf(BadRequest.class).hasFieldOrPropertyWithValue("code", "TEMPLATE_REQUIRED");
    }
}

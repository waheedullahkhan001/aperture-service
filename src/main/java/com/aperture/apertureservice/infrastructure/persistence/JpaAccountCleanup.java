package com.aperture.apertureservice.infrastructure.persistence;

import com.aperture.apertureservice.domain.account.spi.AccountCleanup;
import com.aperture.apertureservice.domain.recording.api.DeleteRecording;
import com.aperture.apertureservice.domain.recording.spi.Recordings;
import com.aperture.apertureservice.infrastructure.persistence.account.jpa.DeviceJpaRepository;
import com.aperture.apertureservice.infrastructure.persistence.account.jpa.VerificationCodeJpaRepository;
import com.aperture.apertureservice.infrastructure.persistence.emergency.jpa.AlertConfigurationJpaRepository;
import com.aperture.apertureservice.infrastructure.persistence.emergency.jpa.ContactJpaRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class JpaAccountCleanup implements AccountCleanup {

    private final Recordings recordings;
    private final DeleteRecording deleteRecording;
    private final ContactJpaRepository contacts;
    private final AlertConfigurationJpaRepository alertConfigs;
    private final DeviceJpaRepository devices;
    private final VerificationCodeJpaRepository verificationCodes;

    public JpaAccountCleanup(Recordings recordings, DeleteRecording deleteRecording,
                             ContactJpaRepository contacts, AlertConfigurationJpaRepository alertConfigs,
                             DeviceJpaRepository devices, VerificationCodeJpaRepository verificationCodes) {
        this.recordings = recordings;
        this.deleteRecording = deleteRecording;
        this.contacts = contacts;
        this.alertConfigs = alertConfigs;
        this.devices = devices;
        this.verificationCodes = verificationCodes;
    }

    @Override
    @Transactional
    public void purgeUserData(UUID userId) {
        recordings.idsByUser(userId).forEach(id -> deleteRecording.delete(userId, id));
        contacts.deleteByUserId(userId);
        // Spring Data 4: deleteById uses findById(..).ifPresent(..) — no exception when row is absent
        alertConfigs.deleteById(userId);
        devices.deleteByUserId(userId);
        verificationCodes.deleteByUserId(userId);
        // alert_dispatch_attempts rows are intentionally retained (audit history; no FK constraints)
    }
}

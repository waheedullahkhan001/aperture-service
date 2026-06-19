package com.aperture.apertureservice.infrastructure.controller.device;

import com.aperture.apertureservice.domain.emergency.api.GetAlertConfiguration;
import com.aperture.apertureservice.domain.emergency.api.ListEmergencyContacts;
import com.aperture.apertureservice.domain.recording.CancelResult;
import com.aperture.apertureservice.domain.recording.EnsureResult;
import com.aperture.apertureservice.domain.recording.Recording;
import com.aperture.apertureservice.domain.recording.RecordingSegment;
import com.aperture.apertureservice.domain.recording.WatchUrls;
import com.aperture.apertureservice.domain.recording.api.AppendMetadataSamples;
import com.aperture.apertureservice.domain.recording.api.CancelAlerts;
import com.aperture.apertureservice.domain.recording.api.EndRecording;
import com.aperture.apertureservice.domain.recording.api.EnsureRecording;
import com.aperture.apertureservice.domain.recording.api.UploadClip;
import com.aperture.apertureservice.infrastructure.configuration.AppProperties;
import com.aperture.apertureservice.infrastructure.security.AuthenticatedDevice;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/device")
public class DeviceApiController {

    private final EnsureRecording ensureRecording;
    private final EndRecording endRecording;
    private final AppendMetadataSamples appendSamples;
    private final GetAlertConfiguration getAlertConfig;
    private final ListEmergencyContacts listContacts;
    private final CancelAlerts cancelAlertsPort;
    private final UploadClip uploadClip;
    private final AppProperties props;

    public DeviceApiController(EnsureRecording ensureRecording, EndRecording endRecording,
                               AppendMetadataSamples appendSamples, GetAlertConfiguration getAlertConfig,
                               ListEmergencyContacts listContacts, CancelAlerts cancelAlertsPort,
                               UploadClip uploadClip, AppProperties props) {
        this.ensureRecording = ensureRecording;
        this.endRecording = endRecording;
        this.appendSamples = appendSamples;
        this.getAlertConfig = getAlertConfig;
        this.listContacts = listContacts;
        this.cancelAlertsPort = cancelAlertsPort;
        this.uploadClip = uploadClip;
        this.props = props;
    }

    private static AuthenticatedDevice device(Authentication auth) {
        return (AuthenticatedDevice) auth.getPrincipal();
    }

    @GetMapping("/me")
    public DeviceApiDtos.DeviceMeResponse me(Authentication auth) {
        AuthenticatedDevice d = device(auth);
        return new DeviceApiDtos.DeviceMeResponse(d.userFullname(), d.deviceName());
    }

    @PutMapping("/recordings/{id}")
    public ResponseEntity<DeviceApiDtos.EnsureResponse> ensure(Authentication auth, @PathVariable UUID id,
            @RequestBody(required = false) DeviceApiDtos.EnsureRequest body) {
        EnsureResult result = ensureRecording.ensure(id, device(auth).userId(),
                body == null ? null : body.startedAt());
        Recording r = result.recording();
        DeviceApiDtos.EnsureResponse response = new DeviceApiDtos.EnsureResponse(r.id(),
                r.status().name(), r.countdownEndsAt(),
                WatchUrls.of(props.publicOrigin(), r.id(), r.viewSecret()));
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(response);
    }

    @PostMapping("/recordings/{id}/end")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void end(Authentication auth, @PathVariable UUID id) {
        endRecording.end(id, device(auth).userId());
    }

    @PostMapping("/recordings/{id}/metadata-samples")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DeviceApiDtos.AcceptedResponse samples(Authentication auth, @PathVariable UUID id,
            @Valid @RequestBody DeviceApiDtos.SamplesRequest body) {
        int accepted = appendSamples.append(id, device(auth).userId(), body.samples().stream()
                .map(s -> new AppendMetadataSamples.NewSample(s.latitude(), s.longitude(),
                        s.clientTimestamp(), s.deviceInfo()))
                .toList());
        return new DeviceApiDtos.AcceptedResponse(accepted);
    }

    @PostMapping("/recordings/{id}/cancel-alerts")
    public DeviceApiDtos.CancelAlertsResponse cancelAlerts(Authentication auth, @PathVariable UUID id) {
        CancelResult result = cancelAlertsPort.cancelAlerts(id, device(auth).userId());
        return new DeviceApiDtos.CancelAlertsResponse(result.cancelled(), result.alertsAlreadyDispatched());
    }

    @GetMapping("/alert-config")
    public DeviceApiDtos.DeviceAlertConfigResponse alertConfig(Authentication auth) {
        UUID userId = device(auth).userId();
        return new DeviceApiDtos.DeviceAlertConfigResponse(
                getAlertConfig.get(userId).countdownDurationSeconds(),
                !listContacts.list(userId).isEmpty());
    }

    @PostMapping(value = "/recordings/{id}/clips", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DeviceApiDtos.ClipUploadResponse uploadClip(
            Authentication auth,
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            @RequestParam("startTime") Instant startTime,
            @RequestParam("endTime") Instant endTime,
            @RequestParam(value = "quality", required = false) String quality,
            @RequestParam("clipId") String clipId)
            throws IOException {
        RecordingSegment seg = uploadClip.upload(id, device(auth).userId(),
                file.getInputStream(), sanitizeFilename(file.getOriginalFilename()),
                file.getSize(), startTime, endTime, quality, clipId);
        return new DeviceApiDtos.ClipUploadResponse(seg.id(), seg.segmentNumber(),
                seg.source().name(), seg.quality(), seg.startTime(), seg.endTime(), seg.sizeBytes());
    }

    private static String sanitizeFilename(String original) {
        if (original == null || original.isBlank()) return "clip.mp4";
        return Path.of(original).getFileName().toString();
    }
}

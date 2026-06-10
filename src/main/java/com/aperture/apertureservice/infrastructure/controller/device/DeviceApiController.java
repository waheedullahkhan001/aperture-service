package com.aperture.apertureservice.infrastructure.controller.device;

import com.aperture.apertureservice.domain.emergency.api.GetAlertConfiguration;
import com.aperture.apertureservice.domain.emergency.api.ListEmergencyContacts;
import com.aperture.apertureservice.domain.recording.EnsureResult;
import com.aperture.apertureservice.domain.recording.Recording;
import com.aperture.apertureservice.domain.recording.WatchUrls;
import com.aperture.apertureservice.domain.recording.api.AppendMetadataSamples;
import com.aperture.apertureservice.domain.recording.api.EndRecording;
import com.aperture.apertureservice.domain.recording.api.EnsureRecording;
import com.aperture.apertureservice.infrastructure.configuration.AppProperties;
import com.aperture.apertureservice.infrastructure.security.AuthenticatedDevice;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/device")
public class DeviceApiController {

    private final EnsureRecording ensureRecording;
    private final EndRecording endRecording;
    private final AppendMetadataSamples appendSamples;
    private final GetAlertConfiguration getAlertConfig;
    private final ListEmergencyContacts listContacts;
    private final AppProperties props;

    public DeviceApiController(EnsureRecording ensureRecording, EndRecording endRecording,
                               AppendMetadataSamples appendSamples, GetAlertConfiguration getAlertConfig,
                               ListEmergencyContacts listContacts, AppProperties props) {
        this.ensureRecording = ensureRecording;
        this.endRecording = endRecording;
        this.appendSamples = appendSamples;
        this.getAlertConfig = getAlertConfig;
        this.listContacts = listContacts;
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

    @GetMapping("/alert-config")
    public DeviceApiDtos.DeviceAlertConfigResponse alertConfig(Authentication auth) {
        UUID userId = device(auth).userId();
        return new DeviceApiDtos.DeviceAlertConfigResponse(
                getAlertConfig.get(userId).countdownDurationSeconds(),
                !listContacts.list(userId).isEmpty());
    }
}

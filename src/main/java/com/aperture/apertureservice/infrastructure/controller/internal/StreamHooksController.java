package com.aperture.apertureservice.infrastructure.controller.internal;

import com.aperture.apertureservice.ddd.BadRequest;
import com.aperture.apertureservice.ddd.Forbidden;
import com.aperture.apertureservice.ddd.Unauthorized;
import com.aperture.apertureservice.domain.recording.api.AuthorizePublish;
import com.aperture.apertureservice.domain.recording.api.AuthorizeView;
import com.aperture.apertureservice.domain.recording.api.EndRecording;
import com.aperture.apertureservice.domain.recording.api.EnsureRecording;
import com.aperture.apertureservice.domain.recording.api.MarkStreaming;
import com.aperture.apertureservice.domain.recording.api.RecordSegmentNotification;
import com.aperture.apertureservice.domain.account.api.IdentifyDevice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/internal/streams")
public class StreamHooksController {

    private static final Logger log = LoggerFactory.getLogger(StreamHooksController.class);

    private final AuthorizePublish authorizePublish;
    private final AuthorizeView authorizeView;
    private final IdentifyDevice identifyDevice;
    private final EnsureRecording ensureRecording;
    private final MarkStreaming markStreaming;
    private final EndRecording endRecording;
    private final RecordSegmentNotification segmentNotification;

    public StreamHooksController(AuthorizePublish authorizePublish, AuthorizeView authorizeView,
                                 IdentifyDevice identifyDevice, EnsureRecording ensureRecording,
                                 MarkStreaming markStreaming, EndRecording endRecording,
                                 RecordSegmentNotification segmentNotification) {
        this.authorizePublish = authorizePublish;
        this.authorizeView = authorizeView;
        this.identifyDevice = identifyDevice;
        this.ensureRecording = ensureRecording;
        this.markStreaming = markStreaming;
        this.endRecording = endRecording;
        this.segmentNotification = segmentNotification;
    }

    /** MediaMTX authMethod:http callback. Non-2xx = deny. */
    @PostMapping("/auth")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void auth(@RequestBody HookDtos.AuthRequest body) {
        UUID recordingId = StreamPaths.recordingId(body.path())
                .orElseThrow(() -> new Forbidden("PATH_FORBIDDEN", "Unknown stream path"));
        switch (body.action() == null ? "" : body.action()) {
            case "publish" -> {
                String token = StreamPaths.queryParam(body.query(), "token")
                        .or(() -> Optional.ofNullable(body.password()).filter(p -> !p.isBlank()))
                        .orElseThrow(() -> new Unauthorized("INVALID_DEVICE_TOKEN", "Missing device token"));
                authorizePublish.authorizePublish(token, recordingId);
            }
            case "read", "playback" -> {
                String secret = StreamPaths.queryParam(body.query(), "t")
                        .or(() -> StreamPaths.queryParam(body.query(), "token"))
                        .orElse("");
                authorizeView.authorizeView(recordingId, secret);
            }
            default -> throw new Forbidden("ACTION_FORBIDDEN", "Action not allowed");
        }
    }

    @PostMapping("/hooks/publish-start")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void publishStart(@RequestBody HookDtos.PublishStart body) {
        UUID recordingId = StreamPaths.recordingId(body.path())
                .orElseThrow(() -> new BadRequest("PATH_INVALID", "Unknown stream path"));
        String token = StreamPaths.queryParam(body.query(), "token")
                .orElseThrow(() -> new Unauthorized("INVALID_DEVICE_TOKEN", "Missing device token"));
        UUID userId = identifyDevice.identify(token).userId();
        ensureRecording.ensure(recordingId, userId, null);
        markStreaming.markStreaming(recordingId, userId);
    }

    @PostMapping("/hooks/publish-end")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void publishEnd(@RequestBody HookDtos.PublishEnd body) {
        StreamPaths.recordingId(body.path()).ifPresentOrElse(
                endRecording::endAsSystem,
                () -> log.warn("publish-end for unknown path {}", body.path()));
    }

    @PostMapping("/hooks/segment-complete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void segmentComplete(@RequestBody HookDtos.SegmentComplete body) {
        StreamPaths.recordingId(body.path()).ifPresentOrElse(
                id -> segmentNotification.segmentCompleted(id, body.segmentPath(),
                        StreamPaths.durationSeconds(body.duration())),
                () -> log.warn("segment-complete for unknown path {}", body.path()));
    }
}

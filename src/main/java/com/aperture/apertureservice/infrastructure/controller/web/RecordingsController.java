package com.aperture.apertureservice.infrastructure.controller.web;

import com.aperture.apertureservice.domain.recording.RecordingDetail;
import com.aperture.apertureservice.domain.recording.RecordingStatus;
import com.aperture.apertureservice.domain.recording.SegmentDownload;
import com.aperture.apertureservice.domain.recording.WatchUrls;
import com.aperture.apertureservice.domain.recording.api.DeleteRecording;
import com.aperture.apertureservice.domain.recording.api.DownloadSegment;
import com.aperture.apertureservice.domain.recording.api.GetRecording;
import com.aperture.apertureservice.domain.recording.api.ListRecordings;
import com.aperture.apertureservice.domain.recording.api.RevokeWatchLink;
import com.aperture.apertureservice.infrastructure.configuration.AppProperties;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/recordings")
public class RecordingsController {

    private final ListRecordings listRecordings;
    private final GetRecording getRecording;
    private final DownloadSegment downloadSegment;
    private final DeleteRecording deleteRecording;
    private final RevokeWatchLink revokeWatchLink;
    private final AppProperties props;

    public RecordingsController(ListRecordings listRecordings, GetRecording getRecording,
                                DownloadSegment downloadSegment, DeleteRecording deleteRecording,
                                RevokeWatchLink revokeWatchLink, AppProperties props) {
        this.listRecordings = listRecordings;
        this.getRecording = getRecording;
        this.downloadSegment = downloadSegment;
        this.deleteRecording = deleteRecording;
        this.revokeWatchLink = revokeWatchLink;
        this.props = props;
    }

    @GetMapping
    public RecordingDtos.PageResponse<RecordingDtos.RecordingResponse> list(Authentication auth,
            @RequestParam(required = false) RecordingStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return RecordingDtos.PageResponse.from(
                listRecordings.list(MeController.userId(auth), Optional.ofNullable(status), page, size),
                RecordingDtos.RecordingResponse::from);
    }

    @GetMapping("/{id}")
    public RecordingDtos.DetailResponse detail(Authentication auth, @PathVariable UUID id) {
        RecordingDetail d = getRecording.get(MeController.userId(auth), id);
        return new RecordingDtos.DetailResponse(RecordingDtos.RecordingResponse.from(d.recording()),
                d.segments().stream().map(RecordingDtos.SegmentResponse::from).toList(),
                d.recentSamples().stream().map(RecordingDtos.SampleResponse::from).toList(),
                WatchUrls.of(props.publicOrigin(), d.recording().id(), d.recording().viewSecret()));
    }

    @GetMapping("/{id}/segments/{n}/download")
    public ResponseEntity<InputStreamResource> download(Authentication auth, @PathVariable UUID id,
                                                        @PathVariable int n) {
        SegmentDownload dl = downloadSegment.download(MeController.userId(auth), id, n);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + dl.filename() + "\"")
                .contentLength(dl.sizeBytes())
                .contentType(MediaType.parseMediaType("video/mp4"))
                .body(new InputStreamResource(dl.stream()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication auth, @PathVariable UUID id) {
        deleteRecording.delete(MeController.userId(auth), id);
    }

    @PostMapping("/{id}/revoke-link")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeLink(Authentication auth, @PathVariable UUID id) {
        revokeWatchLink.revoke(MeController.userId(auth), id);
    }
}

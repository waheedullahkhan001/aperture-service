package com.aperture.apertureservice.infrastructure.controller.web;

import com.aperture.apertureservice.domain.recording.MetadataSample;
import com.aperture.apertureservice.domain.recording.Recording;
import com.aperture.apertureservice.domain.recording.RecordingDetail;
import com.aperture.apertureservice.domain.recording.RecordingSegment;
import com.aperture.apertureservice.domain.recording.RecordingStatus;
import com.aperture.apertureservice.domain.recording.SegmentDownload;
import com.aperture.apertureservice.domain.recording.WatchView;
import com.aperture.apertureservice.domain.recording.api.DeleteRecording;
import com.aperture.apertureservice.domain.recording.api.DownloadSegment;
import com.aperture.apertureservice.domain.recording.api.GetRecording;
import com.aperture.apertureservice.domain.recording.api.GetWatchView;
import com.aperture.apertureservice.domain.recording.api.ListRecordings;
import com.aperture.apertureservice.ddd.PageOf;
import com.aperture.apertureservice.infrastructure.controller.publicapi.WatchController;
import com.aperture.apertureservice.infrastructure.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {RecordingsController.class, WatchController.class})
@AutoConfigureMockMvc(addFilters = false)
class RecordingsControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean ListRecordings listRecordings;
    @MockitoBean GetRecording getRecording;
    @MockitoBean DownloadSegment downloadSegment;
    @MockitoBean DeleteRecording deleteRecording;
    @MockitoBean GetWatchView getWatchView;

    private final UUID userId = UUID.randomUUID();
    private final UUID recId = UUID.randomUUID();
    private final Instant t = Instant.parse("2026-06-07T12:00:00Z");

    private Authentication asUser() {
        return new UsernamePasswordAuthenticationToken(new AuthenticatedUser(userId, UUID.randomUUID()), null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private Recording recording() {
        return new Recording(recId, userId, RecordingStatus.ENDED, t, t.plusSeconds(60), "apv_s", null, null);
    }

    @Test
    void listPagesAndFilters() throws Exception {
        when(listRecordings.list(userId, Optional.of(RecordingStatus.ENDED), 0, 20))
                .thenReturn(new PageOf<>(List.of(recording()), 0, 20, 1));
        mvc.perform(get("/api/v1/recordings?status=ENDED&page=0&size=20").principal(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("ENDED"));
    }

    @Test
    void detailDownloadDelete() throws Exception {
        when(getRecording.get(userId, recId)).thenReturn(new RecordingDetail(recording(),
                List.of(new RecordingSegment(1L, recId, 1, "/p", t, t.plusSeconds(30), 3, true)),
                List.of()));
        when(downloadSegment.download(userId, recId, 1)).thenReturn(
                new SegmentDownload(new ByteArrayInputStream(new byte[]{7, 7}), 2, recId + "-1.mp4"));

        mvc.perform(get("/api/v1/recordings/" + recId).principal(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.segments[0].segmentNumber").value(1));
        mvc.perform(get("/api/v1/recordings/" + recId + "/segments/1/download").principal(asUser()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString(recId + "-1.mp4")))
                .andExpect(header().longValue("Content-Length", 2))
                .andExpect(content().contentType("video/mp4"))
                .andExpect(content().bytes(new byte[]{7, 7}));
        mvc.perform(delete("/api/v1/recordings/" + recId).principal(asUser()))
                .andExpect(status().isNoContent());
        verify(deleteRecording).delete(userId, recId);
    }

    @Test
    void watchIsPublicWithToken() throws Exception {
        when(getWatchView.watch(recId, "apv_s")).thenReturn(new WatchView("Owner", t,
                RecordingStatus.RECORDING,
                Optional.of(new MetadataSample(1L, recId, new BigDecimal("1.5"), new BigDecimal("2.5"), t, t, "Pixel")),
                "http://hls", "http://whep"));
        mvc.perform(get("/api/public/watch/" + recId + "?t=apv_s"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerName").value("Owner"))
                .andExpect(jsonPath("$.latestSample.latitude").value(1.5))
                .andExpect(jsonPath("$.hlsUrl").value("http://hls"));
    }
}

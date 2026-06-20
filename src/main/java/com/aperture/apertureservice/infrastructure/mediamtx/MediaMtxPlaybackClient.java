package com.aperture.apertureservice.infrastructure.mediamtx;

import com.aperture.apertureservice.domain.recording.spi.PlaybackSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class MediaMtxPlaybackClient implements PlaybackSource {

    private static final Logger log = LoggerFactory.getLogger(MediaMtxPlaybackClient.class);

    private final String playbackBase;
    private final HttpClient http;
    private final ObjectMapper mapper;

    public MediaMtxPlaybackClient(String playbackBase, ObjectMapper mapper) {
        this.playbackBase = playbackBase;
        this.http = HttpClient.newHttpClient();
        this.mapper = mapper;
    }

    @Override
    public InputStream fetch(String path, String startRfc3339, double durationSeconds,
                             String viewSecret) throws IOException {
        String url = playbackBase + "/get"
                + "?path=" + enc(path)
                + "&start=" + enc(startRfc3339)
                + "&duration=" + durationSeconds
                + "&format=mp4"
                + "&t=" + enc(viewSecret);
        log.debug("Fetching timeline MP4 from MediaMTX: {}", url);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
        try {
            HttpResponse<InputStream> resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() != 200) {
                resp.body().close();
                throw new IOException("MediaMTX returned HTTP " + resp.statusCode() + " for path " + path);
            }
            return resp.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("MediaMTX fetch interrupted", e);
        }
    }

    @Override
    public List<Span> list(String path, String viewSecret) throws IOException {
        String url = playbackBase + "/list"
                + "?path=" + enc(path)
                + "&t=" + enc(viewSecret);
        log.debug("Listing spans from MediaMTX: {}", url);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
        try {
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new IOException("MediaMTX list returned HTTP " + resp.statusCode());
            }
            JsonNode root = mapper.readTree(resp.body());
            List<Span> spans = new ArrayList<>();
            for (JsonNode node : root) {
                String start = node.get("start").asText();
                double duration = node.get("duration").asDouble();
                spans.add(new Span(start, duration));
            }
            return spans;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("MediaMTX list interrupted", e);
        }
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}

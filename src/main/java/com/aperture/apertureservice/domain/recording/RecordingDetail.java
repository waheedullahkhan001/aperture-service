package com.aperture.apertureservice.domain.recording;

import java.util.List;

public record RecordingDetail(Recording recording, List<RecordingSegment> segments,
                              List<MetadataSample> recentSamples) {}

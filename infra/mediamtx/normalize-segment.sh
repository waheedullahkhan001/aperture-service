#!/bin/sh
# normalize-segment.sh — called by mediamtx runOnRecordSegmentComplete
#
# Remuxes the just-finished fmp4 segment to fix non-monotonic DTS (caused by the
# phone inserting standalone SPS/PPS access units at keyframe boundaries) and adds
# faststart (moov atom before mdat) for immediate browser seek support.
#
# Why this is needed: the phone's RTSP publisher (RootEncoder) injects standalone
# SPS/PPS NAL access units at every keyframe with the same DTS as the preceding
# B-frame. MediaMTX's fmp4 recorder writes them verbatim, producing duplicate DTS
# in the resulting segment. Browsers' MSE is strict about monotonic DTS and stalls.
# (Verified: a clean ffmpeg→MediaMTX recording has 0 DTS issues; phone→MediaMTX
# produces ~18-19 non-monotonic DTS per segment.)
#
# Fix: -fflags +genpts makes ffmpeg bump duplicate DTS by the minimum unit (1 tick);
# -movflags +faststart moves moov to the front (needed for Range/seek).
# No re-encode — pure remux, ~100x realtime, no quality loss.
#
# Failure mode: if ffmpeg fails, we leave the original segment untouched and still
# notify the backend (so the DB records it). The segment plays in VLC but may stall
# in browser at keyframe boundaries — degraded but not broken.

set -eu

SEG="$MTX_SEGMENT_PATH"
TMP="${SEG}.norm.tmp"

# Remux: fix DTS + faststart. Input is the completed fmp4 segment MediaMTX just wrote.
# -f mp4 is required because ffmpeg 8.x cannot detect format from the .tmp extension.
if ffmpeg -y -loglevel error -i "$SEG" \
        -c copy \
        -fflags +genpts \
        -movflags +faststart \
        -f mp4 \
        "$TMP"; then
    # Atomic replace — no window where the file is absent
    mv "$TMP" "$SEG"
else
    rm -f "$TMP"
    echo "normalize-segment: WARN: ffmpeg remux failed for $SEG, keeping original" >&2
fi

# Notify backend (always — even if remux failed the segment is still usable)
curl -sS -o /dev/null -X POST http://backend:8080/internal/streams/hooks/segment-complete \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $MEDIAMTX_WEBHOOK_SECRET" \
    -d "{\"path\":\"$MTX_PATH\",\"segmentPath\":\"$SEG\",\"duration\":\"$MTX_SEGMENT_DURATION\"}"

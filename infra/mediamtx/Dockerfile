# mediamtx + ffmpeg + curl
#
# Why the -ffmpeg variant: the stock bluenviron image is shell-less (verified in
# Step-0 spike), so runOn* hooks silently cannot run at all. The -ffmpeg variant
# includes both /bin/sh (Alpine) and /usr/bin/ffmpeg, giving us everything we need:
#  • sh — required by mediamtx to invoke runOn* hook commands
#  • ffmpeg — used by normalize-segment.sh to remux completed segments
#  • curl is NOT in the ffmpeg variant, so we add it via apk
#
# Pinned to the version the Step-0 spike validated (hook names, auth callback,
# query passthrough) — bump only after re-checking those contracts.
FROM bluenviron/mediamtx:1.19.1-ffmpeg
RUN apk add --no-cache curl
COPY mediamtx.template.yml /mediamtx.template.yml
COPY entrypoint.sh /entrypoint.sh
COPY normalize-segment.sh /normalize-segment.sh
RUN chmod +x /entrypoint.sh /normalize-segment.sh
ENTRYPOINT ["/entrypoint.sh"]

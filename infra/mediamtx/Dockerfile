# mediamtx + curl: the stock bluenviron image is shell-less, so runOn* hooks
# (which mediamtx executes via sh -c) silently cannot run at all. Verified in
# the Step-0 spike. Binary lives at /mediamtx in the stock image.
# pinned to the version the Step-0 spike validated (hook names, auth callback,
# query passthrough) — bump only after re-checking those contracts
FROM bluenviron/mediamtx:1.19.1 AS mediamtx

FROM alpine:3.21
RUN apk add --no-cache curl
COPY --from=mediamtx /mediamtx /mediamtx
COPY mediamtx.template.yml /mediamtx.template.yml
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh
ENTRYPOINT ["/entrypoint.sh"]

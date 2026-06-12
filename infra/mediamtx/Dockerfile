# mediamtx + curl: the stock bluenviron image is shell-less, so runOn* hooks
# (which mediamtx executes via sh -c) silently cannot run at all. Verified in
# the Step-0 spike. Binary lives at /mediamtx in the stock image.
FROM bluenviron/mediamtx:latest AS mediamtx

FROM alpine:3.21
RUN apk add --no-cache curl
COPY --from=mediamtx /mediamtx /mediamtx
COPY mediamtx.template.yml /mediamtx.template.yml
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh
ENTRYPOINT ["/entrypoint.sh"]

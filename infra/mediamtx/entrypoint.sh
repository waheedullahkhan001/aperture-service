#!/bin/sh
# Renders the webhook secret into the mediamtx config, then execs mediamtx.
# The secret must be URL-safe AND sed-safe: it is substituted with sed and
# travels in the authHTTPAddress query string. Hex/alphanumeric only.
set -eu
: "${MEDIAMTX_WEBHOOK_SECRET:?MEDIAMTX_WEBHOOK_SECRET must be set}"
case "$MEDIAMTX_WEBHOOK_SECRET" in
  *[!A-Za-z0-9_-]*)
    echo "MEDIAMTX_WEBHOOK_SECRET must contain only [A-Za-z0-9_-] (use: openssl rand -hex 32)" >&2
    exit 1
    ;;
esac
sed "s|__WEBHOOK_SECRET__|${MEDIAMTX_WEBHOOK_SECRET}|g" /mediamtx.template.yml > /tmp/mediamtx.yml
exec /mediamtx /tmp/mediamtx.yml

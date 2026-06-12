#!/usr/bin/env bash
# Re-point the local stack's public origins at the current LAN IP, so watch links
# in emails and the HLS base resolve from a phone/other device on the same WiFi.
# Run this whenever the laptop's IP changes (DHCP), then it restarts the backend.
#
#   ./scripts/set-local-ip.sh            # auto-detect LAN IP
#   ./scripts/set-local-ip.sh 192.168.1.42   # or force one
set -euo pipefail
cd "$(dirname "$0")/.."

IP="${1:-}"
if [ -z "$IP" ]; then
  # first private, non-docker (172.17/172.18) IPv4
  IP=$(ip -4 -o addr show scope global 2>/dev/null \
        | awk '{print $4}' | cut -d/ -f1 \
        | grep -vE '^172\.1[78]\.' \
        | grep -E '^(192\.168|10\.|172\.)' | head -1)
fi
[ -n "$IP" ] || { echo "Could not detect a LAN IP — pass one explicitly."; exit 1; }

sed -i -E \
  -e "s#^APP_PUBLIC_ORIGIN=.*#APP_PUBLIC_ORIGIN=http://$IP#" \
  -e "s#^MEDIAMTX_HLS_BASE=.*#MEDIAMTX_HLS_BASE=http://$IP#" \
  -e "s#^MEDIAMTX_WEBRTC_BASE=.*#MEDIAMTX_WEBRTC_BASE=http://$IP#" \
  .env

echo "Origins set to http://$IP — restarting backend…"
docker compose up -d backend >/dev/null
echo
echo "Ready. On the phone / app, use base URL:  http://$IP"
echo "Open ports 80 + 8554 in the firewall if you haven't:"
echo "  sudo firewall-cmd --add-port=80/tcp --add-port=8554/tcp"

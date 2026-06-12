#!/usr/bin/env bash
# One-command local demo bring-up: starts the whole stack, points public origins
# at the current LAN IP (so the phone + watch links work), prints the demo URLs.
set -euo pipefail
cd "$(dirname "$0")/.."

echo "Starting the stack…"
docker compose up -d

# point origins/watch-links at the current LAN IP and restart the backend
./scripts/set-local-ip.sh >/dev/null

# wait for health (backend is the slow one)
echo -n "Waiting for backend"
for _ in $(seq 1 30); do
  if curl -sf -o /dev/null --max-time 2 http://localhost:8081/actuator/health; then break; fi
  echo -n "."; sleep 2
done
echo

IP=$(grep '^APP_PUBLIC_ORIGIN=' .env | sed 's|.*http://||')
echo
docker compose ps --format "  {{.Name}}  {{.Status}}"
cat <<EOF

  ───────────────────────────────────────────────
  DEMO READY
  Web UI + watch page : http://localhost   (this laptop)
                        http://$IP   (phone / other devices)
  Alert/verify emails : http://localhost:8025   (mailpit inbox)
  Phone → Settings    : Server URL  http://$IP   + paste device token
  Fake camera (no phone):
    ffmpeg -re -stream_loop -1 -f lavfi -i testsrc=size=640x480:rate=15 \\
      -f lavfi -i sine=frequency=440 -c:v libopenh264 -b:v 500k -g 30 \\
      -c:a aac -t 120 -rtsp_transport tcp \\
      "rtsp://$IP:8554/aperture/\$(uuidgen)?token=<APD_TOKEN>"
  ───────────────────────────────────────────────
EOF

#!/usr/bin/env bash
# Full emergency-journey smoke test against the local compose stack.
# Prereqs: docker compose up -d --build (all healthy), ffmpeg, curl, python3.
# Covers: register -> verify (email via mailpit) -> device token -> contact +
# zero countdown -> ffmpeg publish -> RECORDING -> alert email -> public watch
# -> live HLS -> segment rollover -> ENDED -> download -> cancel-alerts flow
# -> delete.
set -euo pipefail

API="http://localhost:8081"
MAILPIT="http://localhost:8025"
HLS="http://localhost:8888"
TS=$(date +%s)
EMAIL="smoke-${TS}@example.com"
PASS='abcdef1!'

say()  { printf '\n== %s\n' "$*"; }
fail() { printf 'FAIL: %s\n' "$*" >&2; exit 1; }

json() { python3 -c "import sys,json;d=json.load(sys.stdin);print(d$1)"; }

mail_text() { # $1 = mailpit search query; prints newest matching message text
  local id
  id=$(curl -s "$MAILPIT/api/v1/search?query=$1" | json "['messages'][0]['ID']")
  curl -s "$MAILPIT/api/v1/message/$id" | json "['Text']" | tr -d '\r'
}

pick_encoder() {
  if ffmpeg -hide_banner -encoders 2>/dev/null | grep libx264 > /dev/null; then echo libx264
  elif ffmpeg -hide_banner -encoders 2>/dev/null | grep libopenh264 > /dev/null; then echo libopenh264
  else fail "no H264 encoder in ffmpeg"; fi
}

say "register + verify ($EMAIL)"
curl -sf -X POST "$API/api/v1/auth/register" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"fullname\":\"Smoke Test\",\"password\":\"$PASS\"}"
sleep 2
CODE=$(mail_text "to:$EMAIL" | grep -oE '[0-9]{6}' | head -1)
[ -n "$CODE" ] || fail "no verification code in mailpit"
curl -sf -X POST "$API/api/v1/auth/verify-email" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"code\":\"$CODE\"}"

say "login + device token + contact + zero countdown"
JWT=$(curl -sf -X POST "$API/api/v1/auth/login" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASS\"}" | json "['accessToken']")
APD=$(curl -sf -X POST "$API/api/v1/me/devices" -H "Authorization: Bearer $JWT" \
  -H 'Content-Type: application/json' -d '{"name":"smoke-ffmpeg"}' | json "['token']")
curl -sf -X POST "$API/api/v1/me/contacts" -H "Authorization: Bearer $JWT" \
  -H 'Content-Type: application/json' -d '{"name":"Buddy","email":"buddy@example.com"}' > /dev/null
curl -sf -X PUT "$API/api/v1/me/alert-config" -H "Authorization: Bearer $JWT" \
  -H 'Content-Type: application/json' \
  -d '{"countdownDurationSeconds":0,"messageTemplate":"ALERT {{streamUrl}}"}' > /dev/null

ENC=$(pick_encoder)
say "publish 40s stream (encoder: $ENC, 2s GOP for segment rollover)"
REC=$(python3 -c "import uuid;print(uuid.uuid4())")
ffmpeg -hide_banner -loglevel error -re -stream_loop -1 \
  -f lavfi -i "testsrc=size=320x240:rate=10" \
  -f lavfi -i "sine=frequency=440:sample_rate=8000" \
  -c:v "$ENC" -b:v 200k -g 20 -c:a aac -b:a 32k -t 40 \
  -rtsp_transport tcp -f rtsp "rtsp://localhost:8554/aperture/${REC}?token=${APD}" &
FFPID=$!

say "recording goes RECORDING"
for i in $(seq 1 15); do
  STATUS=$(curl -s "$API/api/v1/recordings/$REC" -H "Authorization: Bearer $JWT" \
    | json "['recording']['status']" 2>/dev/null || echo "")
  [ "$STATUS" = "RECORDING" ] && break
  sleep 2
done
[ "$STATUS" = "RECORDING" ] || fail "recording never reached RECORDING (got: ${STATUS:-none})"

say "alert email arrives with watch link"
WATCH_URL=""
for i in $(seq 1 15); do
  # search by the recording uuid so stale emails from previous runs can't shadow it
  WATCH_URL=$(mail_text "$REC" 2>/dev/null | grep -oE 'http[^ ]*/watch/[^ ]*' | head -1 || true)
  echo "$WATCH_URL" | grep -q "$REC" && break
  sleep 2
done
echo "$WATCH_URL" | grep -q "$REC" || fail "no alert email with watch link for $REC"
SECRET=$(echo "$WATCH_URL" | sed 's/.*t=//')

say "public watch endpoint (no JWT)"
HLS_URL=$(curl -sf "$API/api/public/watch/$REC?t=$SECRET" | json "['hlsUrl']")
echo "hlsUrl: $HLS_URL"

say "live HLS playback (cookie session)"
# mediamtx returns 500 while the HLS muxer initialises (no segment buffered yet);
# retry for up to ~20s before declaring failure.
HLS_CODE="000"
for i in $(seq 1 10); do
  JAR=$(mktemp)
  HLS_CODE=$(curl -sL -c "$JAR" -b "$JAR" -o /dev/null -w "%{http_code}" "$HLS_URL")
  [ "$HLS_CODE" = "200" ] && break
  rm -f "$JAR"
  sleep 2
done
[ "$HLS_CODE" = "200" ] || fail "HLS index returned $HLS_CODE after retries"
VARIANT=$(curl -sL -c "$JAR" -b "$JAR" "$HLS_URL" | grep -oE '^[a-z0-9_]+_stream\.m3u8[^ ]*' | head -1)
[ -n "$VARIANT" ] || fail "no variant playlist in HLS index"
# child URL must share the index URL's host or the cookie won't be sent
HLS_DIR="${HLS_URL%/index.m3u8*}"
VCODE=$(curl -s -b "$JAR" -o /dev/null -w "%{http_code}" "$HLS_DIR/$VARIANT")
[ "$VCODE" = "200" ] || fail "variant playlist returned $VCODE (cookie session broken?)"
rm -f "$JAR"

say "wait for stream end -> ENDED with >=2 segments (30s rollover + final)"
wait "$FFPID" 2>/dev/null || true
sleep 5
DETAIL=$(curl -sf "$API/api/v1/recordings/$REC" -H "Authorization: Bearer $JWT")
STATUS=$(echo "$DETAIL" | json "['recording']['status']")
SEGS=$(echo "$DETAIL" | json "['segments'].__len__()")
[ "$STATUS" = "ENDED" ] || fail "status after stream end: $STATUS"
[ "$SEGS" -ge 2 ] || fail "expected >=2 segments, got $SEGS"

say "download segment 1"
DL=$(mktemp)
curl -sf -o "$DL" "$API/api/v1/recordings/$REC/segments/1/download" -H "Authorization: Bearer $JWT"
[ "$(stat -c %s "$DL")" -gt 10000 ] || fail "downloaded segment suspiciously small"
rm -f "$DL"

say "cancel-alerts flow (60s countdown, cancel before dispatch)"
curl -sf -X PUT "$API/api/v1/me/alert-config" -H "Authorization: Bearer $JWT" \
  -H 'Content-Type: application/json' \
  -d '{"countdownDurationSeconds":60,"messageTemplate":"ALERT {{streamUrl}}"}' > /dev/null
REC2=$(python3 -c "import uuid;print(uuid.uuid4())")
ffmpeg -hide_banner -loglevel error -re -stream_loop -1 \
  -f lavfi -i "testsrc=size=320x240:rate=10" \
  -f lavfi -i "sine=frequency=440:sample_rate=8000" \
  -c:v "$ENC" -b:v 200k -g 20 -c:a aac -b:a 32k -t 12 \
  -rtsp_transport tcp -f rtsp "rtsp://localhost:8554/aperture/${REC2}?token=${APD}" &
FFPID2=$!
sleep 6
CANCELLED=$(curl -sf -X POST "$API/api/v1/device/recordings/$REC2/cancel-alerts" \
  -H "Authorization: Bearer $APD" | json "['cancelled']")
[ "$CANCELLED" = "True" ] || fail "cancel-alerts returned cancelled=$CANCELLED"
wait "$FFPID2" 2>/dev/null || true
sleep 3
if mail_text "$REC2" 2>/dev/null | grep -q "$REC2"; then
  fail "alert email was sent for cancelled recording $REC2"
fi

say "delete recording -> 404 afterwards"
curl -sf -X DELETE "$API/api/v1/recordings/$REC" -H "Authorization: Bearer $JWT"
AFTER=$(curl -s -o /dev/null -w "%{http_code}" "$API/api/v1/recordings/$REC" -H "Authorization: Bearer $JWT")
[ "$AFTER" = "404" ] || fail "recording still readable after delete (HTTP $AFTER)"

printf '\nSMOKE PASS — full journey verified against the compose stack\n'

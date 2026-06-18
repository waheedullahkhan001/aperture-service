# Aperture Service

Backend service (API tier) of the **Aperture Emergency Camera System** — an Android-based
emergency video recording platform. The Android client publishes a live audio+video stream
to a media server (MediaMTX) during an emergency; this service owns accounts, devices,
recording lifecycle, metadata, emergency contacts, and alert email dispatch. Alert
recipients view the live stream through an unguessable watch link.

## Architecture

The deployed system follows a multi-tier architecture: Client Tier (Android), API Tier
(this service), Streaming Tier (MediaMTX/Go2RTC), and Presentation Tier (web interface).
Internally, this service is organized hexagonally (ports and adapters): pure-Java domain
logic in `domain/<aggregate>/{api,spi,service}`, with all framework integration under
`infrastructure/`. Behaviors shown on the User entity in the project class diagram are
realized as use-case classes following ports-and-adapters conventions.

Aggregates: `account` (users, sessions, devices), `recording` (lifecycle, segments,
metadata, stream authorization), `emergency` (contacts, alert configuration, dispatch).

## Running locally

Prereqs: JDK 21, Docker.

Dev runs against a local Postgres (matching prod). Start one once:

    docker run --rm -d --name aperture-dev-db -p 5432:5432 \
      -e POSTGRES_DB=aperture -e POSTGRES_USER=aperture -e POSTGRES_PASSWORD=devpass postgres:16-alpine

Then `./gradlew bootRun` (dev profile) — Flyway builds the schema on start.

    ./gradlew bootRun          # dev profile: Docker Postgres, emails logged to console
    ./gradlew test             # full suite (unit + Testcontainers Postgres integration)

Dev server: http://localhost:8081 (8081 on purpose — see the note in `application-dev.yml`).
API docs (Swagger UI) ship in a later iteration once springdoc supports Spring Boot 4.

## Profiles and environment

| Profile | Database | Email | Notes |
|---|---|---|---|
| `dev` (default) | Docker Postgres 16 | logged to console | permissive CORS |
| `test` | Testcontainers PostgreSQL | captured in-memory | used by the test suite |
| `prod` | PostgreSQL | SMTP | fails fast on placeholder secrets |

Prod environment variables: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`
(base64, 64+ bytes), `MEDIAMTX_WEBHOOK_SECRET`, `MEDIAMTX_HLS_BASE`, `MEDIAMTX_WEBRTC_BASE`,
`SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASSWORD`, `APP_PUBLIC_ORIGIN`,
`APP_RECORDINGS_PATH`, `APP_MAIL_FROM` (From address for alert emails).

## Authentication model

- **Web clients** log in with email/password and receive a short-lived JWT access token
  plus a rotating refresh token (reuse detection revokes all sessions; an immediate retry
  after a lost response is tolerated within a 30-second grace window).
- **Android devices** hold a long-lived revocable device token (`apd_…`), minted from a
  logged-in session (`POST /api/v1/me/devices`). The device stores only this token —
  nothing on the device can expire mid-emergency. Tokens are stored hashed and can be
  revoked from any logged-in session.
- **MediaMTX** delegates every publish/read decision to `POST /internal/streams/auth`
  (shared-secret protected). Emergency contacts view streams through per-recording
  capability URLs (`/watch/<id>?t=<secret>`).

## Known limitations (deliberate, documented)

1. No rate limiting beyond verification-code cooldown/lockout.
3. Device tokens do not expire by design (reliability first); compensated by hashed
   storage, TLS-only transport, and one-click revocation.
4. Web access tokens are irrevocable within their ~15-minute lifetime.
5. Recordings are not resumable; each publish session is a new recording.
6. Watch URLs do not expire until the recording is deleted.
7. Retro-upload of clips recorded while offline is reserved in the schema
   (`recording_segments.uploaded`) but not implemented; streaming is the upload path.
8. Password complexity per the project SRS (8+ chars, number, special character).
9. Single-instance deployment assumptions (in-process schedulers).
10. Live-stream URL token pass-through for HLS players is validated in the next
    (infrastructure) phase.

## API docs

springdoc-openapi does not yet have a release line targeting Spring Boot 4. API docs ship
in a later iteration once a compatible release is available.

## Roadmap

- Phase 2: docker-compose deployment — core stack done (see "Running the whole stack
  locally" below); Nginx proxy + watch page land next, then VPS deployment.
- Phase 3: web management UI.

## Running the whole stack locally

Prereqs: Docker + Docker Compose. The backend runs its **prod** profile inside compose — everything is environment-driven (UC-17), and a startup guard refuses to boot with placeholder secrets.

```bash
cp .env.example .env        # then fill in the three secrets (commands in the file)
docker compose up -d --build
docker compose ps           # postgres, backend, mediamtx, mailpit — all healthy
./scripts/compose-smoke.sh  # full emergency journey, no phone needed
```

Services and host ports:

| Service | Image | Host ports | Purpose |
|---|---|---|---|
| backend | built from `Dockerfile` (prod profile) | 8081 → 8080 | REST API + stream auth/hooks |
| mediamtx | built from `infra/mediamtx/` (pinned 1.19.1 + curl) | 8554, 8888, 8889 | RTSP publish, HLS, WebRTC |
| postgres | postgres:16-alpine | — | persistence (named volume `pgdata`) |
| mailpit | axllent/mailpit | 8025 | dev SMTP sink + web UI (http://localhost:8025) |

Two invariants worth knowing:

- The `recordings` volume is mounted at `/data/recordings` in BOTH mediamtx and the backend — the backend stores absolute segment paths from mediamtx hooks verbatim, so the mount paths must be identical.
- Publishers must use **RTSP over TCP** (interleaved). Only 8554/TCP is published; the UDP RTP/RTCP ports are not, so UDP transport times out.

Health and monitoring (UC-18): `GET /actuator/health` is open; `/actuator/metrics` and `/actuator/prometheus` require the webhook shared secret (`Authorization: Bearer ...`); `docker compose logs -f <service>` for logs.

Deployment requirements mapping: Docker deployment configuration provided (SRS-063); all required services included — application server, database, streaming server (SRS-064); Docker containerisation throughout (SRS-087). Compose healthchecks plus `depends_on: condition: service_healthy` make `docker compose up -d --build` verify inter-service communication before reporting up (UC-16).

Deliberate MVP limitations: containers run as root so the backend can delete segment files created by mediamtx in the shared volume; the mediamtx auth callback authenticates via a `?secret=` query parameter because mediamtx cannot send custom headers — in production the backend port is not exposed beyond the compose network, and the endpoint only answers allow/deny against 256-bit secrets.

### With the nginx proxy (same-origin, production shape)

`docker compose up -d` includes nginx on :80/:443 — the API, HLS, and the watch
page share one origin: `http://localhost/watch/<id>?t=...` plays live streams
with no CORS or cookie configuration. Internal surfaces (`/internal`,
`/actuator`) return 404 through the proxy; loopback ports (8081/8888/8889/8025)
remain for direct access and the smoke script.

### Server deployment (TLS)

1. DNS: an A record for your domain → server IP (Cloudflare: DNS-only, NOT
   proxied — RTSP runs on raw TCP 8554 which proxies won't carry).
2. Clone the repo, `cp .env.example .env`, fill real values (`DOMAIN`,
   `CERTBOT_EMAIL`, `APP_PUBLIC_ORIGIN=https://<domain>`,
   `MEDIAMTX_HLS_BASE=https://<domain>`, `MEDIAMTX_WEBRTC_BASE=https://<domain>`,
   real SMTP — `SMTP_SSL=true` for implicit-TLS ports like 465).
   `docker compose up -d --build` — the stack starts in HTTP bootstrap mode.
3. Issue the certificate:
   `docker compose run --rm certbot certonly --webroot -w /var/www/certbot -d <domain> --email <email> --agree-tos --no-eff-email`
4. Switch nginx to TLS:
   `cp infra/nginx/aperture-tls.conf.template infra/nginx/templates/aperture.conf.template && docker compose up -d --force-recreate nginx`
5. Renewal (cron, twice monthly):
   `docker compose run --rm certbot renew && docker compose up -d --force-recreate nginx`
6. Open firewall ports 80, 443, 8554 (both the cloud security list AND any
   on-instance iptables).

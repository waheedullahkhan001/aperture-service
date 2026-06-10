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

Prereqs: JDK 21, Docker (only for tests).

    ./gradlew bootRun          # dev profile: H2 in-memory, emails logged to console
    ./gradlew test             # full suite (unit + Testcontainers Postgres integration)

Swagger UI: http://localhost:8080/swagger-ui/index.html

## Profiles and environment

| Profile | Database | Email | Notes |
|---|---|---|---|
| `dev` (default) | H2 in-memory (PostgreSQL mode) | logged to console | permissive CORS, H2 console |
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

1. No schema migrations — Hibernate `ddl-auto`. A migration tool is planned before any
   real multi-user deployment.
2. No rate limiting beyond verification-code cooldown/lockout.
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

- Phase 2: docker-compose deployment (PostgreSQL, MediaMTX, Nginx, watch page).
- Phase 3: web management UI.

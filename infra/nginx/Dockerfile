# Aperture edge container: nginx + automatic Let's Encrypt TLS + the web UI, all in
# one image. This IS the "Nginx Container (nginx + static-web-files | HTTPS)" from the
# deployment diagram — certbot and auto-renewal run INSIDE it (jonasal/nginx-certbot),
# so there is no host cron, no separate certbot container, and no manual cert step.
# Renewal is self-healing; a dummy cert lets nginx boot before the real one arrives.

# --- stage 1: build the web UI from its own repo, pinned ---
FROM node:20-alpine AS web
ARG WEB_REPO=https://github.com/waheedullahkhan001/aperture-web.git
# pin to a commit/tag for reproducible builds; main is the moving default
ARG WEB_REF=main
RUN apk add --no-cache git
RUN git clone --depth 1 --branch "${WEB_REF}" "${WEB_REPO}" /web
WORKDIR /web
RUN npm ci && npm run setup:prod    # produces /web/site (static tree)

# --- stage 2: nginx + certbot, our routing config, web baked in ---
FROM jonasal/nginx-certbot:6.2.0-alpine
# jonasal has no runtime config templating, so the domain is fixed into the served
# config at build time. Passed via compose build args from .env (CERTBOT_EMAIL is a
# RUNTIME env var, set in compose — not here).
ARG DOMAIN
COPY aperture.conf /etc/nginx/conf.d/aperture.conf
RUN sed -i "s/__DOMAIN__/${DOMAIN}/g" /etc/nginx/conf.d/aperture.conf
COPY --from=web /web/site /usr/share/nginx/html

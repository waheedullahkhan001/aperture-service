# Aperture edge container: nginx + automatic Let's Encrypt TLS + the web UI, all in
# one image. This IS the "Nginx Container (nginx + static-web-files | HTTPS)" from the
# deployment diagram — certbot and auto-renewal run INSIDE it (jonasal/nginx-certbot),
# so there is no host cron, no separate certbot container, and no manual cert step.
# Renewal is self-healing; a dummy cert lets nginx boot before the real one arrives.

# The web UI is VENDORED: infra/nginx/site/ is a pre-built copy of the aperture-web
# repo (commit 562479c). No git clone or Node.js stage is needed — the build is fully
# offline once the base image is cached locally.
#
# To refresh the UI: rebuild aperture-web (npm run setup:prod in that repo), copy its
# site/ directory here (cp -r /path/to/aperture-web/site infra/nginx/site), then
# rebuild this image (docker compose build nginx).

FROM jonasal/nginx-certbot:6.2.0-alpine

# jonasal has no runtime config templating, so the domain is fixed into the served
# config at build time. Passed via compose build args from .env (CERTBOT_EMAIL is a
# RUNTIME env var, set in compose — not here).
ARG DOMAIN
COPY aperture.conf /etc/nginx/conf.d/aperture.conf
RUN sed -i "s/__DOMAIN__/${DOMAIN}/g" /etc/nginx/conf.d/aperture.conf
COPY site /usr/share/nginx/html

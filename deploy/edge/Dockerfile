# Nginx edge image (09-local-storage-and-nginx-spec.md,
# 04-containers-and-compose.md): pinned upstream Nginx + platform config; the
# React 19.3 SPA build output is baked in (prod contract: SPA + /media/* are
# served by the same edge). Build context is the REPO ROOT (SPA build stage
# consumes web/). No object-store tooling exists anywhere.
FROM node:22-alpine@sha256:0a7108bf6c7bf5de370ffb1a3ed6be93d405b43ff159f681a8d18c0e2bc2e402 AS spa
WORKDIR /web
COPY web/package.json web/package-lock.json ./
RUN npm ci
COPY web/ ./
RUN npm run build

FROM nginx:1.27-alpine@sha256:65645c7bb6a0661892a8b03b89d0743208a18dd2f3f17a54ef4b76fb8e2f2a10 AS runtime
# openssl backs the dev self-signed certificate fallback; prod mounts real certs.
RUN apk add --no-cache openssl
COPY deploy/edge/nginx.conf /etc/nginx/nginx.conf
COPY deploy/edge/default.conf /etc/nginx/conf.d/default.conf
COPY deploy/edge/locations.conf /etc/nginx/edge/locations.conf
COPY deploy/edge/security-headers.conf /etc/nginx/edge/security-headers.conf
COPY deploy/edge/proxy-headers.conf /etc/nginx/edge/proxy-headers.conf
COPY deploy/edge/dev-certs.sh /docker-entrypoint.d/40-dev-certs.sh
RUN chmod +x /docker-entrypoint.d/40-dev-certs.sh
COPY --from=spa /web/dist /usr/share/nginx/html/app
EXPOSE 80 443

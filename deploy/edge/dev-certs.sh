#!/bin/sh
# Dev TLS fallback: generate a self-signed pair when /etc/nginx/certs has none
# mounted. Production mounts real certs (secrets never bake into images; 04).
set -e

CERT_DIR="/etc/nginx/certs"
if [ -s "$CERT_DIR/tls.crt" ] && [ -s "$CERT_DIR/tls.key" ]; then
  echo "edge: using mounted TLS certificates"
  exit 0
fi

mkdir -p "$CERT_DIR"
openssl req -x509 -nodes -newkey rsa:2048 -days 365 \
  -subj "/CN=localhost" \
  -addext "subjectAltName=DNS:localhost,IP:127.0.0.1" \
  -keyout "$CERT_DIR/tls.key" -out "$CERT_DIR/tls.crt" 2>/dev/null
echo "edge: generated dev self-signed certificate (mount real certs in prod)"

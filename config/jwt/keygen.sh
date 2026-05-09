#!/usr/bin/env bash
# Generates an RS256 key pair for JWT signing.
# Run once before starting the application stack.
# private.pem is gitignored; public.pem is mounted read-only into all services.
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [[ -f "$DIR/private.pem" ]]; then
  echo "Keys already exist at $DIR — delete them first to regenerate."
  exit 0
fi

openssl genrsa -out "$DIR/private.pem" 2048
openssl rsa -in "$DIR/private.pem" -pubout -out "$DIR/public.pem"

chmod 600 "$DIR/private.pem"
chmod 644 "$DIR/public.pem"

echo "RS256 key pair written to $DIR"

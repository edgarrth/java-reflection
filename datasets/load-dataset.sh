#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# Installs external JARs and publishes runtime/payment-plugins.json.
exec python3 "$ROOT_DIR/scripts/install_plugins.py" "$@"

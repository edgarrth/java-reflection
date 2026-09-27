#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cp "$ROOT_DIR/datasets/payment-plugins.json" "$ROOT_DIR/src/main/resources/datasets/payment-plugins.json"
echo "Catálogo copiado a src/main/resources/datasets/payment-plugins.json"

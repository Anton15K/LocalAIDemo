#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

mkdir -p "$REPO_ROOT/docs/assets"

cp "$REPO_ROOT/src/main/resources/templates/landing_v2.html" "$REPO_ROOT/docs/index.html"
cp -R "$REPO_ROOT/src/main/resources/static/assets/"* "$REPO_ROOT/docs/assets/"

# Prevent GitHub Pages from running Jekyll.
touch "$REPO_ROOT/docs/.nojekyll"

echo "Exported landing_v2.html to $REPO_ROOT/docs"

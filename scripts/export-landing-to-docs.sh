#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

mkdir -p "$REPO_ROOT/docs"

python3 - <<'PY'
from __future__ import annotations

import os
import re
from pathlib import Path

repo_root_env = os.environ.get("REPO_ROOT")
if not repo_root_env:
	raise SystemExit("REPO_ROOT env var is required")
repo_root = Path(repo_root_env)
base_path = repo_root / "src/main/resources/templates/layout/base.html"
landing_path = repo_root / "src/main/resources/templates/landing.html"
out_path = repo_root / "docs/index.html"

base = base_path.read_text(encoding="utf-8")
landing = landing_path.read_text(encoding="utf-8")

# Extract landing fragment content.
start_marker = '<div th:fragment="content">'
start = landing.find(start_marker)
if start == -1:
	raise SystemExit(f"Could not find landing fragment marker: {start_marker}")
start = landing.find(">", start) + 1
end = landing.rfind("</div>\n</body>")
if end == -1:
	# fallback: last closing div before </body>
	end = landing.rfind("</div>")
landing_content = landing[start:end].strip() + "\n"

def strip_thymeleaf_attrs(html: str) -> str:
	# Remove Thymeleaf namespace (if present)
	html = re.sub(r"\s+xmlns:th=\"[^\"]*\"", "", html)
	# Remove th:* attributes (double and single quoted)
	html = re.sub(r"\s+th:[a-zA-Z0-9_-]+=\"[^\"]*\"", "", html)
	html = re.sub(r"\s+th:[a-zA-Z0-9_-]+='[^']*'", "", html)
	return html

base = strip_thymeleaf_attrs(base)
landing_content = strip_thymeleaf_attrs(landing_content)

# Replace the content placeholder in base.html.
pattern = re.compile(
	r"<div[^>]*>\s*<!-- Page content will be inserted here -->\s*</div>",
	re.MULTILINE,
)
match = pattern.search(base)
if not match:
	raise SystemExit("Could not find base content placeholder div")
base = base[: match.start()] + landing_content + base[match.end() :]

# Make title deterministic for static export.
base = re.sub(r"<title>.*?</title>", "<title>Erudit AI demo</title>", base, flags=re.DOTALL)

out_path.write_text(base, encoding="utf-8")
print(f"Wrote {out_path}")
PY

# Prevent GitHub Pages from running Jekyll.
touch "$REPO_ROOT/docs/.nojekyll"

echo "Exported landing.html to $REPO_ROOT/docs"

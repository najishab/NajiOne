#!/usr/bin/env python3
"""Extract the bird Lottie from the Freedom mockup into the app's raw resources.

Run once from the repository root (python scripts/extract_freedom_bird.py). The mockup embeds the
one real LottieFiles file as base64 inside initBird(); the app plays it from res/raw. The first
<script src="data:application/javascript;base64,..."> in the mockup is the Lottie WEB player and is
deliberately NOT extracted. Bird (c) its LottieFiles author, LottieFiles Simple License; the mockup
credits the original -- keep that credit wherever the project lists licenses.
"""
import base64
import json
import pathlib
import re
import sys

root = pathlib.Path(__file__).resolve().parent.parent
html = (root / "Freedom Redesign Mockup.html").read_text(encoding="utf-8")
m = re.search(r'path:"data:application/json;base64,([A-Za-z0-9+/=]+)"', html)
if not m:
    sys.exit("bird Lottie not found in the mockup -- has the file changed?")
raw = base64.b64decode(m.group(1))
data = json.loads(raw)  # fails loudly if the base64 is not the animation JSON
assert data.get("v") and data.get("layers"), "not a Lottie document"
out = root / "app/src/main/res/raw/freedom_bird.json"
out.parent.mkdir(parents=True, exist_ok=True)
out.write_bytes(raw)
print(f"wrote {out} ({out.stat().st_size} bytes, lottie v{data['v']})")
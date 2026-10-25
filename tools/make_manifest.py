#!/usr/bin/env python3
"""Writes version.json for Ice Client's updater.

Usage:
  python make_manifest.py build/libs/IceClient-0.2.0.jar 0.2.0 https://example.com/IceClient-0.2.0.jar "What changed"

Upload the jar and the generated version.json (over HTTPS), then put the link to
version.json into Mods > Auto Updater > Update URL.
"""
import hashlib, json, sys

if len(sys.argv) < 4:
    sys.exit(__doc__)
jar, version, url = sys.argv[1:4]
notes = sys.argv[4] if len(sys.argv) > 4 else ""
digest = hashlib.sha256(open(jar, "rb").read()).hexdigest()
json.dump({"version": version, "url": url, "sha256": digest, "notes": notes},
          open("version.json", "w"), indent=2)
print("wrote version.json  sha256 =", digest)

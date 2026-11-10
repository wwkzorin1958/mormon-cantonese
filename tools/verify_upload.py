#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Verify that an Internet Archive item contains every file the manifest expects.

Fetches https://archive.org/metadata/<identifier>, compares filenames and byte sizes
against build/manifest.json, and reports anything missing, renamed, or truncated.

With --write it fills each item's audioUrl and rewrites the manifest, so build_feed.py
can generate a live feed without a second argument.

Usage:
    python tools/verify_upload.py --identifier kcmcelia-mormon-cantonese-2026
    python tools/verify_upload.py --identifier ... --write
"""

from __future__ import annotations

import argparse
import json
import sys
import urllib.error
import urllib.request
from pathlib import Path
from urllib.parse import quote


def fetch_metadata(identifier: str) -> dict:
    url = f"https://archive.org/metadata/{identifier}"
    with urllib.request.urlopen(url, timeout=60) as resp:
        return json.loads(resp.read().decode("utf-8"))


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--identifier", required=True)
    ap.add_argument("--manifest", type=Path, default=Path("build/manifest.json"))
    ap.add_argument("--write", action="store_true",
                    help="write audioUrl into the manifest")
    ap.add_argument("--base-url", default="",
                    help="override the download base URL")
    args = ap.parse_args()

    manifest_path: Path = args.manifest
    if not manifest_path.exists():
        print(f"FATAL: manifest not found: {manifest_path}", file=sys.stderr)
        return 2
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    items = manifest["items"]

    try:
        meta = fetch_metadata(args.identifier)
    except urllib.error.HTTPError as e:
        print(f"FATAL: {args.identifier} not found (HTTP {e.code}).", file=sys.stderr)
        print("       Check the identifier, and that the item has finished deriving.",
              file=sys.stderr)
        return 2
    except Exception as e:  # noqa: BLE001 - network failures are all fatal here
        print(f"FATAL: could not reach archive.org: {e}", file=sys.stderr)
        return 2

    if not meta or not meta.get("metadata"):
        print(f"FATAL: no metadata for '{args.identifier}'.", file=sys.stderr)
        print("       The identifier probably does not exist, is still being created,",
              file=sys.stderr)
        print("       or the item is private/dark.", file=sys.stderr)
        return 2

    if not meta.get("files"):
        print("FATAL: item exists but lists no files. Has the upload finished?",
              file=sys.stderr)
        return 2

    remote: dict[str, int] = {}
    for f in meta["files"]:
        name = f.get("name", "")
        try:
            size = int(f.get("size", -1))
        except (TypeError, ValueError):
            size = -1
        remote[name] = size

    # Index variants so IA's occasional renaming shows up as a warning, not a mystery.
    by_lower = {k.lower(): k for k in remote}
    by_nospace = {k.replace(" ", "").lower(): k for k in remote}

    expected = [(it, Path(it["file"]).stem + ".m4a") for it in items]
    missing, mismatched, renamed = [], [], []

    for item, name in expected:
        if name in remote:
            remote_name = name
        elif name.lower() in by_lower:
            remote_name = by_lower[name.lower()]
            renamed.append((name, remote_name))
        elif name.replace(" ", "").lower() in by_nospace:
            remote_name = by_nospace[name.replace(" ", "").lower()]
            renamed.append((name, remote_name))
        else:
            missing.append(name)
            continue

        local_size = item.get("bytes", 0)
        remote_size = remote.get(remote_name, -1)
        # Encoded sizes are not the source MP3 sizes, so only flag an obviously broken file.
        if remote_size <= 0:
            mismatched.append((remote_name, "reported size 0 or unknown"))
        elif local_size and remote_size < 1024:
            mismatched.append((remote_name, f"suspiciously small: {remote_size} bytes"))

    total = len(expected)
    ok = total - len(missing) - len(mismatched) - len(renamed)
    print(f"identifier: {args.identifier}")
    print(f"remote files: {len(remote)}")
    print(f"expected:     {total}")
    print(f"matched:      {ok}")

    if renamed:
        print(f"\nRENAMED BY THE ARCHIVE ({len(renamed)}) — feed must use the remote name:")
        for want, got in renamed[:10]:
            print(f"  {want}\n    -> {got}")
    if mismatched:
        print(f"\nSUSPECT FILES ({len(mismatched)}):")
        for name, why in mismatched[:10]:
            print(f"  {name}: {why}")
    if missing:
        print(f"\nMISSING ({len(missing)}):")
        for name in missing[:20]:
            print(f"  {name}")
        if len(missing) > 20:
            print(f"  … and {len(missing) - 20} more")

    base = args.base_url or f"https://archive.org/download/{args.identifier}"
    if args.write and not missing:
        lookup = {}
        for want, got in renamed:
            lookup[want] = got
        for item in items:
            name = Path(item["file"]).stem + ".m4a"
            actual = lookup.get(name, name)
            item["audioUrl"] = f"{base}/{quote(actual)}"
        manifest_path.write_text(
            json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8"
        )
        print(f"\nwrote audioUrl for {len(items)} items into {manifest_path}")
        print(f"base url: {base}")

    if missing or mismatched:
        print("\nNOT READY: fix the above before generating the feed.")
        return 1

    print(f"\n{total}/{total} present. Base url: {base}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

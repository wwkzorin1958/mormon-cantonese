#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Upload build/audio to the Internet Archive, resumably.

Wraps internetarchive.upload() so that:
  * all 245 file paths are passed as Python objects, never through a shell
  * `checksum=True` skips anything already on the item, so re-running resumes
  * the run is verbose enough to watch, and prints a final reconciliation

Requires `ia configure` to have been run once (internetarchive >= 5).

Usage:
    python tools/upload_audio.py [--identifier ID] [--dir build/audio]
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

import internetarchive as ia

METADATA = {
    "title": "摩爾門經（廣東話）",
    "creator": "Celia Chi Mei Keung",
    "description": (
        "摩爾門經廣東話錄音全書：六段前言與十五部書共二百三十九章。"
        "錄音：Celia Chi Mei Keung。免費收聽，不作販售。"
    ),
    "language": "zh-HK",
    "mediatype": "audio",
    "collection": "opensource_audio",
    "licenseurl": "https://creativecommons.org/licenses/by-nc-nd/4.0/",
}


def remote_names(identifier: str) -> set[str]:
    item = ia.get_item(identifier)
    return {f["name"] for f in (item.files or [])}


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--identifier", default="kcmcelia-mormon-cantonese-2026")
    ap.add_argument("--dir", type=Path, default=Path("build/audio"))
    ap.add_argument("--retries", type=int, default=5)
    args = ap.parse_args()

    files = sorted(args.dir.glob("*.m4a"))
    if not files:
        print(f"FATAL: no .m4a files in {args.dir}", file=sys.stderr)
        return 2

    already = remote_names(args.identifier)
    missing = [f for f in files if f.name not in already]
    print(f"local files:   {len(files)}")
    print(f"already there: {len(files) - len(missing)}")
    print(f"to upload:     {len(missing)}")

    if not missing:
        print("nothing to do")
        return 0

    responses = ia.upload(
        args.identifier,
        files=[str(f) for f in missing],
        metadata=METADATA,
        checksum=True,
        retries=args.retries,
        retries_sleep=10,
        verbose=True,
    )

    failures = [r for r in responses if getattr(r, "status_code", 200) not in (200, 201)]
    if failures:
        print(f"\n{len(failures)} upload(s) failed:", file=sys.stderr)
        for r in failures[:10]:
            print(f"  {getattr(r, 'url', '?')} -> {getattr(r, 'status_code', '?')}",
                  file=sys.stderr)

    final = remote_names(args.identifier)
    present = sum(1 for f in files if f.name in final)
    print(f"\nreconcile: {present}/{len(files)} files present on the item")
    return 0 if present == len(files) else 1


if __name__ == "__main__":
    raise SystemExit(main())

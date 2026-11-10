#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Generate feed.xml (RSS 2.0 + iTunes) from build/manifest.json.

The feed and the Android app read the same manifest, so titles, chapter numbers and
durations cannot drift apart.

Ordering: podcast clients list newest-first. Every item's pubDate is therefore set
*descending* by canonical index (item 0001 gets the newest date), so the feed reads
top-to-bottom in reading order in Apple Podcasts and similar clients.

Usage:
    python tools/build_feed.py \
        --base-url https://archive.org/download/<identifier> \
        --site-url https://<user>.github.io/<repo>/ \
        --email you@example.com \
        --artwork https://<user>.github.io/<repo>/artwork.jpg

Run it again after the audio is encoded to pick up real enclosure sizes; otherwise
sizes are estimated from duration at 48 kbps.
"""

from __future__ import annotations

import argparse
import json
import math
import sys
import xml.etree.ElementTree as ET
from datetime import datetime, timedelta, timezone
from email.utils import format_datetime
from pathlib import Path
from urllib.parse import quote

ITUNES = "http://www.itunes.com/dtds/podcast-1.0.dtd"
ATOM = "http://www.w3.org/2005/Atom"

ET.register_namespace("itunes", ITUNES)
ET.register_namespace("atom", ATOM)


def it(tag: str) -> str:
    return f"{{{ITUNES}}}{tag}"


def hms(seconds: float) -> str:
    s = int(round(seconds))
    return f"{s // 3600:02d}:{(s % 3600) // 60:02d}:{s % 60:02d}"


def mmss(seconds: float) -> str:
    s = int(round(seconds))
    return f"{s // 60}:{s % 60:02d}"


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--manifest", type=Path, default=Path("build/manifest.json"))
    ap.add_argument("--out", type=Path, default=Path("build/feed.xml"))
    ap.add_argument("--base-url", default="https://archive.org/download/REPLACE-ME")
    ap.add_argument("--site-url", default="https://REPLACE-ME.github.io/mormon-cantonese/")
    ap.add_argument("--email", default="REPLACE-ME@example.com")
    ap.add_argument("--artwork", default="")
    ap.add_argument("--audio-dir", type=Path, default=Path("build/audio"))
    ap.add_argument("--start-date", default="2026-11-10", help="pubDate of item 0001")
    args = ap.parse_args()

    if not args.manifest.exists():
        print(f"FATAL: manifest not found: {args.manifest}", file=sys.stderr)
        return 2

    manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
    items = manifest["items"]
    counts = manifest["counts"]

    base = args.base_url.rstrip("/")
    site = args.site_url.rstrip("/") + "/"
    artwork = args.artwork or site + "artwork.jpg"
    start = datetime.fromisoformat(args.start_date).replace(tzinfo=timezone.utc)

    rss = ET.Element("rss", {"version": "2.0"})
    channel = ET.SubElement(rss, "channel")

    def sub(parent, tag, text=None, **attrs):
        el = ET.SubElement(parent, tag, attrs)
        if text is not None:
            el.text = text
        return el

    sub(channel, "title", "摩爾門經（廣東話）")
    sub(channel, "link", site)
    sub(channel, "language", "zh-hk")
    sub(channel, "description",
        "摩爾門經廣東話錄音全書，共 245 段：六段前言與十五部書共二百三十九章。"
        "錄音：Celia Chi Mei Keung。免費收聽，不作販售。")
    sub(channel, "lastBuildDate", format_datetime(datetime.now(timezone.utc)))
    sub(channel, "generator", "tools/build_feed.py")
    sub(channel, "docs", "https://www.rssboard.org/rss-specification")
    sub(channel, it("author"), manifest.get("author", "Celia Chi Mei Keung"))
    sub(channel, it("type"), "serial")
    sub(channel, it("explicit"), "false")
    owner = sub(channel, it("owner"))
    sub(owner, it("name"), manifest.get("author", "Celia Chi Mei Keung"))
    sub(owner, it("email"), args.email)
    sub(channel, it("image"), href=artwork)
    img = sub(channel, "image")
    sub(img, "url", artwork)
    sub(img, "title", "摩爾門經（廣東話）")
    sub(img, "link", site)
    cat = ET.SubElement(channel, it("category"), {"text": "Religion & Spirituality"})
    ET.SubElement(cat, it("category"), {"text": "Christianity"})
    sub(channel, f"{{{ATOM}}}link", None, rel="self", type="application/rss+xml",
        href=site + "feed.xml")

    missing_audio = 0
    total_bytes = 0
    for item in items:
        idx = item["index"]
        stem = Path(item["file"]).stem
        filename = stem + ".m4a"
        url = (
            item.get("audioUrl")
            or f"{base}/{quote(filename)}"
        )
        enc_path = args.audio_dir / filename
        if enc_path.exists():
            length = enc_path.stat().st_size
        else:
            missing_audio += 1
            length = int(item["durationSec"] * 48000 / 8)
        total_bytes += length

        # Item 0001 is newest; descending by index keeps reading order in newest-first lists.
        pub = start - timedelta(days=idx - 1)

        node = ET.SubElement(channel, "item")
        sub(node, "title", item["displayTitle"])
        sub(node, it("title"), item["displayTitle"])
        sub(node, "link", site)
        sub(node, "guid", f"kcmcelia-mormon-cantonese-{idx:04d}", isPermaLink="false")
        sub(node, "pubDate", format_datetime(pub))
        sub(node, "description",
            f"{item['displayTitle']}（{mmss(item['durationSec'])}）")
        sub(node, it("duration"), hms(item["durationSec"]))
        sub(node, it("episode"), str(idx))
        sub(node, it("episodeType"), "full")
        sub(node, "enclosure", None, url=url, length=str(length),
            type="audio/mp4")

    out = args.out
    out.parent.mkdir(parents=True, exist_ok=True)
    tree = ET.ElementTree(rss)
    ET.indent(tree, space="  ")
    tree.write(out, encoding="utf-8", xml_declaration=True)
    out.write_bytes(out.read_bytes().replace(b"?>", b"?>\n", 1))

    # ---- report ---------------------------------------------------------------
    n = len(items)
    print(f"items:        {n}  (front matter {counts['frontmatter']}, chapters {counts['chapters']})")
    print(f"total time:   {counts['durationSec'] / 3600:.2f} h")
    print(f"enclosures:   {total_bytes / 1e6:.0f} MB"
          f"{'  (estimated, audio not encoded yet)' if missing_audio else '  (actual sizes)'}")
    print(f"item 0001:    {format_datetime(start)}  -> newest, reads first")
    print(f"item {n:04d}:    {format_datetime(start - timedelta(days=n - 1))}  -> last")
    print(f"base url:     {base}")
    if "REPLACE-ME" in base or "REPLACE-ME" in site:
        print("\nWARNING: placeholder URLs still present. Re-run with --base-url and --site-url")
        print("         once the audio is on the Internet Archive and the feed is on GitHub Pages.")
    print(f"\nwrote {out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

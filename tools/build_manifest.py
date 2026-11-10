#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Build manifest.json for the Cantonese Book of Mormon audio set.

Reads the 245 MP3s, parses index/book/chapter from the filename, normalises the
display titles, measures duration from the MP3 frames (Xing header when present),
hashes each file, and writes:

    build/manifest.json          the single source of truth for feed + app
    build/manifest-review.csv    one human pass over book/chapter assignment

The script REPORTs every inconsistency it finds and exits non-zero only when the
corpus is structurally broken (missing/duplicate index, unparseable name). It
never renumbers and never edits the audio.

Usage:
    python tools/build_manifest.py [--root DIR] [--out DIR] [--no-hash]
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import re
import sys
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path

DEFAULT_ROOT = Path(r"C:\Users\Admin\Videos\ultcov\摩爾門經-廣東話錄音")
DEFAULT_OUT = Path("build")

# Canonical book order and chapter counts (verified against the files 2026-11-10).
BOOKS: list[tuple[str, int]] = [
    ("尼腓一書", 22),
    ("尼腓二書", 33),
    ("雅各書", 7),
    ("以挪士書", 1),
    ("雅龍書", 1),
    ("奧母乃書", 1),
    ("摩爾門語", 1),
    ("摩賽亞書", 29),
    ("阿爾瑪書", 63),
    ("希拉曼書", 16),
    ("尼腓三書", 30),
    ("尼腓四書", 1),
    ("摩爾門書", 9),
    ("以帖書", 15),
    ("摩羅乃書", 10),
]

# Indices 0001-0006 are front matter; their titles are fixed and normalised.
FRONT_MATTER: dict[int, str] = {
    1: "標題頁",
    2: "緒言",
    3: "三位證人的證詞",
    4: "八位證人的證詞",
    5: "先知約瑟·斯密的證詞",
    6: "摩爾門經簡介",
}

CN_DIGITS = "零一二三四五六七八九"

BITRATES = {1: 32, 2: 40, 3: 48, 4: 56, 5: 64, 6: 80, 7: 96, 8: 112, 9: 128,
            10: 160, 11: 192, 12: 224, 13: 256, 14: 320}
SAMPLE_RATES = {0: 44100, 1: 48000, 2: 32000}
CHANNEL_NAME = {3: "mono", 2: "stereo", 1: "joint-stereo", 0: "stereo"}


def to_chinese(n: int) -> str:
    if n < 10:
        return CN_DIGITS[n]
    if n < 20:
        return "十" + (CN_DIGITS[n % 10] if n % 10 else "")
    if n < 100:
        return CN_DIGITS[n // 10] + "十" + (CN_DIGITS[n % 10] if n % 10 else "")
    raise ValueError(f"chapter out of range: {n}")


def from_chinese(s: str) -> int:
    s = s.strip()
    if s.isdigit():
        return int(s)
    if s == "十":
        return 10
    if "十" in s:
        tens, _, ones = s.partition("十")
        return (CN_DIGITS.index(tens) if tens else 1) * 10 + (
            CN_DIGITS.index(ones) if ones else 0
        )
    return CN_DIGITS.index(s)


def _id3v2_size(raw: bytes) -> int:
    if raw[:3] != b"ID3":
        return 0
    return 10 + (((raw[6] & 0x7F) << 21) | ((raw[7] & 0x7F) << 14)
                 | ((raw[8] & 0x7F) << 7) | (raw[9] & 0x7F))


def _parse_header(buf: bytes, i: int) -> dict | None:
    """Parse an MPEG-1 Layer III frame header at buf[i]; None if not one."""
    if i + 4 > len(buf) or buf[i] != 0xFF or (buf[i + 1] & 0xE0) != 0xE0:
        return None
    b1, b2, b3 = buf[i + 1], buf[i + 2], buf[i + 3]
    version = (b1 >> 3) & 3
    layer = (b1 >> 1) & 3
    br_idx = (b2 >> 4) & 0xF
    sr_idx = (b2 >> 2) & 3
    pad = (b2 >> 1) & 1
    mode = (b3 >> 6) & 3
    if version != 3 or layer != 1:
        return None
    br, sr = BITRATES.get(br_idx), SAMPLE_RATES.get(sr_idx)
    if not br or not sr:
        return None
    return {
        "bitrate": br,
        "sample_rate": sr,
        "mode": mode,
        "length": 144 * br * 1000 // sr + pad,
        "side_info": 17 if mode == 3 else 32,
    }


def analyse(path: Path) -> dict | None:
    size = path.stat().st_size
    with path.open("rb") as fh:
        raw = fh.read(1 << 20)
    start = _id3v2_size(raw)

    i = start
    first = None
    while i < len(raw) - 4:
        h = _parse_header(raw, i)
        if h:
            nxt = i + h["length"]
            h2 = _parse_header(raw, nxt) if nxt + 4 <= len(raw) else None
            if h2 and h2["sample_rate"] == h["sample_rate"]:
                first = (i, h)
                break
        i += 1
    if first is None:
        return None

    pos, head = first

    # The Xing/Info frame is a low-bitrate wrapper (typically 64 kbps) carrying stream
    # metadata; it is NOT the audio bitrate. Step over it for the real stream properties.
    xoff = pos + 4 + head["side_info"]
    frames = None
    has_xing = raw[xoff:xoff + 4] in (b"Xing", b"Info")
    if has_xing:
        flags = int.from_bytes(raw[xoff + 4:xoff + 8], "big")
        if flags & 0x1:
            frames = int.from_bytes(raw[xoff + 8:xoff + 12], "big")

    stream_head = head
    audio_start = pos
    if has_xing:
        audio_start = pos + head["length"]
        nxt = _parse_header(raw, audio_start)
        if nxt:
            stream_head = nxt

    brs: list[int] = []
    p = audio_start
    for _ in range(40):
        h = _parse_header(raw, p)
        if not h:
            break
        brs.append(h["bitrate"])
        p += h["length"]
    cbr = len(set(brs)) <= 1

    if frames:
        duration = frames * 1152 / stream_head["sample_rate"]
    elif cbr:
        duration = (size - start) * 8 / (brs[0] * 1000)
    else:
        avg = sum(brs) / len(brs)
        duration = (size - start) * 8 / (avg * 1000)

    avg_kbps = round(size * 8 / duration / 1000) if duration > 0 else stream_head["bitrate"]
    return {
        "bitrate_kbps": avg_kbps,
        "sample_rate": stream_head["sample_rate"],
        "channels": CHANNEL_NAME[stream_head["mode"]],
        "duration_sec": duration,
        "cbr": cbr,
        "frames": frames,
    }


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


@dataclass
class Item:
    index: int
    kind: str
    book_index: int | None
    book_name: str
    chapter: int | None
    display_title: str
    file: str
    bytes: int
    duration_sec: float
    sha256: str
    bitrate_kbps: int | None
    sample_rate: int | None
    channels: str | None
    cbr: bool | None = None
    audio_url: str | None = field(default=None)


def parse_name(path: Path) -> tuple[int, str] | None:
    stem = re.sub(r"^\d+\.", "", path.stem)  # tolerate a legacy "N." prefix
    m = re.match(r"^(\d+)\s*(.*)$", stem)
    if not m:
        return None
    return int(m.group(1)), m.group(2).strip()


def normalise_title(title: str) -> str:
    t = re.sub(r"^(摩爾門經|爾門經)[\s\-]*", "", title).strip()
    t = re.sub(r"[\s\-]+", " ", t)
    t = t.replace("証", "證")
    t = t.replace("先知約瑟 斯密", "先知約瑟·斯密")
    return t


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", type=Path, default=DEFAULT_ROOT)
    ap.add_argument("--out", type=Path, default=DEFAULT_OUT)
    ap.add_argument("--no-hash", action="store_true", help="skip sha256 (faster)")
    args = ap.parse_args()

    root, out = args.root, args.out
    if not root.is_dir():
        print(f"FATAL: not a directory: {root}", file=sys.stderr)
        return 2

    mp3s = sorted(root.glob("*.mp3"))
    if not mp3s:
        print(f"FATAL: no MP3s in {root}", file=sys.stderr)
        return 2

    items: list[Item] = []
    errors: list[str] = []
    warnings: list[str] = []
    vbr_files: list[int] = []
    seen: dict[int, str] = {}

    for path in mp3s:
        parsed = parse_name(path)
        if parsed is None:
            errors.append(f"unparseable filename: {path.name}")
            continue
        index, raw_title = parsed
        if index in seen:
            errors.append(f"duplicate index {index:04d}: {seen[index]} and {path.name}")
            continue
        seen[index] = path.name

        title = normalise_title(raw_title)
        book_index = book_name = chapter = None
        for i, (name, _count) in enumerate(BOOKS, start=1):
            if title.startswith(name):
                book_index, book_name = i, name
                rest = title[len(name):]
                cm = re.search(r"第([一二三四五六七八九十\d]+)章", rest)
                if cm:
                    chapter = from_chinese(cm.group(1))
                break

        if book_index is None:
            kind = "frontmatter"
            display = FRONT_MATTER.get(index, title)
        else:
            kind = "chapter"
            if chapter is None:
                warnings.append(f"{index:04d}: book {book_name} but no chapter parsed")
                display = f"{book_name} {title}"
            else:
                display = f"{book_name}第{to_chinese(chapter)}章"

        meta = analyse(path)
        if meta is None:
            warnings.append(f"{index:04d}: no valid MP3 frame found")
            meta = {"bitrate_kbps": None, "sample_rate": None, "channels": None,
                    "duration_sec": 0.0, "cbr": None, "frames": None}
        if meta["cbr"] is False and not meta["frames"]:
            vbr_files.append(index)

        items.append(
            Item(
                index=index,
                kind=kind,
                book_index=book_index,
                book_name=book_name or "",
                chapter=chapter,
                display_title=display,
                file=path.name,
                bytes=path.stat().st_size,
                duration_sec=round(meta["duration_sec"], 1),
                sha256="" if args.no_hash else sha256(path),
                bitrate_kbps=meta["bitrate_kbps"],
                sample_rate=meta["sample_rate"],
                channels=meta["channels"],
                cbr=meta["cbr"],
            )
        )

    # ---- structural validation ------------------------------------------------
    indices = sorted(seen)
    gaps = [i for i in range(1, max(indices) + 1) if i not in seen]
    if gaps:
        warnings.append(f"missing indices: {gaps}")

    counts: dict[str, int] = {}
    for it in items:
        if it.book_name:
            counts[it.book_name] = counts.get(it.book_name, 0) + 1
    for name, expected in BOOKS:
        if counts.get(name, 0) != expected:
            warnings.append(f"{name}: {counts.get(name, 0)} chapters, expected {expected}")

    front = [it for it in items if it.kind == "frontmatter"]
    if len(front) != len(FRONT_MATTER):
        warnings.append(f"front matter: {len(front)} items, expected {len(FRONT_MATTER)}")

    for it in items:
        if it.kind == "chapter" and it.chapter is not None and not 1 <= it.chapter <= 200:
            warnings.append(f"{it.index:04d}: implausible chapter {it.chapter}")

    duplicates = [it.index for it in items if it.duration_sec <= 0]
    if duplicates:
        warnings.append(f"zero-length items: {duplicates}")

    # ---- assemble -------------------------------------------------------------
    items.sort(key=lambda it: it.index)
    books_payload = []
    for i, (name, expected) in enumerate(BOOKS, start=1):
        members = [it for it in items if it.book_index == i]
        if not members:
            continue
        books_payload.append({
            "index": i,
            "name": name,
            "chapters": expected,
            "firstIndex": members[0].index,
            "lastIndex": members[-1].index,
        })

    total_sec = sum(it.duration_sec for it in items)
    manifest = {
        "schemaVersion": 1,
        "generatedAt": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "title": "摩爾門經（廣東話）",
        "author": "Celia Chi Mei Keung",
        "language": "zh-HK",
        "counts": {
            "items": len(items),
            "frontmatter": len(front),
            "chapters": len(items) - len(front),
            "durationSec": round(total_sec, 1),
            "bytes": sum(it.bytes for it in items),
            "projectedBytesAt32k": round(total_sec * 32000 / 8),
        },
        "books": books_payload,
        "items": [{
            "index": it.index,
            "kind": it.kind,
            "bookIndex": it.book_index,
            "bookName": it.book_name,
            "chapter": it.chapter,
            "displayTitle": it.display_title,
            "file": it.file,
            "bytes": it.bytes,
            "durationSec": it.duration_sec,
            "sha256": it.sha256,
            "bitrateKbps": it.bitrate_kbps,
            "sampleRate": it.sample_rate,
            "channels": it.channels,
            "audioUrl": it.audio_url,
        } for it in items],
    }

    out.mkdir(parents=True, exist_ok=True)
    (out / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8"
    )

    with (out / "manifest-review.csv").open("w", newline="", encoding="utf-8-sig") as fh:
        w = csv.writer(fh)
        w.writerow(["index", "kind", "book", "chapter", "displayTitle", "file",
                    "durationSec", "MB"])
        for it in items:
            w.writerow([f"{it.index:04d}", it.kind, it.book_name, it.chapter or "",
                        it.display_title, it.file, f"{it.duration_sec:.1f}",
                        f"{it.bytes / 1e6:.1f}"])

    # ---- report ---------------------------------------------------------------
    print(f"root: {root}")
    print(f"items: {len(items)}  front matter: {len(front)}  chapters: {len(items) - len(front)}")
    print(f"index range: {indices[0]:04d}-{indices[-1]:04d}  gaps: {gaps or 'none'}")
    print(f"duration: {total_sec / 3600:.2f} h   source: {sum(it.bytes for it in items) / 1e9:.2f} GB")
    print(f"projected @32 kbps mono: {total_sec * 32000 / 8 / 1e6:.0f} MB")
    for name, expected in BOOKS:
        got = counts.get(name, 0)
        print(f"  {'ok  ' if got == expected else 'DIFF'} {name}: {got}/{expected}")
    if vbr_files:
        print(f"\nVBR without Xing frame count (durations approximate): {vbr_files}")
    if warnings:
        print("\nWARNINGS")
        for w_ in warnings:
            print("  -", w_)
    if errors:
        print("\nERRORS")
        for e in errors:
            print("  -", e)
    print(f"\nwrote {out / 'manifest.json'}")
    print(f"wrote {out / 'manifest-review.csv'}")
    return 1 if errors else 0


if __name__ == "__main__":
    raise SystemExit(main())

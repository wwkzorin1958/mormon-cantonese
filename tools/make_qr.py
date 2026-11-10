#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Generate the sharing QR code for the landing page.

Produces two files:
  artwork/qr-card.png   1600x2000 print/share card (title, QR, URL)
  docs/qr.png           plain QR for the landing page

Usage:
    python tools/make_qr.py [--url URL]
"""

from __future__ import annotations

import argparse
from pathlib import Path

import segno
from PIL import Image, ImageDraw, ImageFont

CARD = Path("artwork/qr-card.png")
WEB = Path("docs/qr.png")
RAW = Path("build/qr-raw.png")

CREAM = "#FDF8F0"
NAVY = "#12233B"
GOLD = "#8A6A24"
MUTED = "#5A6472"

FONT_BOLD = "C:/Windows/Fonts/msjhbd.ttc"
FONT_REGULAR = "C:/Windows/Fonts/msjh.ttc"


def load(path: str, size: int) -> ImageFont.FreeTypeFont:
    try:
        return ImageFont.truetype(path, size, index=0)
    except OSError:
        return ImageFont.load_default()


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--url", default="https://wwkzorin1958.github.io/mormon-cantonese/")
    args = ap.parse_args()
    url = args.url

    RAW.parent.mkdir(parents=True, exist_ok=True)

    # Web version: quiet zone included, so it scans from a phone screen.
    web_qr = segno.make(url, error="m")
    web_qr.save(WEB, scale=14, border=4, dark=NAVY, light=CREAM)
    web_qr.save(RAW, scale=30, border=4, dark=NAVY, light=CREAM)

    # Card version.
    card_w, card_h = 1600, 1700
    card = Image.new("RGB", (card_w, card_h), CREAM)
    draw = ImageDraw.Draw(card)

    title_font = load(FONT_BOLD, 104)
    sub_font = load(FONT_REGULAR, 48)
    url_font = load(FONT_REGULAR, 40)
    hint_font = load(FONT_REGULAR, 38)

    title = "摩爾門經（廣東話）"
    subtitle = "245 段錄音 · 免費收聽"
    hint = "掃描後可直接收聽，或下載手機應用程式"

    def center(text: str, font, y: int, fill: str) -> None:
        w = draw.textlength(text, font=font)
        draw.text(((card_w - w) / 2, y), text, font=font, fill=fill)

    center(title, title_font, 120, NAVY)
    center(subtitle, sub_font, 260, GOLD)

    qr_img = Image.open(RAW).convert("RGB")
    qr_size = 1000
    qr_img = qr_img.resize((qr_size, qr_size), Image.NEAREST)
    card.paste(qr_img, ((card_w - qr_size) // 2, 400))

    center(url, url_font, 1500, MUTED)
    center(hint, hint_font, 1580, MUTED)

    # thin gold rule under the QR
    draw.line([(560, 1470), (1040, 1470)], fill="#C9A25A", width=3)

    card.save(CARD, "PNG", optimize=True)
    print(f"wrote {CARD} ({CARD.stat().st_size / 1024:.0f} KB)")
    print(f"wrote {WEB} ({WEB.stat().st_size / 1024:.0f} KB)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

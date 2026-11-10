#!/usr/bin/env bash
# Encode the 245 source MP3s for distribution.
#
#   source: 128 kbps joint-stereo, 48 kHz  (~1.87 GB, 32.3 h)
#   output: 48 kbps AAC-LC, mono, 48 kHz   (~700 MB)
#
# Each file is loudness-normalised to -16 LUFS with a -1.5 dBTP true-peak limit and has
# leading silence trimmed, so 245 recordings made over months sit at a consistent volume.
#
# Requires ffmpeg 6+ on PATH:
#   winget install Gyan.FFmpeg      (Windows)
#   brew install ffmpeg             (macOS)
#   sudo apt install ffmpeg         (Debian/Ubuntu)
#
# Usage:
#   bash tools/encode.sh [SRC_DIR] [OUT_DIR] [BITRATE]
#   bash tools/encode.sh --check                 # verify toolchain only
#
# Re-runs are incremental: existing .m4a files are skipped.

set -euo pipefail

SRC="${1:-C:/Users/Admin/Videos/ultcov/摩爾門經-廣東話錄音}"
OUT="${2:-build/audio}"
BITRATE="${3:-48k}"

if [ "${1:-}" = "--check" ]; then
  SRC="C:/Users/Admin/Videos/ultcov/摩爾門經-廣東話錄音"
  OUT="build/audio"
  BITRATE="48k"
fi

if ! command -v ffmpeg >/dev/null 2>&1; then
  echo "ERROR: ffmpeg not found on PATH." >&2
  echo "  Windows: winget install Gyan.FFmpeg" >&2
  exit 1
fi
if ! command -v ffprobe >/dev/null 2>&1; then
  echo "ERROR: ffprobe not found on PATH (ships with ffmpeg)." >&2
  exit 1
fi

echo "ffmpeg:  $(ffmpeg -version | head -n 1)"
echo "source:  $SRC"
echo "output:  $OUT"
echo "bitrate: $BITRATE mono AAC"

if [ "${1:-}" = "--check" ]; then
  echo "toolchain OK"
  exit 0
fi

if [ ! -d "$SRC" ]; then
  echo "ERROR: source directory not found: $SRC" >&2
  exit 1
fi

mkdir -p "$OUT"
shopt -s nullglob
files=("$SRC"/*.mp3)
total=${#files[@]}
if [ "$total" -eq 0 ]; then
  echo "ERROR: no MP3s in $SRC" >&2
  exit 1
fi
echo "files:   $total"
echo

# Leading-silence trim, then loudness normalisation. Tail trimming is deliberately skipped:
# areverse buffers the whole file in memory, which is wasteful for 17-minute chapters and
# matters little when the player auto-advances.
AF="silenceremove=start_periods=1:start_duration=0:start_threshold=-50dB:detection=peak,loudnorm=I=-16:TP=-1.5:LRA=11"

i=0
skipped=0
for f in "${files[@]}"; do
  i=$((i + 1))
  base=$(basename "$f" .mp3)
  dst="$OUT/$base.m4a"
  if [ -f "$dst" ]; then
    skipped=$((skipped + 1))
    continue
  fi
  printf '[%3d/%3d] %s\n' "$i" "$total" "$base"
  # -map 0:a:0 -vn: every source MP3 also carries a PNG cover image in stream 1;
  # without this, ffmpeg tries to transcode that picture as h264 and fails.
  # -map_metadata -1: drop inherited tags, then set the three we actually want.
  ffmpeg -nostdin -hide_banner -loglevel error -y \
    -i "$f" -map 0:a:0 -vn -map_metadata -1 \
    -af "$AF" \
    -ac 1 -ar 48000 -c:a aac -b:a "$BITRATE" -movflags +faststart -f mp4 \
    -metadata title="$base" -metadata artist="Celia Chi Mei Keung" \
    -metadata album="摩爾門經（廣東話）" \
    "$dst.part"
  # rename only on success, so an interrupted run never leaves a truncated .m4a
  # that the next run would mistake for finished work
  mv "$dst.part" "$dst"
done

echo
echo "skipped (already encoded): $skipped"
echo "encoded:                   $((total - skipped))"

# Report actual totals and compare against the manifest, if one exists.
enc_bytes=$(find "$OUT" -name '*.m4a' -printf '%s\n' 2>/dev/null | awk '{s+=$1} END {print s+0}')
enc_count=$(find "$OUT" -name '*.m4a' | wc -l | tr -d ' ')
printf 'output: %s files, %.0f MB\n' "$enc_count" "$(echo "$enc_bytes" | awk '{printf "%.0f", $1/1e6}')"

if [ -f build/manifest.json ] && command -v python >/dev/null 2>&1; then
  python - <<'PY'
import json, subprocess, sys
from pathlib import Path
m = json.load(open("build/manifest.json", encoding="utf-8"))
missing = [i for i in m["items"] if not (Path("build/audio") / (Path(i["file"]).stem + ".m4a")).exists()]
print(f"manifest expects {len(m['items'])} files; missing: {len(missing)}")
for i in missing[:5]:
    print("  missing:", i["file"])
PY
fi

# Prompt: Cantonese Book of Mormon — offline listening app + podcast feed

## Situation (read this before designing anything)

Celia Chi Mei Keung, a retired Cantonese speaker, spent months recording the entire Book
of Mormon aloud in Cantonese. The recordings are hers. The text is public domain. Her
stated purpose is that people who need it can hear it. She has 245 MP3s (1.87 GB, 128 kbps joint-stereo, 48 kHz, 32.3 hours)
on a Windows PC and no technical background.

Her listeners are older, mostly non-technical, mixed Android and iPhone. They will not pay
for YouTube Premium, will not make accounts, and will not read instructions longer than one
page.

She has agreed to publish the recordings publicly and permanently. Treat that as settled,
and say so plainly in the README: publishing is the point, and taking the feed down later
does not recall copies that have already been downloaded.

Deliver **two things**, in this order:
- **A. A podcast feed** — reaches everyone, including iPhone, using apps they already have.
- **B. An Android APK** — the one-tap experience for people who will not use a podcast app.

A must ship first. It validates demand, covers iOS completely, and keeps working if B ever
breaks.

## What exists already (do not re-create it)

245 files, index first, then the title:

```
0001 摩爾門經 標題頁.mp3
0002 摩爾門經 緒言.mp3
0003 摩爾門經 三位證人的證詞.mp3
0004 摩爾門經 八位證人的證詞.mp3
0005 摩爾門經 先知約瑟 斯密的証詞.mp3
0006 摩爾門經 摩爾門經簡介.mp3
0007 摩爾門經 尼腓一書第一章.mp3
...
0029 摩爾門經 尼腓二書第一章.mp3
...
00104摩爾門經 阿爾瑪書第三章.mp3
00114 摩爾門經-阿爾瑪書第十三章.mp3
0094 爾門經 摩賽亞書第二十二章.mp3
...
00225 摩爾門經-以帖書第五章.mp3
...
0245 摩爾門經 摩羅乃書第十章.mp3  (last file)
```

Structure: leading digits are the index, everything after is the title. Indices run
**0001–0245 continuously with no gaps** — zero-padded to four, growing to five from 00100.
6 front matter + 239 chapters = 245, ending on the last chapter of the Book of Mormon. All
fifteen books match their canonical chapter counts.

Parsing rule: split on `^(\d+)\s*(.*)$`. The title always begins with 摩 or 爾, never a
digit, so the split is unambiguous and the variable index width is harmless.

**Known defects to handle, not fix:**
- 10 filenames are missing the leading 摩: they read `爾門經 摩賽亞書第二十二章` and so on.
- Separators are inconsistent: `摩爾門經 阿爾瑪書`, `摩爾門經-阿爾瑪書`, `摩爾門經 摩賽亞書`
  with a double space, and some with no space at all after the index.
- `0005` uses 証詞 rather than 證詞, and a space rather than ·. Display titles must be
  normalized; filenames must not be renamed again.
- `00225` (以帖書第五章) is the only file at 224 kbps / 44.1 kHz — it was exported from
  YouTube rather than recorded alongside the rest. Check its loudness and tone against its
  neighbours during QC; it may need extra gain correction to sit naturally between 00224 and
  00226.
- **Do not trust the first MP3 frame when measuring these files.** Every recording carries a
  Xing/Info header frame encoded at 64 kbps; the audio itself is 128 kbps CBR. Reading the
  first frame doubles the apparent bitrate error and halves the apparent duration. Measure
  with the frame count in the Xing header, or by walking frames.
- 23 `.srt` transcript files exist, inconsistently. Out of scope for v1.

Chapter counts per book (confirmed against the files):

| # | Book | Chapters |
|---|---|---|
| 1 | 尼腓一書 | 22 |
| 2 | 尼腓二書 | 33 |
| 3 | 雅各書 | 7 |
| 4 | 以挪士書 | 1 |
| 5 | 雅龍書 | 1 |
| 6 | 奧母乃書 | 1 |
| 7 | 摩爾門語 | 1 |
| 8 | 摩賽亞書 | 29 |
| 9 | 阿爾瑪書 | 63 |
| 10 | 希拉曼書 | 16 |
| 11 | 尼腓三書 | 30 |
| 12 | 尼腓四書 | 1 |
| 13 | 摩爾門書 | 9 |
| 14 | 以帖書 | 15 |
| 15 | 摩羅乃書 | 10 |

**The global index is not arithmetic.** 尼腓一書 starts at 0007, not 0001; 尼腓二書第一章
is 0029, not 0023. Never compute a position from a chapter number. Everything derives from
the manifest, which derives from the filenames.

## Hard constraints

- Do not scrape, rip, or download anything from YouTube or any streaming service. No
  yt-dlp, no stream-URL extraction. The audio already exists locally.
- Audio is 224 kbps source; it is the archive master. Never modify or delete the originals.
- Plays immediately when online; must also play with no network once downloaded, with the
  screen off, for hours.
- No accounts, no ads, no analytics, no telemetry, no third-party SDKs, no sync.
- The only network use is: streaming audio, downloading audio, and one startup `latest.json`
  check.
- UI language: 繁體中文 only. Not English-first with translations.
- Everything must be reproducible by someone non-technical: pinned versions, CI build, no
  local toolchain required.

## Phase 0 — Manifest and audio preparation

**0.1 Manifest generator** (`tools/build_manifest.py` or `.kt`, run once):
- Parse filenames into `index`, `kind` (`frontmatter` / `chapter` / `backmatter`), `book`,
  `chapter`, `displayTitle`.
- Parse Chinese numerals 一 through 六十三, including 十-composition (二十三 = 23,
  三十 = 30, 六十三 = 63).
- Emit `manifest.json`: index, kind, bookIndex, bookName, chapter, displayTitle, file,
  durationSec, bytes, sha256, audioUrl (filled later).
- **Validate and report; do not hard-fail.** The chapter table above is derived from two
  filenames and a screenshot. It is a hypothesis until checked against the real files:
  confirm it once, by hand, before writing any code. Afterwards the validator must *report*
  diffs — index gaps, duplicate indices, unexpected chapter counts, unparseable names,
  zero-byte files — and let a human accept or correct them. Never reject a valid corpus
  because a guessed number disagreed with reality.
- Output a review CSV for one human pass, to confirm every book and chapter assignment.

**0.2 Encode for distribution** (`tools/encode.sh`):
- Source is 128 kbps joint-stereo, 48 kHz — about 64 kbps per channel. Encode at **48 kbps
  AAC-LC, mono, 48 kHz, `.m4a`**: close to source quality for speech, and ~700 MB for the
  whole 32.3 hours.
- Verify the source is dual-mono before downmixing (compare L/R channels on one file); if the
  channels differ, keep stereo for that check before deciding.
- Loudness normalization to −16 LUFS, true peak limit −1.5 dBTP (single pass; 245 files
  recorded over months will otherwise have wildly inconsistent levels).
- Trim leading/trailing silence beyond 0.5 s.
- Total output ≈ 725 MB (measured). Report per-file and total size.
- Loudness is the target that holds: every file lands at −16 LUFS ±1 dB. True peak is −1.5
  dBTP *before* the AAC encoder, but AAC-LC at 48 kbps overshoots by ~1.7 dB, so decoded
  peaks sit near 0 dBFS. That is expected; do not chase it by lowering bitrate or gain.
- Durations come from the manifest alone; the feed and the app read the same file, so they
  cannot drift.

## Phase A — Podcast feed

- `feed.xml` on **GitHub Pages** (stable URL, correct content type, versioned with the
  repo). Audio on the **Internet Archive**: free, permanent, and it serves per-file URLs with
  byte ranges, which podcast enclosures need. It requires a free account and the item is
  public by design.
- Before generating the feed, verify the upload against the manifest
  (`tools/verify_upload.py`): every expected filename present, no truncation, no renaming by
  the Archive. A feed built on an unverified upload is a feed full of dead links.
- 245 items, one per recording. `itunes:type` = serial, `itunes:episode` = the global index
  (0001–0245). Do not use `itunes:season`: the six front-matter items belong to no book, so
  season numbering has no consistent meaning.
- `guid` is `kcmcelia-mormon-cantonese-NNNN`, `isPermaLink="false"`, and must never change —
  it is what stops clients re-downloading or duplicating episodes when the feed is rebuilt.
- **Ordering is an experiment, not a solved problem.** Podcast clients list newest-first, so
  try `pubDate` descending by canonical index — item 0001 gets the newest date — and expect
  the feed to read top-to-bottom in reading order. Do not announce anything until this is
  verified in Apple Podcasts, Spotify, Pocket Casts, and AntennaPod. If any of them
  scrambles the sequence, fall back to one feed per book. Record the result in the README.
- Metadata: title 摩爾門經（廣東話）, author Celia Chi Mei Keung, language `zh-HK`,
  category Religion & Spirituality → Christianity, explicit no, 3000×3000 artwork
  (generate simple text artwork; do not use Church-owned images).
- Episode description includes book and chapter in Chinese, plus duration.
- `tools/submit.md`: exact steps for Apple Podcasts Connect and Spotify for Podcasters,
  both free, no developer account.
- Test with a validator and with one real client before announcing.

## Phase B — Android app

**B.1 Stack — exactly four dependencies plus the platform:**
Kotlin, Jetpack Compose + Material 3, **Media3** (ExoPlayer + `MediaSessionService`),
**DataStore**, **kotlinx.serialization**.
Deliberately **not** used: Room, Hilt, WorkManager, Coil, OkHttp, Retrofit, Firebase.
Use the system **`DownloadManager`** for content fetching — it already provides queueing,
retry, resume, Wi-Fi-only constraints, and notifications.
minSdk 26, target and compile the latest stable SDK, all versions pinned exactly.

**B.2 Three screens, no tabs:**
1. **Home** — large 繼續聆聽 card (current book, chapter, progress, resume button), then the
   15 books as rows with chapter counts and a small 已下載／未下載 indicator. One gear icon
   for settings.
2. **Book** — chapter grid, 56dp minimum targets, current chapter highlighted, front/back
   matter listed above and below the grid as named rows.
3. **Player** — expandable sheet plus full screen: play/pause, ±30 s chapter skip, next/prev
   chapter, speed, sleep timer, book/chapter title. No shuffle. No search. No queue editor.

**B.3 Playback behavior:**
- Media3 `MediaSessionService`, foreground service typed `mediaPlayback`, declared
  permissions for Android 14+, runtime `POST_NOTIFICATIONS` on 13+.
- Autoplay the next chapter; cross book boundaries without stopping; at the very end, stop.
- Persist position every 5 s and on pause/stop; resume exactly on relaunch, including after
  process death.
- Lock screen, notification, Bluetooth, and headset controls. Audio focus; pause on noisy
  disconnect. Sleep timer (15/30/45/60 min, end of chapter).
- Speed 0.5–2.0 with pitch preservation, default 1.0.

**B.4 Content delivery — stream first, download as the upgrade:**
- Nothing stands between the app opening and the first word. Tapping a chapter plays
  immediately from `audioUrl` when online; downloaded chapters play from local storage and
  never touch the network.
- After the first chapter finishes, offer once, in plain language: 「下載全部，以後唔需要
  網絡」 with the size (~725 MB) and a Wi-Fi recommendation. Also offer per-book download
  from the book screen. Never a 725 MB wall before the first word.
- Download per file, not as a zip, so a listener never needs double the free space. Use the
  system `DownloadManager` into app-specific external storage: it already provides queueing,
  retry, resume, and Wi-Fi-only constraints.
- Space check before starting a download: ~1.0 GB free. Say it in one sentence.
- Streaming: warn once if on mobile data; prefetch the next chapter while one plays; tune
  buffers for slow connections.
- Not downloaded and offline: one sentence plus a 下載此書 button. Never a generic error.
- Verify downloaded files by sha256 from the manifest; on mismatch, re-download that file.
- On startup, fetch `latest.json` once — `{appVersion, manifestHash}`. Newer app version:
  a quiet 有新版本 notice linking to the Release page. Different manifest hash: offer to
  refresh changed files. Offline: fail silently and change nothing.

**B.5 Elderly-first UI:**
- Base font 20sp, respect system font scale to 200% without clipping.
- Contrast checked for the dark and light themes; no thin fonts, no low-contrast grey text.
- All strings in `strings.xml` (zh-rHK default), Traditional Chinese only.
- No animations longer than 200 ms; no gestures required for any function; every action has
  a visible button.
- First-run screen in plain language: what the app does, that it is free, that it works
  right away online, and that downloading makes it work without a connection.

**B.6 Battery killers — treat as a launch blocker:**
- Request `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` on first run, with a plain-language
  explanation before the system dialog.
- Ship a 繁中 autostart guide covering 小米/MIUI, 華為/EMUI, OPPO/ColorOS, vivo, Samsung,
  with screenshots or diagrams, reachable from settings and shown once on install.
- Verify on a real Xiaomi or Huawei device: screen off, 30 minutes, playback continues.

**B.7 Attribution and About screen:**
- 錄音：Celia Chi Mei Keung, with her own dedication text, verbatim.
- Note: text is public domain; recordings are hers; free to listen and share; not for sale.
- Version and build date. No analytics notice needed, because there are none.

## Phase C — Build, signing, handover

- `org.kcmcelia.bookofmormon` package id. App name 摩爾門經（廣東話）.
- GitHub Actions workflow: on push of a `v*` tag, build a **signed** release APK, attach it
  to a GitHub Release, upload the mapping file.
- Keystore generated once, stored in repository secrets, with explicit instructions to also
  keep a physical backup. Losing it means no future updates to installed apps.
- Stable release page URL + generated QR code, for sharing.
- `MAINTAINER.md` in 繁體中文, one page: how to build (push a tag, wait), how to get the
  APK, how to update the feed, who to contact if the build fails.
- No Play Store. No iOS app. Distribution is sideloaded APK plus the podcast feed.

## Tests and acceptance criteria

Unit tests: filename parser and Chinese numerals (一, 十, 十一, 二十, 二十三, 六十三),
manifest validation failures, position persistence, autoplay across a book boundary.

Instrumented: first run → download → airplane mode → playback with screen off.

Ship only when all of these hold:
1. All 245 files parse; indices continuous 1–245 with zero gaps; the 10 filenames missing
   摩 parse correctly; five-digit indices from 00100 do not collide.
2. Encoded set is under 800 MB (measured 724 MB); every file measures −16 LUFS ±1 dB and
   −1.5 dBTP pre-encode.
3. Feed validates, reads in the correct order in all four clients tested, and downloads
   offline.
4. Fresh install → tap a chapter → audio within 10 seconds while online.
5. 下載全部 completes, survives a forced network drop mid-download, and verifies by
   checksum.
6. Airplane mode → screen off for two hours, then overnight, then overnight while charging;
   resumes at the right second after a force-stop.
7. An undownloaded chapter with no network shows a one-sentence message and a 下載此書
   button.
8. Playback continues from the last chapter of one book into the first chapter of the next.
9. Installs on a Xiaomi or Huawei phone and keeps playing after the battery guide is
   followed.
10. `v1.0.0` tag produces a signed APK on the Releases page, installable over itself.
11. No code path contacts YouTube or any streaming service. No network use except streaming,
    downloads, and the single `latest.json` check.

## Non-goals

No iOS app. No Play Store listing. No accounts, sync, playlists, search, shuffle, social
features, analytics, or ads. No support for other books, languages, or channels. The corpus
is complete, so no discovery or browsing of new material. Re-recorded chapters may be picked
up through the manifest hash; genuinely new recordings ship as a new APK and feed update.

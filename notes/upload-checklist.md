# Release status and how to ship an update

## Live

| | |
|---|---|
| Repository | <https://github.com/wwkzorin1958/mormon-cantonese> |
| Site | <https://wwkzorin1958.github.io/mormon-cantonese/> |
| Feed | <https://wwkzorin1958.github.io/mormon-cantonese/feed.xml> — 245 items |
| Audio | <https://archive.org/details/kcmcelia-mormon-cantonese-2026> — 245 files, 724 MB |
| APK | <https://github.com/wwkzorin1958/mormon-cantonese/releases/latest> — v1.0.0, 9.5 MB |
| Artwork | 3000×3000, on the site |

Verified: feed parses with 245 unique enclosures; episode 0007 returns HTTP 200 with 3.24 MB
and HTTP 206 on range requests; the release APK is signed with the keystore whose SHA-256 is
`47a0c386…05ed`.

## Shipping an update

### New or re-recorded audio

```bash
bash tools/encode.sh                       # incremental; skips existing files
python tools/build_manifest.py             # regenerate manifest + review CSV
python tools/upload_audio.py               # resumable; skips files already on the item
python tools/verify_upload.py --identifier kcmcelia-mormon-cantonese-2026 --write
python tools/build_feed.py \
  --base-url https://archive.org/download/kcmcelia-mormon-cantonese-2026 \
  --site-url https://wwkzorin1958.github.io/mormon-cantonese/ \
  --email wwkzorin@gmail.com
cp build/feed.xml docs/feed.xml
cp build/latest.json docs/latest.json
```

### New app build

1. Bump `versionCode` and `versionName` in `android/app/build.gradle.kts`
   (and `--app-version` when generating the feed).
2. Commit, then `git tag vX.Y.Z && git push origin main vX.Y.Z`.
3. CI builds, signs and attaches the APK to the Release.

## Still manual

- **Device test.** Install the Release APK on a phone, play a chapter, lock the screen, leave
  it 30 minutes, force-stop and relaunch.
- **Directory submissions.** <https://podcasters.apple.com> and
  <https://podcasters.spotify.com>, both free, both need the feed URL and an email address.
  Submit only after the device test passes.

## Never change

- Item identifier `kcmcelia-mormon-cantonese-2026`
- Repository name `mormon-cantonese`
- Any audio filename
- Episode GUIDs `kcmcelia-mormon-cantonese-NNNN`
- The signing keystore — back it up, and keep the password somewhere permanent

Change any of those and existing subscribers or installed apps break.

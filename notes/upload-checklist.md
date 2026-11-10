# What is left — audio upload and release

Everything technical is built, committed and published. What remains needs the Internet
Archive account, because the 724 MB of audio is the one thing that is not in Git.

## Already done (do not repeat)

| | |
|---|---|
| Repository | <https://github.com/wwkzorin1958/mormon-cantonese> |
| Site | <https://wwkzorin1958.github.io/mormon-cantonese/> |
| Feed | <https://wwkzorin1958.github.io/mormon-cantonese/feed.xml> — 245 items, live |
| Artwork | 3000×3000, live on the same site |
| `latest.json` | live |
| Signing keystore | created, backed up locally, four repo secrets set |
| CI | `.github/workflows/android-release.yml`, active |

The feed already points at `https://archive.org/download/kcmcelia-mormon-cantonese-2026/…`
for every episode. Those links return 404 until step 2 is finished — that is the only gap.

## 1. Create the Internet Archive account

<https://archive.org/account/signup> — one address, one password, no payment.

Use Celia's own account if possible: the item belongs to whoever uploads it.

## 2. Upload the 245 files

### Background first (recommended — no tooling)

1. Sign in, click **Upload**.
2. Drag the whole `build/audio` folder in — 245 `.m4a` files, 724 MB.
3. Set the identifier to exactly:

   ```
   kcmcelia-mormon-cantonese-2026
   ```

   If it is taken, use a suffix — but then tell me, because the feed and app must be
   regenerated to match.
4. Metadata:

   | Field | Value |
   |---|---|
   | Title | 摩爾門經（廣東話） |
   | Creator | Celia Chi Mei Keung |
   | Language | zh-HK |
   | Mediatype | audio |
   | Collection | opensource_audio |
   | Licence | CC BY-NC-ND 4.0 (or her choice) |

5. Wait for the item page to show **245 files**.

### Command line

```bash
pip install --upgrade internetarchive
ia configure
ia upload kcmcelia-mormon-cantonese-2026 build/audio/*.m4a \
  --metadata='title:摩爾門經（廣東話）' \
  --metadata='creator:Celia Chi Mei Keung' \
  --metadata='language:zh-HK' \
  --metadata='mediatype:audio' \
  --metadata='collection:opensource_audio' \
  --retries 5 --checksum
```

## 3. Verify, then finish the release

```bash
python tools/verify_upload.py --identifier kcmcelia-mormon-cantonese-2026 --write
python tools/build_feed.py \
  --base-url https://archive.org/download/kcmcelia-mormon-cantonese-2026 \
  --site-url https://wwkzorin1958.github.io/mormon-cantonese/ \
  --email wwkzorin@gmail.com
cp build/feed.xml docs/feed.xml
cd android && ./gradlew assembleRelease && cd ..
git add -A && git commit -m "Audio is live on the Internet Archive" && git push
git tag v1.0.0 && git push --tags
```

`verify_upload.py` must print `245/245 present`. If it reports missing or renamed files, stop
and fix that before tagging — the feed addresses each episode by exact filename.

The tag triggers CI, which builds a signed APK and attaches it to the Releases page. That page
is the permanent download link, and
`https://github.com/wwkzorin1958/mormon-cantonese/releases/latest` is what the landing page's
button points at.

## 4. Test before announcing

- [ ] Open the feed in Apple Podcasts (資料庫 → ⋯ → 依 URL 加入節目) and AntennaPod
- [ ] Both show 245 episodes, **標題頁** first and **摩爾乃書第十章** last
- [ ] Episode 0007 downloads and plays with the screen off
- [ ] Install the APK from the Releases page; launch; tap a chapter; hear audio
- [ ] 下載全部 → airplane mode → playback still works

If any client scrambles the episode order, fall back to one feed per book — the generator
supports it with a small change.

## 5. Directories

| Directory | Where | Cost |
|---|---|---|
| Apple Podcasts | <https://podcasters.apple.com> | free, Apple ID only |
| Spotify | <https://podcasters.spotify.com> | free |

Submit only after step 4 passes. A broken feed submitted once is harder to fix than one
nobody has seen.

## Things that can never change

- The item identifier `kcmcelia-mormon-cantonese-2026`
- The repository name `mormon-cantonese`
- Every audio filename
- Episode GUIDs `kcmcelia-mormon-cantonese-NNNN`
- The signing keystore

Change any of those and existing subscribers or installed apps break.

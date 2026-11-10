# Upload checklist — audio to the Internet Archive, feed to GitHub Pages

Everything here happens **after** `bash tools/encode.sh` has produced 245 `.m4a` files in
`build/audio/`. Nothing in this document changes the audio; it publishes it.

Work top to bottom. Each step ends with a check you can actually run.

---

## 0. Before you start

- [ ] `ls build/audio/*.m4a | wc -l` prints **245**
- [ ] `ls build/audio/*.part 2>/dev/null | wc -l` prints **0**
- [ ] You have a free account at <https://archive.org> (Celia's, not a shared one — the item
      belongs to whoever uploads it)
- [ ] You have settled the licence question in step 2

Decide the item identifier now, because it becomes a permanent URL:

```
kcmcelia-mormon-cantonese-2026
```

Rules: lowercase, letters/digits/dashes only, no spaces, must be globally unique. If it is
taken, append a suffix (`-v1`) — never rename it later, because the URL is baked into the
feed and into every copy the app has cached.

Resulting base URL: `https://archive.org/download/kcmcelia-mormon-cantonese-2026`

---

## 1. Upload the 245 files

### Path A — web uploader (recommended for Celia)

1. Sign in at archive.org → **Upload** (top right).
2. **Upload files** → drag the whole `build/audio` folder in.
3. Fill in the metadata from step 2, set identifier `kcmcelia-mormon-cantonese-2026`.
4. Upload. 724 MB takes roughly 10–40 minutes depending on the connection. Leave the tab
   open until it says the item is created.
5. Open `https://archive.org/details/kcmcelia-mormon-cantonese-2026` and confirm the file
   count reads **245**.

### Path B — command line

```bash
pip install --upgrade internetarchive
ia configure                     # asks for your archive.org email + password
                                 # (or S3 keys from https://archive.org/account/s3.php)

ia upload kcmcelia-mormon-cantonese-2026 build/audio/*.m4a \
  --metadata='title:摩爾門經（廣東話）' \
  --metadata='creator:Celia Chi Mei Keung' \
  --metadata='description:摩爾門經廣東話錄音全書：六段前言與十五部書共二百三十九章。' \
  --metadata='language:zh-HK' \
  --metadata='mediatype:audio' \
  --metadata='collection:opensource_audio' \
  --metadata='licenseurl:https://creativecommons.org/licenses/by-nc-nd/4.0/' \
  --retries 5 --checksum
```

Check:

```bash
ia list kcmcelia-mormon-cantonese-2026 | wc -l    # expect 245 (plus IA's own metadata files)
```

**Do not rename files after uploading**, and do not let anything rename them before. The
feed addresses each episode by exact filename.

---

## 2. Metadata and licence

| Field | Value |
|---|---|
| Title | `摩爾門經（廣東話）` |
| Creator | `Celia Chi Mei Keung` |
| Date | recording year(s) |
| Language | `zh-HK` |
| Mediatype | `audio` |
| Collection | `opensource_audio` |
| Rights | see below |

**The licence is a decision, not a default.** She owns the recordings; the text is public
domain. Options:

- **CC BY-NC-ND 4.0** — anyone may share it unaltered, with credit, never for sale. Closest
  to her stated intent ("能帶給有需要的人聆聽到"). Recommended.
- **CC BY 4.0** — also allows altered versions and commercial use. More permissive than she
  probably wants.
- **No licence stated** — all rights reserved by default; still publicly downloadable from
  the Archive, but nobody may legally re-share it.

---

## 3. Verify the upload against the manifest

Do not skip this. It catches renamed files, truncation, and partial uploads before they turn
into dead links in the feed.

```bash
python tools/verify_upload.py --identifier kcmcelia-mormon-cantonese-2026 --write
```

It fetches the item's metadata, compares all 245 expected filenames and byte sizes against
`build/manifest.json`, prints anything missing or mismatched, and — with `--write` — fills in
each item's `audioUrl`.

Expected output ends with `245/245 present, sizes match`.

Then spot-check one file in a browser:

```
https://archive.org/download/kcmcelia-mormon-cantonese-2026/0007%20%E6%91%A9%E7%88%BE%E9%96%80%E7%B6%93%20%E5%B0%BC%E8%85%93%E4%B8%80%E6%9B%B8%E7%AC%AC%E4%B8%80%E7%AB%A0.m4a
```

It should download or play, not 404.

---

## 4. Publish the feed on GitHub Pages

1. Create a public repository, e.g. `mormon-cantonese`.
2. Add:
   - `feed.xml`
   - `artwork.jpg` (copy of `artwork/artwork-3000.jpg`)
3. Settings → Pages → Source: **Deploy from a branch** → `main` / `/ (root)` → Save.
4. Wait a minute, then confirm `https://<user>.github.io/mormon-cantonese/artwork.jpg` loads.

The repository must stay public and the branch must stay named `main`. Renaming the repo
breaks every subscriber's feed URL permanently.

---

## 5. Regenerate the feed with real URLs

```bash
python tools/build_feed.py \
  --base-url https://archive.org/download/kcmcelia-mormon-cantonese-2026 \
  --site-url https://<user>.github.io/mormon-cantonese/ \
  --email <a-real-address@example.com> \
  --artwork https://<user>.github.io/mormon-cantonese/artwork.jpg
```

The run must print `enclosures: 724 MB (actual sizes)` and **no** `REPLACE-ME` warning.
If the warning appears, a URL was not substituted and the feed is not ready.

Check the output:

```bash
grep -c "<item>" build/feed.xml          # 245
grep -c "REPLACE-ME" build/feed.xml      # 0
```

Then commit the regenerated `feed.xml` to the repository.

---

## 6. Test before announcing

- [ ] `https://<user>.github.io/mormon-cantonese/feed.xml` opens as XML in a browser
- [ ] Paste it into <https://podba.se/validate/> — no errors
- [ ] Add it in **Apple Podcasts** (Library → ⋯ → Add a Show by URL) and in **AntennaPod**
- [ ] Both list **245 episodes**, and **標題頁** is first, **摩爾乃書第十章** last
- [ ] Episode 0007 downloads and plays with the screen off

If the order is scrambled in any client, switch to one feed per book — that fallback is
already written into the spec.

---

## 7. Submit to directories

| Directory | Where | Cost |
|---|---|---|
| Apple Podcasts | <https://podcasters.apple.com> | free, needs an Apple ID (no developer account) |
| Spotify | <https://podcasters.spotify.com> | free |
| Pocket Casts / Overcast | add-by-URL only | — |

Both ask for the feed URL and an email. Review usually takes a few days. Do this **after**
step 6, not before — a broken feed submitted once is harder to fix than a broken feed nobody
has seen.

---

## Undo / migration

- New recordings: upload them, re-run `verify_upload.py`, re-run `build_feed.py`, commit.
  Episode GUIDs stay stable, so existing subscribers only see the new items.
- Moving off the Archive: keep the same filenames, change `--base-url`, regenerate. GUIDs are
  independent of the host, so nothing is re-downloaded.
- Never change an item's identifier, the repository name, or any filename. Those three are
  the only permanent commitments in the whole project.

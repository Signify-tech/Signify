# Sign media

This folder holds sign media files. The backend serves them at `/api/media/{entryId}`. It only serves files that are registered in the vocabulary. All installed assets remain `UNVERIFIED`.

## Installed assets

- `letters/A.jpg` through `letters/Z.jpg`: the supplied RealSign alphabet images. Existing blank source, licence, and attribution fields are preserved; authenticity has not been verified.
- `words/`: 38 unchanged MP4 clips copied from the existing `datasets/raw/isl40/` corpus for 37 exact-match WORD entries and the PHRASE `thank you`. Original filenames are preserved. Selection uses exact original labels, accepted/unique dataset rows, preferring INCLUDE, then CISLR, highest quality score, with filename as the tie-breaker. `goodbye` and `me` were not mapped to `bye` or `I`; these are different labels requiring review.
- [words/provenance.csv](words/provenance.csv): full per-clip local dataset metadata and registered paths. Source, declared licence, paper citation, and original filename are also recorded in the vocabulary CSV. Dataset `accepted` review does not certify authentic ISL.

INCLUDE clips retain their declared `CC-BY-4.0` licence. CISLR clips retain the local metadata's `research (see HF card)` value; the [upstream CISLR card](https://huggingface.co/datasets/Exploration-Lab/CISLR) currently declares AFL-3.0. The aggregate is not a single uniformly licensed dataset. Original dataset files are untouched.

Run the backend from `backend/` as documented. When using another working directory, set `SIGNIFY_MEDIA_DIR` and `SIGNIFY_SEED_FILE` to absolute paths to this folder and `data/vocabulary.csv`.

```
media/
  words/     word and phrase signs, e.g. words/hello.mp4
  letters/   ISL fingerspelling alphabet, e.g. letters/a.png
```

## Adding an asset

1. **Confirm the right to use it.** The ISLRTC copyright policy (https://islrtc.nic.in/copyright-policy/) says its content "may not be reproduced partially or fully, without due permission". Get written permission, or use media you recorded with consenting signers. Do not bulk-download or scrape dictionary sites.
2. Copy the file into `words/` or `letters/`. Supported types:
   - `VIDEO`: `.mp4`, `.webm`. Plays to the end at the selected speed.
   - `GIF`: `.gif`. Shown for 2.6 s at 1× speed. Browsers cannot pause a GIF.
   - `IMAGE`: `.png`, `.jpg`, `.jpeg`, `.webp`. Shown for 1.8 s at 1× speed.
3. Fill in the matching row in `data/vocabulary.csv`, for example:

   ```csv
   hello,,WORD,words/hello.mp4,VIDEO,ISLRTC dictionary,Permission letter 2026-11-02,"© ISLRTC, DEPwD",UNVERIFIED,
   ```

4. Restart the backend. It re-imports the CSV on startup and updates existing rows by category and term.
5. Set `verification_status` to `VERIFIED` only after a fluent ISL signer has checked the sign.

The server rejects these rows at import time and logs a warning:

- absolute paths, or paths that leave this folder (`..`)
- a file extension that does not match `media_type`
- a `VERIFIED` row with no media

In local development, if a registered file is missing on disk, the entry shows **Media not added** and the player shows a placeholder. Production startup rejects missing registered files before importing. See [deployment and dataset expansion](../docs/DEPLOYMENT.md) for path/licence preflight, immutable Docker packaging, and redeployment steps.

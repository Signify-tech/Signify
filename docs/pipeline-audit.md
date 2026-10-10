# Signify pipeline audit — 10 October 2026

The targeted fixes preserve the existing React design, services, PostgreSQL schema, and JSON contracts. No dependencies were added and no media was fabricated or deleted. The updated backend is running on port 8080; the existing frontend runs on port 5173.

## Root causes and fixes

1. **Standalone A:** OpenNLP tags `A`/`a` as an article; R2 correctly filtered it under ordinary sentence rules. TranslationService now identifies explicit alphabet input for the entire request: a single A–Z letter with optional whitespace and trailing `.`, `!`, or `?`. The transformer preserves its surface letter and lookup uses LETTER records, including standalone I. Sentence pronouns and article filtering remain unchanged. Live testing also found `b?!` tagged as one PUNCT token; explicit alphabet context now handles attached punctuation without changing ordinary punctuation filtering. Alphabet units report their actual media status rather than incorrectly saying NOT_IN_VOCABULARY.
2. **Word videos:** The raw dataset was already present (642 MP4 clips across 40 labels), but `media/words/` was empty and no word-video paths were registered. Copied 38 existing clips unchanged, preserving filenames, for 37 exact-match WORD entries and the PHRASE `thank you`. Local per-clip source, declared licence, attribution, original filename, and provenance are recorded. No alias mapping was invented for `goodbye`→`bye` or `me`→`I`.
3. **Stale results:** Edits and example selections previously changed text without clearing the result or cancelling pending output. Request identifiers were incremented on completion, and success responses were not checked for cancellation. Edits/examples/reset now invalidate results and pending requests. Each submission receives an identifier before starting; both success and failure handlers ignore superseded requests. No translation is triggered by typing.
4. **Playback:** Repeated video signs reused an ended element; completed videos could replay from their end; pause/resume on the final sign could restart the whole sequence; late rejected play promises could mark old signs failed. Video elements are keyed by sign position and URL, completion is tracked separately from pause, replay rewinds, and obsolete play promises are ignored. Paused ended events do not advance. Load failures show a placeholder and missing-media badge. Existing image/GIF rendering, timing, navigation, speed, and unverified warnings remain covered.

## Files modified

- `backend/src/main/java/com/signify/translation/TranslationService.java`: request-level alphabet classification, letter lookup, accurate alphabet unit status.
- `backend/src/main/java/com/signify/translation/IslTransformer.java`: explicit alphabet context while preserving sentence rules.
- `frontend/src/pages/TranslatorPage.tsx`: result invalidation, cancellation, request ordering, and accurate initial media description.
- `frontend/src/components/SignPlayer.tsx`: video identity, replay/resume, stale promise handling, and error status.
- `data/vocabulary.csv`: 38 real word/phrase media registrations; the 26 existing JPEG registrations and verification values are unchanged.
- `media/words/*.mp4`: 38 byte-identical copies of selected raw dataset assets.
- `media/words/provenance.csv`: full selected local metadata with registered paths.
- `backend/src/test/java/com/signify/web/AlphabetMediaIntegrationTest.java`: actual CSV/assets, all letters in both cases and with punctuation, contextual filtering, requested sentences, ZEBRA URLs, real videos/ranges, missing word and alphabet files, mixed sequences, startup updates.
- `frontend/src/test/TranslatorPage.test.tsx`: edits/examples, reset, competing responses, cancellation, and fresh player state.
- `frontend/src/test/SignPlayer.test.tsx`: completion, pause/speed, replay, final-sign resume, repeated videos, navigation, loading errors, stale/current rejected play promises.
- `README.md`, `media/README.md`: current media inventory, usage, provenance, and limitations.
- `docs/pipeline-audit.md`: this report.

MediaStorage, MediaController, VocabularySeeder, VocabularyRepository, NlpPipeline, database migrations, and API schemas required no changes. Existing relative paths resolve correctly when launched from `backend/`; other working directories require absolute SIGNIFY_MEDIA_DIR and SIGNIFY_SEED_FILE overrides, as documented.

## Actual validation

- `mvn test`: **162 passed, 0 failures/errors/skips**, including all prior backend tests. Tests use H2 in PostgreSQL mode and the real shipped assets where applicable.
- `npm test`: **33 passed**, including all prior frontend tests. Playback methods are mocked in jsdom; these tests validate React behaviour, not browser codec support.
- `npm run lint`: passed with no warnings.
- `npm run build`: TypeScript and Vite production build passed.
- Live PostgreSQL 17.10: UP. Startup CSV import: **0 inserted, 233 updated, 0 rejected**. All 233 existing IDs, creation timestamps, and verification values retained; unrelated row contents unchanged apart from importer update timestamps.
- **104 live alphabet requests** (all 26 uppercase/lowercase/plain/whitespace/punctuation forms) returned the matching LETTER ID, IMAGE URL, playable=true, verified=false.
- **26/26 JPEG endpoints** returned HTTP 200, image/jpeg, and exact file bytes.
- **38/38 MP4 endpoints** returned HTTP 200, video/mp4, and exact file bytes. All 38 byte-range requests returned HTTP 206, correct Content-Range, and matching bytes.
- All 38 registered videos decoded fully through native macOS AVFoundation to actual frames; copied files match raw dataset bytes.
- Existing Vite /api proxy: all 26 alphabet translations/images and brother/hello/water video/range requests passed.
- Blank/overlong input rejected with HTTP 400; punctuation-only input returned zero signs; missing book media returned HTTP 404.
- Live API examples and sign metadata were inspected, including curly-apostrophe negation and mixed video/image/placeholder sequences.

| Live input | Documented rule output |
|---|---|
| A brother | BROTHER |
| An apple | APPLE |
| I am a student | I → STUDENT |
| I want to learn Indian Sign Language. | I → INDIAN → SIGN LANGUAGE → WANT → LEARN |
| What is your name? | YOUR → NAME → WHAT |
| She went to school yesterday. | YESTERDAY → SHE → SCHOOL → GO |
| I don't like cold tea. | I → COLD → TEA → LIKE → NOT |
| Thank you, my friend Zebra! | THANK YOU → MY → FRIEND → ZEBRA |

`brother`, `hello`, and `water` resolve respectively to existing IDs 116, 23, and 50 with VIDEO and AVAILABLE_UNVERIFIED. `ZEBRA` returns Z/E/B/R/A with IDs 214/193/190/206/189 and actual /api/media/{id} JPEG URLs. `brother Zebra book` produces one available video, five available images, and one missing-media word placeholder.

## Inventory and remaining limitations

- **642 original videos physically present**, untouched. **38 additional copies** under the configured media root; **38 mapped and API-playable/decoded clips** (37 WORD, 1 PHRASE). The other raw clips are alternate examples or unmatched labels, not missing downloads.
- Of 197 existing word/phrase entries, **159 lack installed media**: 150 WORD and 9 PHRASE. All 10 DIGIT entries also lack media. All 26 LETTER entries have accessible images.
- All 64 available assets remain **UNVERIFIED**. The alphabet source/licence/attribution fields were originally blank and remain blank. Dataset review labels do not substitute for fluent ISL verification.
- Word provenance preserves local declared licences. CISLR's local `research (see HF card)` declaration differs from the upstream card's AFL-3.0 label; the original metadata is retained rather than silently replaced. See media/README.md and upstream references: https://huggingface.co/datasets/Exploration-Lab/CISLR and https://github.com/AI4Bharat/INCLUDE.
- The browser tool denied opening http://localhost:5173 due to its browser security/permission policy. **Browser end-to-end rendering and actual browser video playback could not be checked.** Live HTTP/proxy checks, native video decoding, and React/jsdom tests passed; none is claimed as a completed browser test.
- ISL rules remain an interpretable approximation. Sense disambiguation, facial/non-manual features, complex-clause grammar, and intelligibility evaluation are still outside the implemented pipeline; no full ISL correctness is claimed.

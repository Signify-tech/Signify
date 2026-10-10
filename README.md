# Signify

Signify is an NLP-based assistive framework. It translates written English into an ordered sequence of Indian Sign Language (ISL) representations.

```
English input → NLP preprocessing → ISL-oriented transformation → sign dictionary lookup → fingerspelling fallback → sequential playback
```

- `backend/`: Spring Boot 3.5 (Java 21+) REST API. It uses Apache OpenNLP for NLP, PostgreSQL with Flyway for the vocabulary, and stores media on disk.
- `frontend/`: React 19, Vite and TypeScript, with hand-written CSS. The frontend has no UI framework and no router library.
- `data/vocabulary.csv`: vocabulary and media manifest, imported on startup. `data/vocabulary-template.csv` shows the column format.
- `media/`: 26 alphabet JPEGs and 38 registered word/phrase MP4 clips, all unverified. See [media/README.md](media/README.md).

For Neon Free, Render Free Docker, personal Vercel Hobby CLI deployment, and incremental dataset updates, see [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md). Public deployment currently requires media rights clarification; the guide lists the validation results and remaining checks.

## Prerequisites

- JDK 21 or newer. Tested with JDK 25.
- Maven 3.9 or newer.
- Node.js 20.19 or newer. Tested with Node 25.
- PostgreSQL 14 or newer. Tested with a local PostgreSQL server.

## Setup

```bash
createdb signify
```

```bash
cp backend/.env.example backend/.env
```

Edit `backend/.env` and set your PostgreSQL user and password. Environment variables override this file.

| Variable | Default | Purpose |
|---|---|---|
| `SIGNIFY_DB_URL` | `jdbc:postgresql://localhost:5432/signify` | JDBC URL |
| `SIGNIFY_DB_USERNAME` / `SIGNIFY_DB_PASSWORD` | `postgres` / empty | Database credentials |
| `SIGNIFY_PORT` | `8080` | API port |
| `SIGNIFY_CORS_ORIGINS` | `http://localhost:5173,http://127.0.0.1:5173` | Allowed frontend origins |
| `SIGNIFY_MEDIA_DIR` | `../media` | Media root, relative to `backend/` |
| `SIGNIFY_SEED_FILE` | `../data/vocabulary.csv` | Manifest imported on startup |
| `SIGNIFY_SEED_ON_STARTUP` | `true` | Turn the import on or off |

Flyway creates the schema on first start. The CSV import inserts new rows and updates existing ones. It never deletes rows.

The OpenNLP English models (Universal Dependencies EWT: sentence, token, POS and lemma) are Maven dependencies (`org.apache.opennlp:opennlp-models-*-en:1.3.0`). Maven downloads them automatically, so no separate model download is needed.

## Run

Start the backend from `backend/`. It listens on port 8080.

```bash
cd backend && mvn spring-boot:run
```

Start the frontend from `frontend/`. It runs at http://localhost:5173 and proxies `/api` to port 8080.

```bash
cd frontend && npm install && npm run dev
```

To build the frontend for production, set `VITE_API_BASE_URL` (see `frontend/.env.example`) if the API runs on another origin. Then run:

```bash
cd frontend && npm run build
```

## API

| Method | Path | Notes |
|---|---|---|
| `POST` | `/api/translate` | Body `{"text": "..."}`. The text must not be blank and must be 500 characters or fewer. |
| `GET` | `/api/vocabulary?category=&page=0&size=24` | Paginated. `category` is `WORD`, `PHRASE`, `LETTER` or `DIGIT`. `size` is 1–200. |
| `GET` | `/api/vocabulary/search?q=&category=&page=&size=` | Case-insensitive substring match. An exact match is listed first. |
| `GET` | `/api/vocabulary/stats` | Counts computed from the database and from the files on disk. |
| `GET` | `/api/rules` | The transformation rules the backend implements. |
| `GET` | `/api/media/{entryId}` | Serves a registered file. Supports HTTP range requests. Returns 404 if the file is missing. |
| `GET` | `/api/health` | Database, NLP and media folder status. |

Every error returns the same shape: `{timestamp, status, error, message, path, fieldErrors?}`.

Example request:

```bash
curl -X POST localhost:8080/api/translate -H 'Content-Type: application/json' -d '{"text":"I want to learn Indian Sign Language."}'
```

The response includes:

- `tokens`: each token with its lemma, UD POS tag, the action taken (`KEPT`, `GROUPED` or `DROPPED`) and the reason.
- `transformedSequence`: here `I INDIAN "SIGN LANGUAGE" WANT LEARN`.
- `units` and `rulesApplied`: each rule application, with the order before and after it ran.
- `signs[]`: one entry per playback item, with `entryId`, `gloss`, `representation`, `mappingSource`, `status`, `mediaType`, `mediaUrl`, `playable` and `placeholder`.
- `summary`, `warnings` and the measured `processingTimeMs`.

### Media status values

| `status` | Meaning |
|---|---|
| `NOT_IN_VOCABULARY` | No entry exists for the word. The word is fingerspelled, for example `ZEBRA` becomes `Z → E → B → R → A`. |
| `MEDIA_MISSING` | An entry exists, but no media file is registered or present on disk. The player shows a labelled placeholder. |
| `AVAILABLE_UNVERIFIED` | A file exists but has not been verified as authentic ISL. The player plays it with a warning. |
| `VERIFIED` | A file exists and has been reviewed as an authentic ISL sign. |

## Transformation rules

The rules are explicit and live in `IslTransformer`. They run in this order:

1. **R1 Phrase grouping.** Registered phrases of 2–4 words become one sign unit.
2. **R2 Filtering.** Removes articles, the auxiliaries be, do and have, the infinitive `to`, the prepositions `to` and `of`, the possessive `'s` and punctuation.
3. **R3 Base forms.** Uses lemmas, and turns object pronouns into their base form (me → I).
4. **R4 Time first.** Moves today, tomorrow, yesterday and similar time words to the start.
5. **R5 Negation last.** Keeps not, n't, never and "no" before a noun, and moves them to the end.
6. **R6 Verb after object.** Applies only to simple statements with one verb group. It is skipped, with a note, when the sentence has a conjunction or more than one verb group.
7. **R7 Question word last.** Applies to questions.

These rules approximate patterns commonly described for ISL. They are not a full ISL grammar. Word lookup tries the lemma first, then the surface form, then a simple singular form for nouns. A word that still has no match is fingerspelled.

## Tests

Run the backend tests. They use H2 in PostgreSQL mode, so no database is needed. Media integration tests also check the shipped CSV and files.

```bash
cd backend && mvn test
```

```bash
cd backend && mvn package
```

Run the frontend tests (Vitest and Testing Library), the linter (oxlint) and the type-checked production build.

```bash
cd frontend && npm test
```

```bash
cd frontend && npm run lint
```

```bash
cd frontend && npm run build
```

## Vocabulary and media

`data/vocabulary.csv` registers 197 common words and phrases, 26 letters and 10 digits. The 26 alphabet images and videos for 37 words and one phrase are available, all `UNVERIFIED`. The project target of 100 words and phrases with media is not met. The vocabulary page and `/api/vocabulary/stats` show live counts. [media/README.md](media/README.md) explains the installed assets and their provenance.

An isolated A–Z letter, in either case and optionally followed by sentence punctuation, selects its alphabet image. This includes standalone `I`; in sentences, `I` remains a pronoun and articles are filtered normally. Editing the translator input cancels pending requests and clears previous results; translate again to produce a new sequence.

Possible sources of authentic ISL media are listed below. Neither has been integrated:

- **ISLRTC**: https://islrtc.nic.in. It links a dictionary site, a YouTube channel and a dataset on data.gov.in. Its copyright policy requires permission before any reproduction. Ask ISLRTC for permission before using its media.
- **Your own recordings**, made with fluent ISL signers who give consent. Have each sign reviewed before you mark it `VERIFIED`.

## Limitations

- Entries without installed media show placeholders. Installed media remains unverified until authentic ISL review is completed.
- The rules simplify ISL grammar. Non-manual features such as facial expression and head movement are not represented.
- Word senses are not disambiguated, and multi-clause sentences keep their English order.
- No accuracy or intelligibility results are claimed. An evaluation with ISL users has not been done yet.
- Browser text-to-speech ("Read English aloud") reads the English input aloud. It is separate from the visual ISL output.

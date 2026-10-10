# Deployment and dataset expansion

Target: private `Signify-tech/Signify` repository, branch `main` → Render Free Docker backend, Neon Free PostgreSQL, and a personal Vercel Hobby frontend deployed through the CLI. Media lives in the backend image. No paid storage, persistent disk, billing, or cloud deployment has been added.

## Current readiness

The current manifest contains 233 entries and 64 registered assets: 26 JPEG alphabet images and 38 MP4 word/phrase clips (5.00 MiB total). All remain UNVERIFIED. There are 159 word/phrase entries and 10 digit entries without registered media; they retain the existing placeholders. Raw datasets are excluded from Git and Docker; registered `media/` assets remain included.

**Public redistribution is blocked pending rights review for 58 assets:** 26 RealSign images have no recorded source/licence/attribution; 32 CISLR clips retain the unresolved local licence `research (see HF card)`. The upstream card declares AFL-3.0, which has not been reconciled with the individual clips. Six INCLUDE clips have recorded CC-BY-4.0 metadata; confirm the original evidence and required credits as well. Do not infer permission or authentic ISL from file availability, dataset acceptance, or a populated licence field. No metadata or verification status was changed to resolve this blocker.

Run these read-only checks from the repository root:

```bash
python3 scripts/validate_manifest.py
python3 scripts/validate_manifest.py --public
python3 -m unittest discover -s scripts -p 'test_*.py'
```

The technical check currently succeeds with 58 rights reviews. The public check intentionally fails with 58 errors. Resolve these before publishing the packaged media; the Dockerfile currently preserves all existing assets. The validator checks identity, schema lengths, categories/status, path containment, file existence, nonempty files, extensions, and credit metadata. It does not certify licences, decode media, or verify ISL authenticity.

## 1. Neon Free database

1. Create a **Free** Neon project and database, using a region near the Render service. Do not select a paid plan or enable billing. Retain the same project, branch, database, and role for subsequent deployments.
2. In Neon's Connect dialog, select the database and role and obtain the **direct/unpooled** hostname. The backend limits its connection pool to three connections. Flyway creates the existing schema in a fresh database, and startup imports the CSV; no local database export is needed.
3. Store the following as Render secrets. Use separate credentials, with no password in the URL:

```text
SIGNIFY_DB_URL=jdbc:postgresql://<direct-neon-host>/<database>?sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory&channelBinding=require
SIGNIFY_DB_USERNAME=<neon-role>
SIGNIFY_DB_PASSWORD=<neon-password>
```

`verify-full` checks the server certificate and hostname; `DefaultJavaSSLFactory` uses the Java trust store. Do not disable certificate verification to resolve connection errors. See [pgJDBC SSL](https://jdbc.postgresql.org/documentation/ssl/) and [Neon Java connections](https://neon.com/docs/guides/java). The prepared URL has not been tested against Neon because no cloud database was provisioned.

## 2. Render Free Docker backend

After resolving public rights, review and push the changes yourself. Keep the repository private. Authorize Render's GitHub integration for only the required organization repository, then create a **Web Service** with:

| Setting | Value |
|---|---|
| Repository | Private `Signify-tech/Signify` |
| Branch | `main` |
| Runtime | Docker |
| Root Directory | Empty (repository root) |
| Dockerfile Path | `./Dockerfile` |
| Docker Build Context Directory | `.` if shown |
| Instance Type | **Free** |
| Health Check Path | `/api/health` |
| Persistent disk | None |
| Auto-deploy | Optional; use manual deployment while completing validation |

Do not enter Maven build/start commands for Docker. Render builds the root Dockerfile, which packages the Java 21 JAR, `data/vocabulary.csv`, and `media/`, and runs as a non-root user with read-only application files. NLP model dependencies are included in the executable JAR.

| Environment variable | Production value |
|---|---|
| `SIGNIFY_DB_URL`, `SIGNIFY_DB_USERNAME`, `SIGNIFY_DB_PASSWORD` | Neon values above; required and nonblank |
| `SIGNIFY_CORS_ORIGINS` | Exact frontend origin, e.g. `https://<project>.vercel.app`; required |
| `PORT` | Supplied by Render (`10000` by default); do not hardcode it |
| `SPRING_PROFILES_ACTIVE` | Set explicitly to `production` on Render; also the image default |
| `SIGNIFY_MEDIA_DIR` | `/app/media` (already set in image) |
| `SIGNIFY_SEED_FILE` | `/app/data/vocabulary.csv` (already set in image) |
| `SIGNIFY_SEED_ON_STARTUP` | `true` (default) |

Use only `SIGNIFY_DB_*` for connection configuration. `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD` are redundant: remove them from the service and any linked environment group. Remove connection overrides `SPRING_DATASOURCE_HIKARI_JDBC_URL`, `SPRING_DATASOURCE_HIKARI_USERNAME`, `SPRING_DATASOURCE_HIKARI_PASSWORD`, `SPRING_FLYWAY_URL`, `SPRING_FLYWAY_USER`, and `SPRING_FLYWAY_PASSWORD`. Keep unrelated pool/Flyway settings. Also inspect `SPRING_APPLICATION_JSON`, `JAVA_TOOL_OPTIONS`, `JDK_JAVA_OPTIONS`, Docker Command, and `SPRING_CONFIG_*` for stale datasource/profile overrides; remove those overrides, preserving the image's memory settings. Do not put actual credentials into JVM arguments or Docker Command.

Use the planned stable Vercel project origin initially, then update CORS after obtaining the actual URL. Origins have no path/trailing slash. Multiple explicit origins can be comma separated; never use `*`. Preview deployments need their own explicit origin if you want them to access the API. Updating Render environment variables requires redeployment.

The production profile requires DB credentials/CORS explicitly, binds `0.0.0.0`, validates the entire manifest before importing, and disables Flyway clean. Local development retains its existing defaults. No public database administration/upload endpoint is introduced.

### Diagnose the localhost failure and redeploy

The repository's Dockerfile already sets `production`, copies `backend/src` into the build, and packages `application-production.yml` in the JAR. There is no separate Flyway URL. A baseline Docker run with the expected production profile and missing `SIGNIFY_DB_URL` did **not** attempt localhost; overriding the profile to `default` reproduced `Connection to localhost:5432 refused`. A stale `SPRING_DATASOURCE_URL` pointing to localhost also reproduced that error even with an external `SIGNIFY_DB_URL`, matching [Spring property precedence](https://docs.spring.io/spring-boot/3.5/reference/features/external-config.html). These are reproduced failure mechanisms, not proof of which setting caused the remote deployment failure. Render's actual environment and deployed revision have not been inspected.

The new early environment check runs after YAML/profile resolution and **before datasource/Flyway beans connect**. Production requires a password-free PostgreSQL JDBC URL and nonblank credentials. It rejects loopback URLs, conflicting datasource/Hikari overrides, dedicated Flyway connection settings, and disabled Flyway. Neon URLs require TLS and the direct host. If [Render's injected `RENDER=true`](https://render.com/docs/environment-variables) is present, a missing/wrong production profile fails explicitly instead of using local defaults. Errors name the problematic key, never its value or the original URL parsing exception. Do not enable TRACE logging or dump environment variables for diagnosis.

To configure the existing Neon database, open its Connect dialog, select the existing Singapore project/branch/database/role, and turn **connection pooling off**. For a connection string shaped like `postgresql://<role>:<password>@<direct-host>/<database>?sslmode=require`, copy the host/database into the password-free JDBC example above and enter the role/password separately in Render. Do not merely prepend `jdbc:` to a URL containing credentials. Keep `channelBinding=require` if present. The existing local PostgreSQL database does not need migration/export, and no Neon connection has been attempted automatically.

After reviewing the fix, run these commands yourself from the repository root:

```bash
git status --short
git diff --check
git add backend/src/main/java/com/signify/config/ProductionDatabaseEnvironmentPostProcessor.java \
  backend/src/main/resources/META-INF/spring.factories \
  backend/src/test/java/com/signify/config/ProductionDatabaseEnvironmentPostProcessorTest.java \
  backend/.env.example docs/DEPLOYMENT.md
git diff --cached
git commit -m "fix: validate production database configuration before startup"
git push origin main
git rev-parse HEAD
```

On the **existing** Render service:

1. Confirm repository `Signify-tech/Signify`, branch `main`, runtime Docker, empty Root Directory, Dockerfile `./Dockerfile`, root build context, and Free instance. Leave Docker Command unset so the image entrypoint is used.
2. Set the required environment values in the table above, including explicit `SPRING_PROFILES_ACTIVE=production`; remove conflicting variables from the service **and linked environment groups**. Save without exposing passwords or posting screenshots of secrets. Never change the Neon project/branch/database to work around configuration errors.
3. Choose **Manual Deploy → Clear build cache & deploy**, so the updated code is rebuilt from GitHub. Compare the deployed commit SHA in Render's deployment details with `git rev-parse HEAD`. The build should copy `backend/src`; changing environment values alone cannot update an old JAR. Do not deploy a stale prebuilt image tag.
4. On startup, confirm the active profile is `production`. A configuration guard error should be fixed before retrying; it deliberately prevents any connection. After configuration passes, Flyway should log the intended external database target, validate/apply migrations, and the vocabulary importer should report zero rejected rows. Share only redacted error summaries, not full connection URLs or credentials.
5. Verify `https://<backend>.onrender.com/api/health` returns 200, then check vocabulary, a translation, an image, and video ranges as described below. `No open ports detected` after an exception is a consequence of failed startup; changing PORT cannot fix database selection.

| Failure | What to check |
|---|---|
| `Signify production database configuration: ...` | Named key, production profile, conflicting overrides; no DB connection was attempted |
| Still `Connection to localhost:5432 refused` without the new guard | Wrong/stale deployed commit, wrong Dockerfile/context, or bypassed entrypoint/configuration; confirm the actual artifact first |
| External `UnknownHostException` / timeout / connection refused | Direct hostname, DNS/network reachability and Neon compute; hostname selection is now a separate issue from authentication |
| `28P01` / password authentication failed | Role/password for the selected Neon branch/database; keep them in separate env fields |
| `3D000` / database does not exist | Database name in the JDBC path, not a new database/provider |
| SSL/certificate/handshake/channel-binding failure | URL TLS mode, host, Java trust store, driver settings; do not turn off TLS or certificate verification |
| Flyway checksum/validation failure | Migration history and committed migrations; do not delete records, run clean, or repair blindly |
| Flyway recommends upgrade for PostgreSQL 18 | The pinned Flyway 11.7.2 certifies through PostgreSQL 17; this warning is distinct from a connection failure. See actual PostgreSQL 18 validation below |

The dependencies remain Flyway core/PostgreSQL module **11.7.2** and pgJDBC **42.7.11**. No speculative dependency upgrade or schema change was introduced. The [pinned Flyway PostgreSQL implementation](https://github.com/flyway/flyway/blob/flyway-11.7.2/flyway-database/flyway-database-postgresql/src/main/java/org/flywaydb/database/postgresql/PostgreSQLDatabase.java) recommends an upgrade for versions newer than 17; it does not itself reject 18. A successful isolated PostgreSQL 18 test confirms this project's migration works, but does not certify every Flyway feature or prove Neon authentication/TLS.

### Optional local Docker validation

Run after starting Docker, from the repository root:

```bash
docker build -t signify:deployment-check .
docker run --rm --name signify-check -p 8091:8080 --memory=512m --cpus=1 \
  --env-file /absolute/path/to/untracked-production.env signify:deployment-check
docker stats signify-check
```

The untracked environment file should contain the required DB/CORS variables and `PORT=8080`; use a disposable test database for this check. Docker Desktop can access a local PostgreSQL test server at `host.docker.internal`, subject to its PostgreSQL access configuration. Use the TLS URL above for Neon. Never bake credentials into the image or upload the environment file.

The image sets 192 MiB heap, 96 MiB metaspace, 32 MiB code cache, 16 MiB direct memory, 512 KiB thread stacks, and Serial GC. Tomcat has at most eight request threads; Hikari has at most three connections. These limits leave room for native memory and loaded models, but require confirmation under Java 21 and Render's actual 512 MB limit. Do not raise the heap to 512 MB. If the container is killed or sustained load exceeds its memory/quota, report the free-tier blocker instead of upgrading automatically.

## 3. Vercel personal Hobby frontend via CLI

Vercel Hobby is for **personal, noncommercial** use. Confirm eligibility; an organization/commercial deployment cannot be promised at ₹0 on this plan. CLI deployment avoids connecting the private organization Git repository. See [Hobby terms and limits](https://vercel.com/docs/plans/hobby) and [CLI deployment](https://vercel.com/docs/cli/deploy).

With the Vercel CLI installed, run these yourself:

```bash
cd frontend
vercel login
vercel link
vercel env add VITE_API_BASE_URL production
vercel env add VITE_API_BASE_URL preview
vercel
vercel --prod
```

During `link`, select your **personal Hobby account**, create/select the frontend project, and use the current `frontend/` directory. Enter `https://<backend>.onrender.com` for both API environment prompts, without `/api`, credentials, query strings, or fragments. `npx vercel@latest` can replace `vercel` if it is not installed. Do not choose a paid team or connect Git. `vercel.json` sets Vite, `npm ci`, `npm run build`, and `dist`.

Update Render's `SIGNIFY_CORS_ORIGINS` to the actual stable production frontend origin and redeploy the backend. A preview URL is different from the production alias. New Vercel environment values take effect on the **next frontend deployment**.

For a local production build:

```bash
cd frontend
npm ci
npm test
npm run lint
VITE_API_BASE_URL=https://<backend>.onrender.com npm run build
```

Production builds reject missing/localhost/non-HTTPS API origins and `/api` suffixes. Development retains Vite's existing localhost proxy. Only the public API origin belongs in `VITE_*`; never place DB credentials there. Hash routing already supports refresh/direct navigation and needs no rewrite.

## 4. Verify deployment

Replace the example origin with the actual Render origin:

```bash
curl -i https://<backend>.onrender.com/api/health
curl -sS 'https://<backend>.onrender.com/api/vocabulary?category=LETTER&size=26'
curl -sS https://<backend>.onrender.com/api/translate \
  -H 'Content-Type: application/json' -d '{"text":"hello Zebra book"}'
```

Use entry IDs/media URLs from **that database's API response**; do not assume local and Neon IDs are identical. Fetch an image and a video:

```bash
curl -I https://<backend>.onrender.com/api/media/<image-entry-id>
curl -i -H 'Range: bytes=0-31' https://<backend>.onrender.com/api/media/<video-entry-id>
```

Expect `image/jpeg` for current letters, `video/mp4` for current clips, and `206` plus `Content-Range` for a valid video range. Missing media returns 404; translation returns the existing placeholder metadata. Verify all 26 letter URLs, `brother`, `hello`, `water`, and ordered `ZEBRA` fingerspelling. UNVERIFIED assets must show the unverified badge.

Open the Vercel site and inspect browser Network/Console: API and media requests should use the Render HTTPS origin, with no CORS errors. Test translation, vocabulary search, direct hash navigation/refresh, editing/reset/rapid submissions, mixed image/video/placeholder playback, pause/resume, navigation, speed, restart, and playback errors. Repeat after a cold start. API checks alone do not prove browser playback. The rule-based pipeline still has the linguistic limitations documented in [pipeline-audit.md](pipeline-audit.md).

## 5. Incremental dataset workflow

1. Obtain the actual media and document its source, redistribution licence/permission evidence, attribution, and ISL review. Keep permission documents outside the public API; record a useful reference in `notes`. Keep `UNVERIFIED` until a qualified ISL reviewer confirms the sign. Verification and redistribution permission are separate requirements.
2. Copy permitted images/videos into `media/letters/`, `media/words/`, or another subfolder such as `media/phrases/`. Preserve filenames/extensions and exact Linux case. Use browser-compatible MP4/WebM, JPEG/PNG/WebP, or GIF. Check actual decoding/playback; changing an extension does not convert a codec. Avoid symlinks, absolute paths, and large unreviewed bulk imports.
3. Edit the **existing** `data/vocabulary.csv` using `data/vocabulary-template.csv` columns. For an existing entry, update its row without changing `(category, normalized_term)`. For a new entry, append one unique row. Paths such as `phrases/<actual-filename>.mp4` are relative to `media/`. Set `media_type` consistently and provide source, licence, attribution, status, and notes. WORD has one normalized word; PHRASE supports two to four. The current letter paths are `letters/A.jpg` through `letters/Z.jpg`. Unknown words use these same LETTER entries.
4. Run the technical/public manifest checks above. Public preflight must pass before publishing the expanded image. Confirm licensing evidence manually, including any obligations beyond attribution. Run backend/frontend regression checks:

```bash
cd backend
mvn -B -ntp verify
cd ../frontend
npm ci
npm test
npm run lint
VITE_API_BASE_URL=https://<backend>.onrender.com npm run build
```

5. Test the expanded manifest locally with the existing startup importer and API. Check new terms, phrases, media MIME/ranges/playback, and old IDs/statuses. Take a database backup before significant mapping changes. Keep the frontend unchanged for ordinary additions: it consumes category/type/status and `/api/media/{entryId}` generically.
6. From the repository root, inspect `git status --short` and `git diff --check`. Stage the specific new media files, any provenance/credit documentation, and CSV together with `git add <specific-paths>`. Review the staged diff, commit, and `git push` to your normal deployment branch. These actions are for you to run; none have been executed here. `.gitignore` keeps raw downloads, credentials, dependencies, and build artifacts out while allowing registered assets. Check unexpectedly absent assets with `git check-ignore -v <media-path>`; do not force-add secrets.
7. On the **existing** Render service, choose **Manual Deploy → Deploy latest commit**, or let its configured auto-deploy run. The new Docker image incorporates the new files/CSV. Keep the existing Neon environment values unchanged. Startup transactionally inserts new identities and updates changed metadata in place. Matching unchanged rows retain IDs and timestamps; rows absent from the CSV are retained. The import log's `updated` count includes existing rows inspected even when SQL performs no write.
8. Recheck health, vocabulary, new/old translations, media endpoints, and browser playback. A dataset-only change needs no Vercel redeployment. Frontend changes use `cd frontend` then `vercel --prod`; backend/config/media changes require a new Render image.

Do not delete previously registered assets or expect removal from CSV to delete database rows: retained records still need their packaged files. Do not rename category/normalized identity to update metadata; that creates a different record. Coordinate explicit renames/removals separately. Avoid overlapping deployments with different manifests to the same DB. Rolling back an image also reimports its older CSV and can restore older metadata; review database changes before rollback.

No durable state is written to Render's filesystem. PostgreSQL holds vocabulary metadata; Git/Docker hold media. Copying assets into a running Render instance would be lost on restart and is not the workflow. Future object storage can be implemented at the existing `MediaStorage`/`MediaController` boundary while preserving relative manifest keys, entry IDs, statuses, and `/api/media/{entryId}` contracts. NLP, vocabulary lookup, and React need no storage redesign. No object-storage service or paid integration has been introduced.

## Free-tier limits and measured validation

- [Render Free](https://render.com/docs/free): sleeps after 15 idle minutes, with about a minute to wake; 750 instance hours/workspace/month; ephemeral filesystem and no persistent disk. Bandwidth/build quotas can suspend service/builds without a payment method. Keep billing disabled and monitor usage. Render recommends Free for hobby/preview workloads, not reliable production service; uninterrupted availability cannot be promised.
- [Neon Free](https://neon.com/pricing): current limits include 100 CU-hours/project/month, 1 GB storage/project (20 GB/account), 5 GB public transfer/month, and autosuspend after five idle minutes. The [October 2026 storage update](https://neon.com/blog/neon-free-plan-1-gb-per-project) supersedes older 0.5 GB references. Choose only the permanent Free plan. Media is not stored in Neon.
- Vercel Hobby is personal/noncommercial and quota limited; the [plan page](https://vercel.com/docs/plans/hobby) lists 100 GB fast data transfer. Monitor all three dashboards as the dataset/traffic grows. Stop or reduce usage at free limits; no automatic paid fallback is configured. A ₹0 deployment is conditional on eligibility and remaining within quotas.

Local validation on 2026-10-10:

| Check | Actual result |
|---|---|
| Maven tests + executable JAR packaging | 164 passed, no failures/errors/skips |
| `npm ci` / frontend tests / lint / HTTPS production build | Install succeeded, 46 tests passed, lint/build passed |
| Manifest validator tests | 8 passed |
| Technical/public media preflight | Technical passed; public blocked by 58 rights reviews |
| Fresh real PostgreSQL, production profile | Flyway initialized 1 migration; CSV imported 233 rows |
| Different working directory, injected port, media | 26 JPEG + 38 MP4 responses matched actual file bytes/types; 38 byte-range checks passed |
| Standalone alphabet | 52 uppercase/lowercase requests with whitespace/punctuation passed |
| Sentences/known words/unknown/mixed media | Required examples exercised; ZEBRA ordered with real media URLs; missing media 404; invalid inputs 400 |
| CORS | Configured HTTPS origin and POST preflight accepted; unapproved origin rejected |
| Restart and incremental import | IDs/timestamps unchanged on repeat; one real goodbye clip added via a temporary manifest, 234 rows, all original records unchanged |
| Limited JVM load check | 64 translations at concurrency 8 per run; two runs passed, maximum sampled process RSS 236.6 MiB on macOS Java 25 |
| Docker build/run and Java 21 / 512 MB cgroup | Not executed: installed Docker CLI had no running daemon. Image tags resolved in registry, which does not validate the build |
| Neon/Render/Vercel and remote browser playback | Not executed: cloud resources/deployment were explicitly excluded; browser localhost permission was unavailable during the prior audit |

The disposable PostgreSQL database and temporary expansion files were removed; the existing local Signify database was not altered. The memory sample is indicative, not a guarantee under Docker/Render or larger future datasets. Recheck actual container memory and decoded media before deployment.

### Render connection fix validation — 2026-10-11

The five changed files are the production environment guard, its `META-INF/spring.factories` registration, its regression test, `backend/.env.example`, and this guide. Docker/YAML were already correct and remain unchanged. No frontend, dataset, migration, or dependency changes were required.

- **188 backend tests** passed (24 new configuration regressions); executable JAR packaging passed. **46 frontend tests**, lint, and HTTPS production build passed. **8 manifest-validator tests** passed; technical preflight passed and public preflight remains blocked by the same 58 rights reviews.
- The updated Docker image built and ran with **Java 21** and a **512 MiB cgroup limit**. The production YAML and guard registration were confirmed inside the actual image JAR; the app ran non-root, with read-only application files and no `.env` in `/app`.
- Ten negative Docker startup cases passed: missing URL/username/password, loopback URL, wrong profile on Render, conflicting standard/Hikari/Flyway connection settings, a URL containing a password, and an invalid external hostname. All exited with failure; none attempted localhost or printed the test credential values. The invalid external hostname reached the driver/DNS layer and named the requested hostname, proving URL selection.
- Against an **explicitly approved disposable local PostgreSQL 18.6 container**, Flyway used the application datasource, applied migration V1, and imported 233 entries. Health returned 200, runtime `PORT=9090` took effect, all 26 JPEG/38 MP4 responses matched real bytes/MIME types, and all 38 range checks passed. Required translations and ordered playable ZEBRA fingerspelling succeeded.
- 64 translations at concurrency eight passed; peak cgroup memory was **199.8 MiB**. Restart needed no migration and preserved all IDs and creation/update timestamps. Flyway 11.7.2 emitted its PostgreSQL 18 warning; it did not prevent migration/startup.
- The test containers, in-memory database, and test network were removed. The existing local PostgreSQL database and Neon were not touched. No commit, push, Render deployment, or cloud-authentication/TLS validation was performed. Actual Render revision/environment verification and remote browser checks remain necessary.

## Final checklist

- [ ] Resolve 58 current rights reviews; preserve truthful UNVERIFIED status.
- [ ] Confirm personal/noncommercial Hobby eligibility and Free plans with no billing.
- [x] Build/run Java 21 Docker image under 512 MiB with isolated PostgreSQL 18; exercise API/media/load/restart checks locally.
- [ ] Review changes/secrets, then push to the private repository yourself.
- [ ] Create Neon Free; set separate TLS JDBC credentials on Render Free.
- [ ] Deploy root-context Docker backend and verify migrations/import/health/media.
- [ ] Link personal Vercel project, set public API origin, deploy with CLI.
- [ ] Set exact final CORS origin, redeploy backend, and verify browser playback/navigation.
- [ ] Monitor free quotas and follow the same CSV/media preflight workflow for each expansion.

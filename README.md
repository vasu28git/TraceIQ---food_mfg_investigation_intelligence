# TaceIQ — Investigation Workspace

TaceIQ is a **multi-tenant investigation workspace** for organization investigators. Evidence is ingested from integrations or manual file uploads, normalized into `canonical_evidence` (PostgreSQL source of truth), projected into Neo4j for traceability, and exposed through an evidence-derived chronology. Investigators work in an isolated workspace (checks, notes, decisions, final result) and complete investigations only when backend-validated prerequisites are met.

> **Portfolio/demo status:** functionally validated end-to-end (Neon PostgreSQL + Neo4j + Spring Boot + React). No mock data in main flow.

---

## Key Capabilities

- **Multi-tenant RBAC** — Platform Admin vs Organization User, role/permission scoped by `org_id` (backend authoritative)
- **Evidence input (2 paths):** Integration sync (generic Evidence Vault API, paginated `limit/cursor/updatedSince`) **and** Manual File Upload → `canonical_evidence`
- **Canonical evidence** — idempotent `UNIQUE(org_id, integration_id, external_id)` + `content_hash` (SHA-256 of sorted `normalized_payload`), `is_deleted` soft-delete
- **Graph projection** — PostgreSQL → Neo4j `MERGE (e:Evidence {orgId, stableId})`, `BELONGS_TO`/`CREATED_BY`/`DERIVED_FROM`, validated by `GraphValidator`
- **Graph Ready gate** — `SUCCESS` sync + canonical exists + projection + `evidenceCount == canonicalCount` + `valid`
- **Investigation lifecycle** — `DRAFT → ACTIVE → COMPLETED → ARCHIVED`, completion requires `graphReady + ≥1 check + 0 OPEN + ≥1 decision + finalResult`
- **Evidence linking** — `investigation_evidence` scoped `(org_id, investigation_id, canonical_evidence_id)`, graph-ready + `DRAFT/ACTIVE` only
- **Evidence Chronology** — *evidence-derived* `EVIDENCE_CREATED (sourceCreatedAt)` / `EVIDENCE_UPDATED (sourceUpdatedAt when distinct)` only; not an audit log, no fabricated `LOGIN/EXPORT` actions
- **Investigator checks** — `OPEN → COMPLETED / SKIPPED`, lifecycle + graph-ready gated
- **Notes** — author-owned (`author_user_id`), 5000-char, `DRAFT/ACTIVE` only, never enters chronology/completion
- **AI Recommendations (optional)** — advisory `VERIFY/INVESTIGATE/REVIEW/FOLLOW_UP`, bounded context (`≤100 evidence, ≤200 timeline events`), no auto-mutation, disabled by default
- **Decisions & Final Result** — human-owned, `ACTIVE` only, decisions require resolved checks, final result requires decision
- **Reports** — `JSON/CSV/PDF` (default via `REPORT_DEFAULT_FORMAT`), bounded, safe-field only (no `orgId`, `contentHash`, `normalizedPayload`, credentials)
- **Workspace UI** — `OrganisationLayout` with `Evidence Graph` global explorer (`caseId`/`actorId` filters, `GET /graph/evidence/{stableId}`, `GET /graph/cases/{caseId}/traceability` depth capped 10)

---

## Architecture

```
User → React (Vite + Axios Bearer) → Spring Boot API (/api)
                                   ├─ PostgreSQL (Neon) — canonical_evidence = source of truth
                                   ├─ Neo4j (optional) — derived graph (MERGE, org-filtered)
                                   └─ Gemini (optional) — NoOp provider if GEMINI_API_KEY empty
```

* Users never access Neo4j/DB/AI directly. PostgreSQL → Neo4j is **not** a single ACID transaction; `HikariPool` + `initialization-fail-timeout=0` lets the app boot when Neon is unreachable and `GraphReadinessService` drives projection/validation.

---

## Technology Stack

- **Frontend:** React 18, TypeScript 5.6, Vite 5.4, React Router 6, Zustand 4, Axios 1.7
- **Backend:** Spring Boot 3.3.6, Java 21, Spring Security + JWT (jjwt 0.12.6), Spring Data JPA/Hibernate 6.5, PostgreSQL driver 42.7, Flyway (disabled, migrations via JDBC), Neo4j Java Driver, OpenPDF 1.3
- **DB:** PostgreSQL 14+ (Neon pooled `sslmode=require`), Neo4j `neo4j+s://`
- **Build:** Maven 3, Vite

---

## Core Workflow

```
Evidence Input (Integration sync OR File upload)
  ↓ canonical_evidence (PostgreSQL, idempotent)
  ↓ Neo4j projection (MERGE) → GraphValidator → GRAPH READY
  ↓ Complaint (optional) OR Direct Investigation (DRAFT)
  ↓ Link Evidence (investigation_evidence, DRAFT/ACTIVE, graph-ready)
  ↓ Evidence Chronology (EVIDENCE_CREATED/UPDATED only)
  ↓ Checks (OPEN → COMPLETED/SKIPPED)
  ↓ AI Recommendation (optional, advisory, bounded)
  ↓ Decision (human, ACTIVE, requires resolved checks)
  ↓ Final Result (human, ACTIVE, requires decision)
  ↓ Complete (ACTIVE → COMPLETED, requires graphReady + checks + decision + finalResult)
  ↓ Report (JSON/CSV/PDF, safe fields)
  ↓ Archive (COMPLETED → ARCHIVED, graph-ready gated)
```

---

## Local Setup

### Prerequisites

- Java 21 (Temurin), Maven 3.9+, Node 18+, PostgreSQL (Neon) and Neo4j (optional)
- No real secrets are committed – copy `.env.example` to `.env` and fill.

### Environment Variables

See `.env.example`. Required for production:

```
DATABASE_URL=jdbc:postgresql://…/neondb?sslmode=require&channelBinding=require
DATABASE_USERNAME=
DATABASE_PASSWORD=
JWT_SECRET=                         # 64 hex chars, e.g. openssl rand -hex 32
PLATFORM_ADMIN_EMAIL=
PLATFORM_ADMIN_PASSWORD=
# optional
NEO4J_URI=neo4j+s://…
NEO4J_USERNAME=
NEO4J_PASSWORD=
NEO4J_DATABASE=neo4j
GEMINI_API_KEY=
GEMINI_MODEL=gemini-1.5-flash
FILES_STORAGE_PATH=./uploads
```

Backend fails fast if `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `JWT_SECRET`, `PLATFORM_ADMIN_EMAIL/PASSWORD` are missing (no silent fallback to real credentials).

---

## Backend Setup

```bash
cp .env.example .env   # fill real values, never commit .env
# or set env vars in your shell / deployment platform

mvn package -o -DskipTests
java -jar target/taceiq-0.0.1-SNAPSHOT.jar --logging.file.name=logs/backend.log
# or: mvn spring-boot:run
```

* `spring.jpa.hibernate.ddl-auto=none` – schema via `src/main/resources/db/migration/` applied manually/JDBC.
* `spring.flyway.enabled=false` – enable only when automating migrations.

---

## Frontend Setup

```bash
cd frontend
cp .env.example .env  # if you add VITE_API_BASE_URL
npm install
npm run build   # tsc -b && vite build
npm run dev     # Vite dev server http://localhost:5173 (proxy /api → :8080)
```

`VITE_API_BASE_URL` defaults to `/api`.

---

## Running the Application

1. Start PostgreSQL (Neon) and ensure `DATABASE_URL` reachable (`TestNeon` style `SELECT 1`).
2. Start Neo4j if you need graph projection (app starts without it, but `GET /graph/validation` will be `valid:false`).
3. Start backend `:8080`, frontend `:5173`.
4. Login via platform admin (provisioned via `PLATFORM_ADMIN_EMAIL/PASSWORD`) → create organisation → org admin user.
5. **Create an organization and organization user through the platform-admin provisioning flow, or configure a local demo account securely.** Do not hardcode demo passwords in the repo.

---

## Database / Neo4j Prerequisites

- PostgreSQL 14+ with `pgcrypto` optional (SHA-256 is app-side). Required tables: `organisations`, `users`, `roles`, `permissions`, `integrations`, `integration_sync` (`stats jsonb`), `canonical_evidence` (`UNIQUE(org_id,integration_id,external_id)`, partial index `uq_evidence_org_external_manual WHERE integration_id IS NULL`), `files`, `complaint`, `investigation`, `investigation_evidence`, `investigation_check`, `investigation_note`, `investigation_decision`, `investigation_final_result`.
- Neo4j 5+ with `neo4j+s://` – driver connects via `app.neo4j.*`. Graph is derived: `MERGE (e:Evidence {orgId, stableId})`, relationships `BELONGS_TO`, `CREATED_BY`, `DERIVED_FROM` filtered by `orgId`.

---

## AI Configuration (Optional)

- `AI_INVESTIGATION_ENABLED=false` by default (`ConfigurationDefinitionSeeder`). Set via `Configurations` UI or env.
- `GEMINI_API_KEY` empty → `NoOpInvestigationRecommendationProvider` returns empty list (clearly not fake output). With key, `GeminiInvestigationRecommendationProvider` uses `GeminiRestApiClient` (`https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent`, `10s connect, 30s read` `RestTemplateConfig`).
- Recommendations are **advisory only** – never auto-create checks/decisions/finalResult, prompt truncated to 12k, `temperature 0.2`, sanitized `Bearer ***`.

---

## Security Note

- Never commit `.env`, `auth_header*.txt`, `token*.txt`, `*.log`, `uploads/`, `BOOT-INF/`, `target/`, `frontend/dist/`, `*.class`, `*.ps1` debug scripts – all ignored via `.gitignore`.
- `application.properties` contains **no real credentials** – only `${ENV_VAR}` placeholders; missing production secrets cause startup failure (no silent fallback).
- `Authorization: Bearer` is sanitized in logs (`sanitize` in `GeminiRestApiClient`, `GraphReadinessService`).
- If a secret was ever committed, rotate it (Neon password, `JWT_SECRET`, `NEO4J_PASSWORD`, `GEMINI_API_KEY`) and rewrite history (`git filter-repo` or BFG) – do not just delete the file.

---

## Intentional Limitations (not bugs)

- **No SFTP/DB connectors** – Integration `type API` only; generic Evidence Vault `GET /api/v1/evidence?limit&cursor&updatedSince`.
- **One File → One Canonical Record** – manual upload creates `MANUAL_FILE_{fileId}`, no OCR.
- **No embeddings, no S3** – local `uploads/` with `UUID_originalName`.
- **No arbitrary full-text search** – `GET /graph/evidence` only `caseId`/`actorId` + pagination; Evidence Graph filters are server-side.
- **Timeline is evidence-derived only** – `EVIDENCE_CREATED`/`EVIDENCE_UPDATED` from `sourceCreatedAt`/`sourceUpdatedAt`; no audit-log, no `LOGIN`/`RECORD_EXPORTED` unless upstream provides structured history. Notes never enter timeline.
- **No automatic AI checks** – AI is optional, bounded (`≤100 evidence`), validated, and never mutates investigation.
- **Synchronous sync** – `POST /integrations/{id}/sync` runs inline; `stats` is `jsonb` (`@JdbcTypeCode(JSON)`).

---

## Demo Credentials

Do not hardcode demo passwords in the repo. Create an organization and organization user through the platform-admin provisioning flow, or configure a local demo account securely via `.env` (`PLATFORM_ADMIN_EMAIL`, `PROVISIONING_INITIAL_PASSWORD`).

---

## Useful Scripts

- `mvn package -o -DskipTests` – backend build
- `npm run build` – frontend build (`tsc -b && vite build`)
- `GET /api/graph/validation` – graph readiness (`valid`, `evidenceCount`)
- `POST /api/integrations/{id}/sync` – evidence sync (idempotent, hash-driven)

---

*For current data contract and canonical model see `docs/phase1-data-contract.md`, `docs/phase1-canonical-model.md`, `docs/sample-source-response.json`.*

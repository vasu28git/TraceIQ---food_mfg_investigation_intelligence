# TaceIQ Phase 1 – Canonical PostgreSQL Model (Design Only)
> `docs/phase1-canonical-model.md` – Design review, **no migration applied, no JPA entities created, no Java/TS change**. Source remains `Generic Enterprise Evidence Vault (PROPOSED)` `docs/sample-source-response.json`.

---

## 1. Design Goals
- **Tenant-safe by construction**: every row has `org_id`, every query filters `org_id`, `externalId` unique at `org_id + integration_id` scope – mirrors `IntegrationService.findByIdAndOrganisationOrgId:90` and `AuthorizationService.getCurrentOrgId:44`.
- **Idempotent sync**: repeated extracts never create duplicates; hash drives skip vs update.
- **Minimal yet graph-ready**: store just enough normalized columns for indexing + `normalized_payload JSONB` for lossless future fields, plus relationship refs (`case_id`, `actor_id`, `parent_id`) for Neo4j.
- **Non-destructive evolution**: additive columns (`deleted_at`, `is_deleted`) can be added without backfill; `ddl-auto=none` `application.properties:18` – DDL is review-only.
- **Operational visibility**: `integration_sync` captures counts and errors without bloating with per-record logs.
- **No speculation**: only fields present in `docs/sample-source-response.json:73-96` (`evidenceId`, `caseId`, `title`, `sourceType`, `status`, `createdAt/updatedAt`, `actor.actorId`, `parentEvidenceId`, `attributes.*`) are mapped; anything else **UNKNOWN**.

---

## 2. integration_sync Table (sync lifecycle)

### 2.1 Purpose
One row per sync attempt for an `Integration` (per `org_id`). State machine `PENDING→RUNNING→SUCCESS/FAILED/PARTIAL` (`phase1-data-contract.md:D1`). Separate from `Integration.status` (`ACTIVE/DRAFT` `IntegrationService:25`).

### 2.2 Columns – final proposed

| Column | Type | Null | Default | Status | Notes |
|---|---|---|---|---|---|
| `id` | `BIGSERIAL` | NOT NULL | auto | **CONFIRMED** PK pattern (`User.id`, `Integration.id` IDENTITY) – use `BIGSERIAL`/`BIGINT GENERATED ALWAYS AS IDENTITY` consistent |
| `org_id` | `BIGINT` | NOT NULL | – | **CONFIRMED** tenant – FK `organisations.org_id` `Organisation.java:22` |
| `integration_id` | `BIGINT` | NOT NULL | – | **CONFIRMED** source – FK `integrations.id` `Integration.java:21` |
| `status` | `TEXT` | NOT NULL | `PENDING` | **PROPOSED** check `IN ('PENDING','RUNNING','SUCCESS','FAILED','PARTIAL')` – mirrors `VALID_STATUSES:25` but distinct domain |
| `external_cursor` | `TEXT` | NULL | – | **PROPOSED** opaque `pagination.nextCursor` `sample:59` |
| `started_at` | `TIMESTAMPTZ` | NOT NULL | `now()` | **PROPOSED** minimal operational |
| `completed_at` | `TIMESTAMPTZ` | NULL | – | **PROPOSED** null while `RUNNING` |
| `records_fetched` | `INTEGER` | NOT NULL | `0` | **PROPOSED** explicit – **why explicit not JSONB** see 2.3 |
| `records_created` | `INTEGER` | NOT NULL | `0` | **PROPOSED** |
| `records_updated` | `INTEGER` | NOT NULL | `0` | **PROPOSED** |
| `records_skipped` | `INTEGER` | NOT NULL | `0` | **PROPOSED** hash-unchanged dedup |
| `records_failed` | `INTEGER` | NOT NULL | `0` | **PROPOSED** per-record failures (counts toward `PARTIAL`) |
| `error_summary` | `TEXT` | NULL | – | **PROPOSED** last error, not per-record; no secrets |
| `retry_count` | `INTEGER` | NOT NULL | `0` | **PROPOSED** minimal retry info; `next_retry_at TIMESTAMPTZ NULL` optional – **PROPOSED** defer if not needed |

**Stats JSONB decision (2.3)**: keep `stats JSONB NULL` **optional** for extensibility (`{totalCount, rateLimitRemaining}`) but **not primary** – explicit `records_*` columns allow `WHERE records_failed>0` indexing and dashboard without JSONB query. Reuses `D1` minimal useful principle.

### 2.3 Constraints / FKs / Indexes
- **PK**: `PRIMARY KEY (id)` – **CONFIRMED** surrogate internal identity, not external.
- **FKs**: `org_id → organisations.org_id ON DELETE CASCADE` (cascade mirrors `Organisation.integrations cascade ALL:55`); `integration_id → integrations.id ON DELETE CASCADE` – **CONFIRMED** tenant cascade.
- **Tenant check** (application-level, DDL `CHECK` hard to cross-table): enforce in service that `integrations.org_id = integration_sync.org_id` – same guard as `IntegrationService.getIntegrationById(id,orgId):90`.
- **Indexes**: `CREATE INDEX idx_sync_org_integration ON integration_sync(org_id, integration_id, started_at DESC)` for list; `INDEX idx_sync_status ON integration_sync(status) WHERE status IN ('PENDING','RUNNING')` for scheduler.
- **Status check**: `CHECK (status IN ('PENDING','RUNNING','SUCCESS','FAILED','PARTIAL'))`.

---

## 3. canonical_evidence Table (normalized evidence)

### 3.1 Purpose
PostgreSQL source of truth for evidence records from `GET /api/v1/evidence` `sample:24`. One row per `evidenceId` per `org_id` per `integration_id`. Feeds future Neo4j projection.

### 3.2 Columns – minimum required from sample (no invented fields)

| Column | Type | Null | Default | Status | Source field (`sample:73`) |
|---|---|---|---|---|---|
| `id` | `BIGSERIAL` | NOT NULL | auto | **CONFIRMED** surrogate PK (`Integration.id` pattern) – **Neo4j stable identity is `external_id` not `id`** |
| `org_id` | `BIGINT` | NOT NULL | – | **CONFIRMED** tenant FK `organisations.org_id` |
| `integration_id` | `BIGINT` | NOT NULL | – | **CONFIRMED** FK `integrations.id` provenance |
| `sync_id` | `BIGINT` | NULL | – | **PROPOSED** last writer FK `integration_sync.id ON DELETE SET NULL` – audit which sync touched row |
| `external_id` | `TEXT` | NOT NULL | – | **PROPOSED/CONFIRMED** `evidenceId ev_001:75` stable dedup key – see 6 |
| `case_id` | `TEXT` | NULL | – | **PROPOSED** `caseId CASE-1001:76` relationship to Case – not nullable if vendor requires, but sample allows, so NULL |
| `actor_id` | `TEXT` | NULL | – | **PROPOSED** `actor.actorId actor_42:83` – not `users.id` FK (external actor), keep TEXT |
| `parent_id` | `TEXT` | NULL | – | **PROPOSED** `parentEvidenceId ev_001:89` self-ref externalId, not internal `id` FK |
| `title` | `TEXT` | NULL | – | **PROPOSED** `title:77` ANONYMIZED – present but not required `recordStructure.requiredFields:131` |
| `source_type` | `TEXT` | NULL | – | **PROPOSED** `sourceType FILE/API:78` maps to `File.sourceType:31` check list – reuse vocab but column allow custom |
| `status` | `TEXT` | NULL | – | **PROPOSED** `status READY:79` maps to `VALID_STATUSES:25` but do not enforce same check – evidence status UNKNOWN domain, keep free-text with optional check |
| `source_created_at` | `TIMESTAMPTZ` | NULL | – | **PROPOSED** `createdAt 2026-09-01T08:12:33Z:80` – external time, nullable if vendor omits |
| `source_updated_at` | `TIMESTAMPTZ` | NULL | – | **PROPOSED** `updatedAt 2026-09-03T10:15:00Z:81` – driver for `updatedSince:45` |
| `content_hash` | `TEXT` | NOT NULL | – | **PROPOSED** SHA-256 of canonical `normalized_payload` for hash-unchanged skip – see 7 |
| `normalized_payload` | `JSONB` | NOT NULL | `'{}'` | **PROPOSED** lossless copy of normalized record (includes `attributes.size/contentType/storageRef/tags:91` + `relationships`); allows future field addition without DDL |
| `first_seen_at` | `TIMESTAMPTZ` | NOT NULL | `now()` | **PROPOSED** TaceIQ ingest time – mirrors `File.receivedAt:49` `Integration.createdAt:46` |
| `last_seen_at` | `TIMESTAMPTZ` | NOT NULL | `now()` | **PROPOSED** last sync time – for `updatedSince` watermark |
| `created_at` | `TIMESTAMPTZ` | NOT NULL | `now()` | **CONFIRMED** `Integration.created_at:46 @PrePersist` pattern |
| `updated_at` | `TIMESTAMPTZ` | NOT NULL | `now()` | **CONFIRMED** `Integration.updated_at:49 @PreUpdate` |
| `deleted_at` | `TIMESTAMPTZ` | NULL | – | **PROPOSED extensible** – not used until tombstone known (see 9) |
| `is_deleted` | `BOOLEAN` | NOT NULL | `false` | **PROPOSED extensible** – flag for soft-delete; default false |

**NOT included** (prevent invention):
- `description/content` – **UNKNOWN** – sample has no `description` field (`sample:73` only `title`); do not add `description TEXT` until vendor provides. Use `normalized_payload->>'title'` instead.
- `evidence_type` – **UNKNOWN** – sample `sourceType` covers channel, no separate `evidenceType`; avoid duplicate column.
- `storageRef` as column – keep inside `normalized_payload` only; **UNKNOWN** whether to index – see `raw staging` decision C2.

### 3.3 Constraints / FKs / Indexes

- **PK**: `PRIMARY KEY (id)` surrogate.
- **FKs**: `org_id → organisations.org_id CASCADE`, `integration_id → integrations.id CASCADE`, `sync_id → integration_sync.id SET NULL` (preserve evidence if sync row deleted).
- **Unique**: `UNIQUE (org_id, integration_id, external_id)` – **critical tenant rule see 6**.
- **Tenant isolation check**: same as `2.3` – service must assert `integrations.org_id = canonical.org_id`; DDL cannot enforce cross-FK equality, so add comment `CHECK` placeholder or trigger later (PROPOSED defer).
- **Indexes**:
  - `CREATE UNIQUE INDEX uq_ev_org_integ_ext ON canonical_evidence(org_id, integration_id, external_id)` (enforces unique).
  - `INDEX idx_ev_org_integ_case ON canonical_evidence(org_id, case_id)` for `?caseId` filter & future Case edge.
  - `INDEX idx_ev_org_integ_actor ON canonical_evidence(org_id, actor_id)` for actor edge.
  - `INDEX idx_ev_org_integ_parent ON canonical_evidence(org_id, parent_id)` self hierarchy.
  - `INDEX idx_ev_org_updated ON canonical_evidence(org_id, integration_id, source_updated_at DESC)` for `updatedSince` cursor.
  - `INDEX idx_ev_org_first_seen ON canonical_evidence(org_id, first_seen_at)` for retention `EVIDENCE_RETENTION_DAYS:36`.
  - `GIN INDEX idx_ev_payload ON canonical_evidence USING GIN (normalized_payload jsonb_path_ops)` optional **PROPOSED** if query on `tags` needed – defer if not needed initially.
- **Checks**: `CHECK (external_id <> '')`, `CHECK (content_hash <> '')`.

---

## 4. Constraints (summary)
- `integration_sync.status IN ('PENDING','RUNNING','SUCCESS','FAILED','PARTIAL')`.
- `canonical_evidence.external_id NOT NULL, content_hash NOT NULL`.
- `UNIQUE(org_id, integration_id, external_id)` on canonical.
- FKs `CASCADE` for org/integration as `Organisation.java:55` pattern.
- No `CHECK` inventing status beyond sample `READY` – keep `status TEXT` free until vendor confirms enum.

---

## 5. Indexes (summary)
- `integration_sync`: `(org_id, integration_id, started_at DESC)` + partial `status IN (PENDING,RUNNING)`.
- `canonical_evidence`: unique `(org_id,integration_id,external_id)` + `case_id`, `actor_id`, `parent_id`, `source_updated_at DESC`, `first_seen_at`, optional `GIN normalized_payload`.

---

## 6. Tenant Isolation

**Rules – must be enforced in service (mirrors `IntegrationService:69,90`):**
1. Every `integration_sync` row has `org_id`, every `canonical_evidence` row has `org_id`.
2. Every `canonical_evidence.integration_id` must point to an `integrations` row whose `org_id` equals `canonical_evidence.org_id` – application check on insert/update (like `createIntegration forces org:81`).
3. Every sync’s `org_id` must equal its `integration_id`’s org.
4. Future Neo4j projection includes `orgId` as property on **every** node/edge (`phase1-data-contract.md:E1`) – enables `MATCH (n {orgId:$orgId})` no cross-tenant.
5. Queries always `WHERE org_id = :currentOrgId` (`AuthorizationService.getCurrentOrgId:44` blocking `PLATFORM_ADMIN:47`).

**Why `UNIQUE(org_id, integration_id, external_id)`:**
- `external_id` (`evidenceId ev_001`) is **only stable within a source’s namespace**, not globally – `ev_001` could repeat across two integrations or two vendor exports (`sample:75` no global guarantee). `(external_id)` alone would collide.
- `(org_id, external_id)` alone would collide if org connects two integrations to same vendor (e.g., two Evidence Vault connections).
- Triple guarantees **tenant + source isolation** while allowing same externalId to reappear in another org (multi-tenant SaaS) without conflict – mirrors `Integration` name unique per org `existsByNameAndOrganisationOrgId:72` (per-org scope) and `Configuration unique(org_id,definition_id):9` (per-org-entity scope).

---

## 7. Idempotency Strategy

**Contract (no code yet):**

- **Canonical compute**: on fetch, normalize record (trim strings, uppercase enums if `status`, parse `createdAt/updatedAt` ISO8601 to `Instant` as `File.onCreate:59`) then `content_hash = SHA-256(normalized_payload::text)` (or SHA-256 of sorted keys to avoid field order).

- **Same externalId + same hash → skip** (`records_skipped++`): `SELECT ... WHERE org_id=:o AND integration_id=:i AND external_id=:e AND content_hash=:h` exists → no `UPDATE`, only `last_seen_at = now()` maybe bump without `updated_at` churn.

- **Same externalId + different hash → update** (`records_updated++`): `UPDATE canonical_evidence SET normalized_payload=:p, content_hash=:h, case_id=:c, actor_id=:a, parent_id=:par, title=:t, source_type=:st, status=:s, source_updated_at=:u, last_seen_at=now(), updated_at=now(), sync_id=:syncId WHERE org_id=:o AND integration_id=:i AND external_id=:e`.

- **New externalId → insert** (`records_created++`): `INSERT ... ON CONFLICT (org_id,integration_id,external_id) DO NOTHING` then if inserted else fallback to update path – handles race between two concurrent syncs for same org/integration.

- **Repeat sync watermark**: incremental `?updatedSince= max(source_updated_at) or max(last_seen_at)` from last `SUCCESS` sync’s `completed_at`; if vendor has no `updatedSince`, fall back to full scan with hash skip.

- **Conflict handling**: DB unique + `ON CONFLICT` is safety net for `409` semantics already used for `Integration` name `72`; not relying on app lock.

---

## 8. Sync Lifecycle

```
Integration.type/configuration set → POST /{id}/test 200:71
  → create integration_sync status=PENDING org_id, integration_id, started_at=now(), records_*=0
  → worker picks PENDING → status=RUNNING, loop:
        GET /api/v1/evidence?limit=&cursor=&updatedSince=  (sample:40,49)
        for each page { nextCursor, hasMore }
          for each record → normalize → idempotency check (7) → insert/update/skip/failed
          update integration_sync (external_cursor=nextCursor, records_fetched+=n)
        on 429/5xx retryable (sample:142 Retry-After) keep RUNNING, retry_count++
        on 401/400 non-retryable → status=FAILED, error_summary=..., completed_at=now()
  → after last page hasMore=false → status=SUCCESS or PARTIAL if records_failed>0 but some succeeded
  → completed_at=now(), records_* finalized
```

`PARTIAL` example: `900 created, 100 failed (bad parent_id)` – still stores 900, surfaces via `integrationsPage status badge` and `error_summary` without failing whole sync – mirrors `D1` definition.

---

## 9. Deletion / Tombstone Handling (extensible, not implemented)

**Current UNKNOWN** `sample:137 deleteTombstoneBehavior UNKNOWN` – sample has no `deletedAt`/`isDeleted`.

**Model remains extensible without premature implementation:**
- Columns `deleted_at TIMESTAMPTZ NULL` and `is_deleted BOOLEAN NOT NULL DEFAULT false` already **PROPOSED** in `canonical_evidence` `3.2` but **not active** – queries default `WHERE is_deleted=false` can be added later without migration breaking existing rows (all `false`).
- **Options (vendor to confirm, do NOT pick now):**
  - `a) deletedAt timestamp` – source sends `deletedAt` – set `is_deleted=true, deleted_at=deletedAt`.
  - `b) status='DELETED'` – map `status` to tombstone – same columns, `status` already exists.
  - `c) Separate /deleted endpoint` – requires additional fetch – schedule separate sync.
- **Neo4j readiness**: `is_deleted` allows future `DETACH DELETE` or `SET n:Deleted` without deleting PostgreSQL row (audit). `EVIDENCE_RETENTION_DAYS:36` `INTEGER` can later drive hard purge job.

No `ON DELETE CASCADE` from `Integration` to canonical yet preserves audit if integration deleted – choice **PROPOSED defer** (currently `ON DELETE CASCADE` suggested in `3.3` for hard delete; can switch to `SET NULL` if retention requires).

---

## 10. PostgreSQL → Neo4j Readiness

Proposed canonical contains all seven minima `phase1-data-contract.md:F5, E1`:

| Required | Covered by canonical_evidence | Status |
|---|---|---|
| stable internal identity | `id BIGSERIAL` PK | **CONFIRMED PK** |
| stable external identity | `external_id TEXT` unique triple | **PROPOSED** `ev_001:75` |
| organisation identity | `org_id` | **CONFIRMED** `Integration.organisation org_id NOT NULL:25` |
| integration/source identity | `integration_id` + `sync_id` | **CONFIRMED/PROPOSED** provenance |
| relationships/references | `case_id, actor_id, parent_id` TEXT | **PROPOSED** from `sample:83,89` |
| source timestamps | `source_created_at, source_updated_at, first_seen_at, last_seen_at` | **PROPOSED** ISO8601 parse |
| normalized properties | `normalized_payload JSONB` + extracted columns `title, source_type, status, size...` | **PROPOSED** lossless |

Future projection query example (not executed):
```sql
SELECT org_id, integration_id, external_id, case_id, actor_id, parent_id,
       normalized_payload, source_updated_at
FROM canonical_evidence WHERE org_id=:o AND is_deleted=false;
```
→ `MERGE (e:Evidence {orgId: row.org_id, stableId: row.external_id}) SET e += payload`.

No labels/relationship types finalized – `Evidence` placeholder, `BELONGS_TO` etc. remain **UNKNOWN** `H mapping table`.

---

## 11. CONFIRMED / PROPOSED / UNKNOWN Decisions

- **CONFIRMED**: reuse `org_id` FK pattern, `BIGSERIAL` PK, `TIMESTAMPTZ` `Instant`, `normalized_payload JSONB`, `71 perms`/`8 configs` unchanged `PermissionSeeder:21`, `ddl-auto=none` so design-only.
- **PROPOSED**: all new tables/columns above including `integration_sync` 12 columns, `canonical_evidence` 18 columns, `content_hash` SHA-256, triple unique, five indexes, cursor `TEXT`, `PARTIAL` state, soft `is_deleted` stub.
- **UNKNOWN**: vendor name/host, pagination `totalCount`, offset vs cursor, filter names beyond `limit/cursor/updatedSince`, exact enums for `status/sourceType`, whether `caseId`/`actorId` are FKs to separate tables vs denormalized TEXT, hard-delete vs tombstone, rate-limit headers, vault for `configuration`, whether one `canonical_evidence` suffices vs split `canonical_case` table.

---

## 12. Proposed DDL – Review Only (Non-Destructive)

```sql
-- =====================================================================
-- TaceIQ Phase 1 Canonical Model – PROPOSED DDL (DO NOT EXECUTE)
-- Review only. No migration file, no JPA entities yet.
-- Requires PostgreSQL 14+ with pgcrypto for hash (or app-side SHA-256).
-- =====================================================================

-- integration_sync: one row per sync attempt
CREATE TABLE IF NOT EXISTS integration_sync (
    id                BIGSERIAL PRIMARY KEY,
    org_id            BIGINT NOT NULL REFERENCES organisations(org_id) ON DELETE CASCADE,
    integration_id    BIGINT NOT NULL REFERENCES integrations(id) ON DELETE CASCADE,
    status            TEXT NOT NULL DEFAULT 'PENDING'
                      CHECK (status IN ('PENDING','RUNNING','SUCCESS','FAILED','PARTIAL')),
    external_cursor   TEXT,
    started_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at      TIMESTAMPTZ,
    records_fetched   INTEGER NOT NULL DEFAULT 0 CHECK (records_fetched >= 0),
    records_created   INTEGER NOT NULL DEFAULT 0 CHECK (records_created >= 0),
    records_updated   INTEGER NOT NULL DEFAULT 0 CHECK (records_updated >= 0),
    records_skipped   INTEGER NOT NULL DEFAULT 0 CHECK (records_skipped >= 0),
    records_failed    INTEGER NOT NULL DEFAULT 0 CHECK (records_failed >= 0),
    error_summary     TEXT,
    retry_count       INTEGER NOT NULL DEFAULT 0 CHECK (retry_count >= 0),
    stats             JSONB, -- optional extensible {totalCount, rateLimitRemaining}
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_sync_org_integ_started ON integration_sync(org_id, integration_id, started_at DESC);
CREATE INDEX IF NOT EXISTS idx_sync_org_status ON integration_sync(org_id, status) WHERE status IN ('PENDING','RUNNING');
COMMENT ON TABLE integration_sync IS 'PROPOSED sync lifecycle PENDING→RUNNING→SUCCESS/FAILED/PARTIAL, tenant-isolated. No code yet.';

-- canonical_evidence: normalized evidence, source of truth for Neo4j
CREATE TABLE IF NOT EXISTS canonical_evidence (
    id                 BIGSERIAL PRIMARY KEY,
    org_id             BIGINT NOT NULL REFERENCES organisations(org_id) ON DELETE CASCADE,
    integration_id     BIGINT NOT NULL REFERENCES integrations(id) ON DELETE CASCADE,
    sync_id            BIGINT REFERENCES integration_sync(id) ON DELETE SET NULL,
    external_id        TEXT NOT NULL CHECK (external_id <> ''),
    case_id            TEXT,
    actor_id           TEXT,
    parent_id          TEXT, -- stores parentEvidenceId external_id, self-ref
    title              TEXT,
    source_type        TEXT, -- e.g. FILE/API, keep free-text until vendor enum confirmed
    status             TEXT,
    source_created_at  TIMESTAMPTZ,
    source_updated_at  TIMESTAMPTZ,
    content_hash       TEXT NOT NULL CHECK (content_hash <> ''),
    normalized_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    first_seen_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at         TIMESTAMPTZ, -- PROPOSED extensible, UNKNOWN tombstone
    is_deleted         BOOLEAN NOT NULL DEFAULT false,
    CONSTRAINT uq_evidence_org_integ_external UNIQUE (org_id, integration_id, external_id)
);
CREATE INDEX IF NOT EXISTS idx_ev_org_case ON canonical_evidence(org_id, case_id) WHERE case_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_ev_org_actor ON canonical_evidence(org_id, actor_id) WHERE actor_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_ev_org_parent ON canonical_evidence(org_id, parent_id) WHERE parent_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_ev_org_updated ON canonical_evidence(org_id, integration_id, source_updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_ev_org_first_seen ON canonical_evidence(org_id, first_seen_at);
-- Optional GIN for tags/payload search – defer unless needed
-- CREATE INDEX IF NOT EXISTS idx_ev_payload_gin ON canonical_evidence USING GIN (normalized_payload jsonb_path_ops);
COMMENT ON TABLE canonical_evidence IS 'PROPOSED canonical evidence from Generic Evidence Vault sample:2 records ev_001/ev_002. Tenant (org_id, integration_id, external_id) unique. Extensible deleted_at/is_deleted for UNKNOWN tombstone.';
```

> **WARNING: Do NOT run**. Kept as design artifact; migration file not created, `spring.jpa.hibernate.ddl-auto=none:18` remains.

---

## 13. Final Summary

**Final proposed tables**
- `integration_sync` (sync run)
- `canonical_evidence` (normalized evidence)

**Final proposed columns**
- `integration_sync`: `id, org_id, integration_id, status, external_cursor, started_at, completed_at, records_fetched/created/updated/skipped/failed, error_summary, retry_count, stats?, created_at, updated_at`
- `canonical_evidence`: `id, org_id, integration_id, sync_id, external_id, case_id, actor_id, parent_id, title, source_type, status, source_created_at, source_updated_at, content_hash, normalized_payload, first_seen_at, last_seen_at, created_at, updated_at, deleted_at, is_deleted`

**Primary keys**
- `integration_sync.id BIGSERIAL PK`
- `canonical_evidence.id BIGSERIAL PK`

**Foreign keys**
- `integration_sync.org_id → organisations.org_id CASCADE`, `integration_sync.integration_id → integrations.id CASCADE`
- `canonical_evidence.org_id → organisations.org_id CASCADE`, `canonical_evidence.integration_id → integrations.id CASCADE`, `canonical_evidence.sync_id → integration_sync.id SET NULL`

**Unique constraints**
- `canonical_evidence UNIQUE(org_id, integration_id, external_id)` (`uq_evidence_org_integ_external`) – enforces tenant+source dedup (section 6).

**Important indexes**
- `idx_sync_org_integ_started`, `idx_sync_org_status` (partial)
- `idx_ev_org_case`, `idx_ev_org_actor`, `idx_ev_org_parent`, `idx_ev_org_updated DESC`, `idx_ev_org_first_seen`, optional `GIN normalized_payload`

**Remaining UNKNOWN decisions**
- Vendor/host, pagination `totalCount`, offset vs cursor, filter names, `status/sourceType` enum closure, `case/actor` as separate tables vs TEXT, tombstone (`deletedAt` vs status), rate-limit headers, vault for `Integration.configuration`, `canonical_evidence` vs additional `canonical_case` table, `GIN` need, `parent_id` FK target (externalId vs internal `id`).

**Exact next implementation step (smallest safe coding step after design review)**
1. **No code yet – get design sign-off** on this doc + DDL (review `UNIQUE(org_id,integration_id,external_id)` and explicit `records_*` vs JSONB).
2. Then **single migration file** `V__canonical_evidence.sql` containing the two `CREATE TABLE IF NOT EXISTS` + indexes above (add-only, non-destructive, `IF NOT EXISTS` guards).
3. Next increment after migration: **create JPA entities `IntegrationSync` and `CanonicalEvidence` (fields exactly as DDL, no business logic) + Spring Data repositories with `findByOrganisationOrgId` tenant methods** – still no sync endpoint, no external HTTP, no Neo4j.

No Java/TS/permissions/config/security/Neo4j/frontend changes in this step.

---

## 14. Step 10 Update – GRAPH_READY Decision (Implemented)

> `GraphReadinessService.java:1` – `GRAPH_READY` is **service-level boolean**, not new `integration_sync.status` value.

**Why not add `GRAPH_READY` status:** Existing `CHECK (status IN ('PENDING','RUNNING','SUCCESS','FAILED','PARTIAL'))` `V20250905__canonical_persistence.sql:8` would require `ALTER TABLE ... DROP CONSTRAINT ... ADD CHECK` migration to add `GRAPH_READY`. This couples canonical PostgreSQL success with derived Neo4j state and would make `SUCCESS` ambiguous. Smallest safe is keep `SUCCESS` = canonical persisted, derive `graphReady = SUCCESS && projection && validation && count>0` in service. Future `ALTER CHECK` only if product requires column-level readiness.

**Lifecycle now:** `PENDING → RUNNING → SUCCESS → (GraphReadinessService.checkReadiness) → projection → validation → GRAPH_READY boolean` – PostgreSQL commit first, then Neo4j `MERGE` `GraphProjectionService.java:45`, no single ACID transaction, no rollback of canonical on Neo4j failure.


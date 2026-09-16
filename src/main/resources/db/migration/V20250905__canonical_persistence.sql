-- =====================================================================
-- TaceIQ Phase 1 Step 5 – Canonical Persistence Migration
-- Approved design: docs/phase1-canonical-model.md §12 (review-only DDL)
-- Non-destructive: CREATE TABLE IF NOT EXISTS + IF NOT EXISTS indexes
-- Tenant isolation: UNIQUE(org_id, integration_id, external_id)
-- Requires PostgreSQL 14+ (Neon). No destructive DROP/ALTER on existing tables.
-- =====================================================================

-- integration_sync: one row per sync attempt (PENDING→RUNNING→SUCCESS/FAILED/PARTIAL)
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
    stats             JSONB,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_sync_org_integ_started ON integration_sync(org_id, integration_id, started_at DESC);
CREATE INDEX IF NOT EXISTS idx_sync_org_status ON integration_sync(org_id, status) WHERE status IN ('PENDING','RUNNING');

-- canonical_evidence: normalized evidence, tenant-isolated, graph-ready
CREATE TABLE IF NOT EXISTS canonical_evidence (
    id                 BIGSERIAL PRIMARY KEY,
    org_id             BIGINT NOT NULL REFERENCES organisations(org_id) ON DELETE CASCADE,
    integration_id     BIGINT NOT NULL REFERENCES integrations(id) ON DELETE CASCADE,
    sync_id            BIGINT REFERENCES integration_sync(id) ON DELETE SET NULL,
    external_id        TEXT NOT NULL CHECK (external_id <> ''),
    case_id            TEXT,
    actor_id           TEXT,
    parent_id          TEXT,
    title              TEXT,
    source_type        TEXT,
    status             TEXT,
    source_created_at  TIMESTAMPTZ,
    source_updated_at  TIMESTAMPTZ,
    content_hash       TEXT NOT NULL CHECK (content_hash <> ''),
    normalized_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    first_seen_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at         TIMESTAMPTZ,
    is_deleted         BOOLEAN NOT NULL DEFAULT false,
    CONSTRAINT uq_evidence_org_integ_external UNIQUE (org_id, integration_id, external_id)
);
CREATE INDEX IF NOT EXISTS idx_ev_org_case ON canonical_evidence(org_id, case_id) WHERE case_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_ev_org_actor ON canonical_evidence(org_id, actor_id) WHERE actor_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_ev_org_parent ON canonical_evidence(org_id, parent_id) WHERE parent_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_ev_org_updated ON canonical_evidence(org_id, integration_id, source_updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_ev_org_first_seen ON canonical_evidence(org_id, first_seen_at);
-- Optional GIN index deferred
-- CREATE INDEX IF NOT EXISTS idx_ev_payload_gin ON canonical_evidence USING GIN (normalized_payload jsonb_path_ops);

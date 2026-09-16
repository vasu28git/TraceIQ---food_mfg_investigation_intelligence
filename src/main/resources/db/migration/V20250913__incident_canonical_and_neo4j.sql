-- Phase 1: incident-centric canonical evidence
-- Investigation remains the persistence table; Incident is the domain alias.
-- Adds incident_id FK to canonical_evidence for ingestion-time association.

ALTER TABLE canonical_evidence
    ADD COLUMN IF NOT EXISTS incident_id BIGINT
    REFERENCES investigation(id) ON DELETE SET NULL;

-- Index for incident-scoped queries, partial where not null (consistent with other partial indexes)
CREATE INDEX IF NOT EXISTS idx_ev_org_incident
    ON canonical_evidence (org_id, incident_id)
    WHERE incident_id IS NOT NULL;

-- Legacy evidence with NULL incident_id remains supported; NOT NULL not enforced in this phase.

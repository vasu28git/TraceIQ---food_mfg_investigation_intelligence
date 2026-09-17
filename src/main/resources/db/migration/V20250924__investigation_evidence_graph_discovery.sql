-- V20250924__investigation_evidence_graph_discovery.sql
-- Adds graph discovery metadata and authoritative review status to investigation_evidence.
-- Preserves canonical_evidence.incident_id and performs safe idempotent backfills.

ALTER TABLE investigation_evidence
    ADD COLUMN IF NOT EXISTS relevance VARCHAR(32) DEFAULT 'RELATED',
    ADD COLUMN IF NOT EXISTS distance INTEGER DEFAULT 1,
    ADD COLUMN IF NOT EXISTS discovery_method VARCHAR(64) DEFAULT 'NEO4J_GRAPH_TRAVERSAL',
    ADD COLUMN IF NOT EXISTS discovery_path JSONB DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS discovery_reason TEXT,
    ADD COLUMN IF NOT EXISTS review_status VARCHAR(32) DEFAULT 'PENDING_REVIEW',
    ADD COLUMN IF NOT EXISTS investigator_notes TEXT,
    ADD COLUMN IF NOT EXISTS reviewed_by_user_id BIGINT REFERENCES users(id),
    ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW();

-- Create indexes for efficient filtering and pagination
CREATE INDEX IF NOT EXISTS idx_inv_ev_org_inv ON investigation_evidence(org_id, investigation_id);
CREATE INDEX IF NOT EXISTS idx_inv_ev_relevance ON investigation_evidence(org_id, investigation_id, relevance);
CREATE INDEX IF NOT EXISTS idx_inv_ev_review_status ON investigation_evidence(org_id, investigation_id, review_status);

-- Safe backfill: migrate legacy canonical_evidence.incident_id into authoritative investigation_evidence
-- Explicitly marked as LEGACY_CANONICAL_INCIDENT, preserving compatibility without fabricating Neo4j paths
INSERT INTO investigation_evidence (
    org_id, investigation_id, canonical_evidence_id, created_at, updated_at,
    relevance, distance, discovery_method, discovery_path, discovery_reason, review_status
)
SELECT 
    ce.org_id, ce.incident_id, ce.id, NOW(), NOW(),
    'DIRECT', 1, 'LEGACY_CANONICAL_INCIDENT', '[]'::jsonb,
    'Direct legacy incident canonical evidence backfill', 'PENDING_REVIEW'
FROM canonical_evidence ce
WHERE ce.incident_id IS NOT NULL AND ce.is_deleted = false
ON CONFLICT (org_id, investigation_id, canonical_evidence_id) DO NOTHING;

-- Safe initial seed: copy any existing historical review assessments into authoritative investigation_evidence
UPDATE investigation_evidence ie
SET review_status = iea.review_status,
    investigator_notes = iea.investigator_notes,
    reviewed_by_user_id = iea.reviewed_by_user_id,
    reviewed_at = iea.reviewed_at,
    updated_at = iea.updated_at
FROM investigation_evidence_assessment iea
WHERE ie.org_id = iea.org_id
  AND ie.investigation_id = iea.investigation_id
  AND ie.canonical_evidence_id = iea.canonical_evidence_id;

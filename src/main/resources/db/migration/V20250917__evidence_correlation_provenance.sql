-- Tenant-scoped explanations for automatic evidence discovery.
CREATE TABLE IF NOT EXISTS evidence_correlation_provenance (
    id BIGSERIAL PRIMARY KEY,
    org_id BIGINT NOT NULL REFERENCES organisations(org_id) ON DELETE CASCADE,
    investigation_id BIGINT NOT NULL REFERENCES investigation(id) ON DELETE CASCADE,
    canonical_evidence_id BIGINT NOT NULL REFERENCES canonical_evidence(id) ON DELETE CASCADE,
    reason TEXT NOT NULL,
    matched_field TEXT,
    matched_value TEXT,
    source_record_id TEXT,
    intermediate_entity_type TEXT,
    intermediate_entity_value TEXT,
    connection_path JSONB NOT NULL DEFAULT '[]'::jsonb,
    discovered_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_corr_prov_org_investigation
    ON evidence_correlation_provenance(org_id, investigation_id);
CREATE INDEX IF NOT EXISTS idx_corr_prov_org_evidence
    ON evidence_correlation_provenance(org_id, canonical_evidence_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_corr_prov_match
    ON evidence_correlation_provenance(
        org_id, investigation_id, canonical_evidence_id, reason,
        matched_field, matched_value, source_record_id,
        intermediate_entity_type, intermediate_entity_value
    );
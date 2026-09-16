-- Manual file upload support: allow canonical_evidence without integration
-- Existing integration evidence remains with NOT NULL integration_id and unique (org_id, integration_id, external_id)
-- Manual evidence will have integration_id NULL and uses deterministic external_id per file

-- Make integration_id nullable for manual uploads
ALTER TABLE canonical_evidence ALTER COLUMN integration_id DROP NOT NULL;

-- Ensure existing unique constraint still works for integration evidence (NULLs are distinct in PG, so manual rows won't clash via existing constraint)
-- Add partial unique index for manual evidence to prevent duplicate external_id per org when integration_id IS NULL
CREATE UNIQUE INDEX IF NOT EXISTS uq_evidence_org_external_manual
    ON canonical_evidence (org_id, external_id)
    WHERE integration_id IS NULL;

-- Optional: ensure content_hash remains NOT NULL (already is)
-- No other changes needed; GraphProjectionService already handles NULL integration via findAllByOrganisationOrgId

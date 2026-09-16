ALTER TABLE investigation_finding_evidence
    ADD COLUMN IF NOT EXISTS relationship_type TEXT NOT NULL DEFAULT 'SUPPORTING';

ALTER TABLE investigation_finding_evidence
    DROP CONSTRAINT IF EXISTS investigation_finding_evidence_relationship_type_check;

ALTER TABLE investigation_finding_evidence
    ADD CONSTRAINT investigation_finding_evidence_relationship_type_check
    CHECK (relationship_type IN ('SUPPORTING','CONTRADICTING'));
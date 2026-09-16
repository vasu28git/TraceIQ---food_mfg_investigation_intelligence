ALTER TABLE investigation_finding
    ADD COLUMN IF NOT EXISTS category TEXT,
    ADD COLUMN IF NOT EXISTS confidence TEXT;

ALTER TABLE investigation_finding
    DROP CONSTRAINT IF EXISTS investigation_finding_status_check;

ALTER TABLE investigation_finding
    ADD CONSTRAINT investigation_finding_status_check
    CHECK (status IN ('OPEN','CONFIRMED','DISMISSED'));
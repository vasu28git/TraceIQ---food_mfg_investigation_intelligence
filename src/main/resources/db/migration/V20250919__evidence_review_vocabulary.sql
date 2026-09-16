ALTER TABLE investigation_evidence_assessment
    DROP CONSTRAINT IF EXISTS investigation_evidence_assessment_review_status_check;
ALTER TABLE investigation_evidence_assessment
    DROP CONSTRAINT IF EXISTS investigation_evidence_assessment_relevance_check;
ALTER TABLE investigation_evidence_assessment
    DROP CONSTRAINT IF EXISTS investigation_evidence_assessment_importance_check;
ALTER TABLE investigation_evidence_assessment
    DROP CONSTRAINT IF EXISTS investigation_evidence_assessment_assessment_check;

UPDATE investigation_evidence_assessment
SET review_status = 'PENDING_REVIEW'
WHERE review_status = 'UNREVIEWED';

ALTER TABLE investigation_evidence_assessment
    ALTER COLUMN review_status SET DEFAULT 'PENDING_REVIEW';
ALTER TABLE investigation_evidence_assessment
    ADD CONSTRAINT investigation_evidence_assessment_review_status_check
        CHECK (review_status IN ('PENDING_REVIEW','REVIEWED'));
ALTER TABLE investigation_evidence_assessment
    ADD CONSTRAINT investigation_evidence_assessment_relevance_check
        CHECK (relevance IS NULL OR relevance IN ('RELEVANT','NOT_RELEVANT'));
ALTER TABLE investigation_evidence_assessment
    ADD CONSTRAINT investigation_evidence_assessment_importance_check
        CHECK (importance IS NULL OR importance IN ('HIGH','MEDIUM','LOW'));
ALTER TABLE investigation_evidence_assessment
    ADD CONSTRAINT investigation_evidence_assessment_assessment_check
        CHECK (assessment IS NULL OR assessment IN ('SUPPORTS_INVESTIGATION','CONTRADICTS_INVESTIGATION','CONTEXT_ONLY','INCONCLUSIVE','NOT_ASSESSED'));
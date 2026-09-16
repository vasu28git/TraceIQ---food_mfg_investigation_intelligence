-- Phase 2: incident context fields for Investigation (Incident domain alias)
-- Adds optional nullable columns; existing data unaffected; no backfill; additive only.

ALTER TABLE investigation
    ADD COLUMN IF NOT EXISTS batch_reference TEXT,
    ADD COLUMN IF NOT EXISTS product_reference TEXT,
    ADD COLUMN IF NOT EXISTS order_reference TEXT,
    ADD COLUMN IF NOT EXISTS incident_start TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS incident_end TIMESTAMPTZ;

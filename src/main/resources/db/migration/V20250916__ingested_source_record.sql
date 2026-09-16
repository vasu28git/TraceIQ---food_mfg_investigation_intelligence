-- Generic tenant-scoped ingested source records for structured file ingestion
-- Supports CSV/JSON/XLSX parsed rows, tenant-isolated, correlation via batch/machine/supplier/product/order/external references
CREATE TABLE IF NOT EXISTS ingested_source_record (
    id BIGSERIAL PRIMARY KEY,
    org_id BIGINT NOT NULL REFERENCES organisations(org_id) ON DELETE CASCADE,
    file_id BIGINT REFERENCES files(id) ON DELETE SET NULL,
    source_type TEXT NOT NULL CHECK (source_type IN ('CRM','MES','LIMS','CMMS','SHIPMENT','WAREHOUSE','SOP','ERP','MANUAL_UPLOAD','OTHER')),
    source_record_id TEXT NOT NULL,
    batch_reference TEXT,
    machine_reference TEXT,
    supplier_reference TEXT,
    product_reference TEXT,
    order_reference TEXT,
    external_reference TEXT,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    ingested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_source_org_type_record UNIQUE (org_id, source_type, source_record_id)
);
CREATE INDEX IF NOT EXISTS idx_source_org_batch ON ingested_source_record(org_id, batch_reference) WHERE batch_reference IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_source_org_machine ON ingested_source_record(org_id, machine_reference) WHERE machine_reference IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_source_org_supplier ON ingested_source_record(org_id, supplier_reference) WHERE supplier_reference IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_source_org_product ON ingested_source_record(org_id, product_reference) WHERE product_reference IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_source_org_external ON ingested_source_record(org_id, external_reference) WHERE external_reference IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_source_org_type ON ingested_source_record(org_id, source_type);
CREATE INDEX IF NOT EXISTS idx_source_org_file ON ingested_source_record(org_id, file_id);

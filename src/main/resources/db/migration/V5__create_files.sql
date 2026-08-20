-- V5: Files Table
CREATE TABLE IF NOT EXISTS files (
    file_id UUID PRIMARY KEY,
    org_id UUID NOT NULL,
    integration_id UUID,
    file_name VARCHAR(255) NOT NULL,
    file_type VARCHAR(100) NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    file_size BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_files_organization FOREIGN KEY (org_id) REFERENCES organizations(org_id) ON DELETE CASCADE,
    CONSTRAINT fk_files_integration FOREIGN KEY (integration_id) REFERENCES integrations(int_id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_files_org_id ON files(org_id);
CREATE INDEX IF NOT EXISTS idx_files_integration_id ON files(integration_id);

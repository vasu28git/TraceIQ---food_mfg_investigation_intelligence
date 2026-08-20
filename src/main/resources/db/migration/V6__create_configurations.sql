-- V6: Configurations Table
CREATE TABLE IF NOT EXISTS configurations (
    config_id UUID PRIMARY KEY,
    org_id UUID NOT NULL,
    config_key VARCHAR(100) NOT NULL,
    config_value TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_configurations_organization FOREIGN KEY (org_id) REFERENCES organizations(org_id) ON DELETE CASCADE,
    CONSTRAINT uk_configurations_org_key UNIQUE (org_id, config_key)
);

CREATE INDEX IF NOT EXISTS idx_configurations_org_id ON configurations(org_id);
CREATE INDEX IF NOT EXISTS idx_configurations_org_key ON configurations(org_id, config_key);

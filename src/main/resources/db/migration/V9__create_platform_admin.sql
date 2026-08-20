-- V9: Support Platform-Level Users and Roles (Nullable org_id)
-- Allow platform-level roles without an organization
ALTER TABLE roles ALTER COLUMN org_id DROP NOT NULL;
ALTER TABLE roles DROP CONSTRAINT IF EXISTS uk_roles_org_name;
CREATE UNIQUE INDEX IF NOT EXISTS uk_roles_org_name ON roles(org_id, name) WHERE org_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_roles_platform_name ON roles(name) WHERE org_id IS NULL;

-- Allow platform-level users without an organization
ALTER TABLE users ALTER COLUMN org_id DROP NOT NULL;
ALTER TABLE users DROP CONSTRAINT IF EXISTS uk_users_org_email;
CREATE UNIQUE INDEX IF NOT EXISTS uk_users_org_email ON users(org_id, email) WHERE org_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_users_platform_email ON users(email) WHERE org_id IS NULL;

-- Allow audit logs without an organization for platform-level actions
ALTER TABLE audit_logs ALTER COLUMN org_id DROP NOT NULL;

-- Seed PLATFORM_ADMIN role
INSERT INTO roles (role_id, org_id, name, description, created_at)
VALUES ('b0000001-0000-0000-0000-000000000001', NULL, 'PLATFORM_ADMIN', 'Platform Super Administrator with system-wide access across all organizations', CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING;

-- Grant all system permissions to PLATFORM_ADMIN role
INSERT INTO role_permissions (role_id, perm_id, created_at)
SELECT 'b0000001-0000-0000-0000-000000000001', perm_id, CURRENT_TIMESTAMP
FROM permissions
ON CONFLICT DO NOTHING;

-- V8: Seed Initial System Permissions
INSERT INTO permissions (perm_id, name, description, created_at)
VALUES
    ('a0000001-0000-0000-0000-000000000001', 'USER_READ', 'Permission to view users', CURRENT_TIMESTAMP),
    ('a0000001-0000-0000-0000-000000000002', 'USER_CREATE', 'Permission to create users', CURRENT_TIMESTAMP),
    ('a0000001-0000-0000-0000-000000000003', 'USER_UPDATE', 'Permission to update user details', CURRENT_TIMESTAMP),
    ('a0000001-0000-0000-0000-000000000004', 'USER_DELETE', 'Permission to delete or suspend users', CURRENT_TIMESTAMP),

    ('a0000002-0000-0000-0000-000000000001', 'ROLE_READ', 'Permission to view roles', CURRENT_TIMESTAMP),
    ('a0000002-0000-0000-0000-000000000002', 'ROLE_CREATE', 'Permission to create new custom roles', CURRENT_TIMESTAMP),
    ('a0000002-0000-0000-0000-000000000003', 'ROLE_UPDATE', 'Permission to update roles', CURRENT_TIMESTAMP),
    ('a0000002-0000-0000-0000-000000000004', 'ROLE_DELETE', 'Permission to delete roles', CURRENT_TIMESTAMP),

    ('a0000003-0000-0000-0000-000000000001', 'PERMISSION_READ', 'Permission to view system permissions', CURRENT_TIMESTAMP),
    ('a0000003-0000-0000-0000-000000000002', 'PERMISSION_ASSIGN', 'Permission to assign permissions to roles', CURRENT_TIMESTAMP),

    ('a0000004-0000-0000-0000-000000000001', 'INTEGRATION_READ', 'Permission to view integrations', CURRENT_TIMESTAMP),
    ('a0000004-0000-0000-0000-000000000002', 'INTEGRATION_CREATE', 'Permission to register new integrations', CURRENT_TIMESTAMP),
    ('a0000004-0000-0000-0000-000000000003', 'INTEGRATION_UPDATE', 'Permission to update integration configurations', CURRENT_TIMESTAMP),
    ('a0000004-0000-0000-0000-000000000004', 'INTEGRATION_DELETE', 'Permission to delete integrations', CURRENT_TIMESTAMP),

    ('a0000005-0000-0000-0000-000000000001', 'FILE_READ', 'Permission to view and read file metadata', CURRENT_TIMESTAMP),
    ('a0000005-0000-0000-0000-000000000002', 'FILE_UPLOAD', 'Permission to upload file records', CURRENT_TIMESTAMP),
    ('a0000005-0000-0000-0000-000000000003', 'FILE_DELETE', 'Permission to delete file records', CURRENT_TIMESTAMP),

    ('a0000006-0000-0000-0000-000000000001', 'CONFIGURATION_READ', 'Permission to read organization configurations', CURRENT_TIMESTAMP),
    ('a0000006-0000-0000-0000-000000000002', 'CONFIGURATION_UPDATE', 'Permission to update organization configurations', CURRENT_TIMESTAMP),

    ('a0000007-0000-0000-0000-000000000001', 'AUDIT_READ', 'Permission to inspect audit logs', CURRENT_TIMESTAMP)
ON CONFLICT (name) DO NOTHING;

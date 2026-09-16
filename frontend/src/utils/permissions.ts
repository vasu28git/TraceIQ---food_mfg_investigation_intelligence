// Permission-aware helpers – frontend is convenience only, backend is authoritative (src/main/java/com/taceiq/security/AuthorizationService.java:118)
import { useAuthStore } from '../store/authStore'

/**
 * All permission aliases as defined in AuthorizationService. Keep in sync with backend.
 */
export const PERMISSIONS = {
  USER_READ: ['USER_READ', 'READ_USER', 'MANAGE_USERS', 'MANAGE_USER', 'USER_MANAGE', 'USERS_READ', 'USER_CREATE', 'CREATE_USER'],
  USER_CREATE: ['USER_CREATE', 'CREATE_USER', 'MANAGE_USERS', 'MANAGE_USER', 'USER_MANAGE', 'USERS_MANAGE'],
  USER_UPDATE: ['USER_UPDATE', 'UPDATE_USER', 'MANAGE_USERS', 'MANAGE_USER', 'USER_MANAGE', 'USER_CREATE', 'CREATE_USER'],
  USER_DELETE: ['USER_DELETE', 'DELETE_USER', 'MANAGE_USERS', 'MANAGE_USER', 'USER_MANAGE'],
  ROLE_READ: ['ROLE_READ', 'READ_ROLE', 'MANAGE_ROLES', 'MANAGE_ROLE', 'ROLE_MANAGE', 'ROLES_READ', 'ROLE_CREATE', 'CREATE_ROLE'],
  ROLE_CREATE: ['ROLE_CREATE', 'CREATE_ROLE', 'MANAGE_ROLES', 'MANAGE_ROLE', 'ROLE_MANAGE', 'ROLES_MANAGE'],
  ROLE_UPDATE: ['ROLE_UPDATE', 'UPDATE_ROLE', 'MANAGE_ROLES', 'MANAGE_ROLE', 'ROLE_MANAGE'],
  ROLE_DELETE: ['ROLE_DELETE', 'DELETE_ROLE', 'MANAGE_ROLES', 'MANAGE_ROLE', 'ROLE_MANAGE'],
  PERMISSION_READ: ['PERMISSION_READ', 'READ_PERMISSION', 'MANAGE_PERMISSIONS', 'MANAGE_PERMISSION', 'PERMISSION_MANAGE', 'MANAGE_ROLES', 'ROLE_READ', 'READ_ROLE'],
  PERMISSION_ASSIGN: ['PERMISSION_ASSIGN', 'ASSIGN_PERMISSION', 'MANAGE_ROLES', 'MANAGE_ROLE', 'MANAGE_PERMISSIONS', 'MANAGE_PERMISSION', 'PERMISSION_MANAGE', 'ROLE_MANAGE'],
  CONFIG_READ: ['CONFIG_READ', 'READ_CONFIGURATION', 'READ_CONFIG', 'MANAGE_CONFIGURATIONS', 'MANAGE_CONFIGURATION', 'CONFIGURATION_READ', 'CONFIG_CREATE', 'CREATE_CONFIGURATION'],
  CONFIG_CREATE: ['CONFIG_CREATE', 'CREATE_CONFIGURATION', 'CREATE_CONFIG', 'MANAGE_CONFIGURATIONS', 'MANAGE_CONFIGURATION', 'CONFIGURATION_MANAGE', 'CONFIG_MANAGE'],
  CONFIG_UPDATE: ['CONFIG_UPDATE', 'UPDATE_CONFIGURATION', 'UPDATE_CONFIG', 'MANAGE_CONFIGURATIONS', 'MANAGE_CONFIGURATION', 'CONFIGURATION_MANAGE'],
  CONFIG_DELETE: ['CONFIG_DELETE', 'DELETE_CONFIGURATION', 'DELETE_CONFIG', 'MANAGE_CONFIGURATIONS', 'MANAGE_CONFIGURATION'],
  INTEGRATION_READ: ['INTEGRATION_READ', 'READ_INTEGRATION', 'MANAGE_INTEGRATIONS', 'MANAGE_INTEGRATION', 'INTEGRATION_MANAGE', 'INTEGRATION_CREATE', 'CREATE_INTEGRATION'],
  INTEGRATION_CREATE: ['INTEGRATION_CREATE', 'CREATE_INTEGRATION', 'MANAGE_INTEGRATIONS', 'MANAGE_INTEGRATION', 'INTEGRATION_MANAGE'],
  INTEGRATION_UPDATE: ['INTEGRATION_UPDATE', 'UPDATE_INTEGRATION', 'MANAGE_INTEGRATIONS', 'MANAGE_INTEGRATION', 'INTEGRATION_MANAGE'],
  INTEGRATION_DELETE: ['INTEGRATION_DELETE', 'DELETE_INTEGRATION', 'MANAGE_INTEGRATIONS', 'MANAGE_INTEGRATION'],
  FILE_READ: ['FILE_READ', 'READ_FILE', 'MANAGE_FILES', 'MANAGE_FILE', 'FILE_MANAGE', 'FILE_CREATE', 'CREATE_FILE'],
  FILE_CREATE: ['FILE_CREATE', 'CREATE_FILE', 'MANAGE_FILES', 'MANAGE_FILE', 'FILE_MANAGE'],
  FILE_UPDATE: ['FILE_UPDATE', 'UPDATE_FILE', 'MANAGE_FILES', 'MANAGE_FILE', 'FILE_MANAGE'],
  FILE_DELETE: ['FILE_DELETE', 'DELETE_FILE', 'MANAGE_FILES', 'MANAGE_FILE', 'FILE_MANAGE'],
  EVIDENCE_GRAPH: ['EVIDENCE_GRAPH_ACCESS', 'EVIDENCE_ACCESS', 'INVESTIGATION_ACCESS', 'MANAGE_INVESTIGATIONS', 'INVESTIGATION_ADMIN', 'INTEGRATION_READ'],
} as const

export type PermissionGroup = keyof typeof PERMISSIONS

/** Check if current user has any of the aliases for a given group */
export function hasPermissionGroup(group: PermissionGroup): boolean {
  const { hasAnyAuthority } = useAuthStore.getState()
  return hasAnyAuthority(...PERMISSIONS[group])
}

/** React hook wrapper */
import { useAuthStore as useAuth } from '../store/authStore'
export function usePermissions() {
  const hasAuthority = useAuth((s) => s.hasAuthority)
  const hasAnyAuthority = useAuth((s) => s.hasAnyAuthority)
  const isPlatformAdmin = useAuth((s) => s.isPlatformAdmin)
  return {
    hasAuthority,
    hasAnyAuthority,
    isPlatformAdmin: isPlatformAdmin(),
    canReadUsers: hasAnyAuthority(...PERMISSIONS.USER_READ),
    canCreateUsers: hasAnyAuthority(...PERMISSIONS.USER_CREATE),
    canUpdateUsers: hasAnyAuthority(...PERMISSIONS.USER_UPDATE),
    canDeleteUsers: hasAnyAuthority(...PERMISSIONS.USER_DELETE),
    canReadRoles: hasAnyAuthority(...PERMISSIONS.ROLE_READ),
    canCreateRoles: hasAnyAuthority(...PERMISSIONS.ROLE_CREATE),
    canUpdateRoles: hasAnyAuthority(...PERMISSIONS.ROLE_UPDATE),
    canDeleteRoles: hasAnyAuthority(...PERMISSIONS.ROLE_DELETE),
    canAssignPermissions: hasAnyAuthority(...PERMISSIONS.PERMISSION_ASSIGN),
    canReadPermissions: hasAnyAuthority(...PERMISSIONS.PERMISSION_READ),
    canReadConfigs: hasAnyAuthority(...PERMISSIONS.CONFIG_READ),
    canCreateConfigs: hasAnyAuthority(...PERMISSIONS.CONFIG_CREATE),
    canUpdateConfigs: hasAnyAuthority(...PERMISSIONS.CONFIG_UPDATE),
    canDeleteConfigs: hasAnyAuthority(...PERMISSIONS.CONFIG_DELETE),
    canReadIntegrations: hasAnyAuthority(...PERMISSIONS.INTEGRATION_READ),
    canCreateIntegrations: hasAnyAuthority(...PERMISSIONS.INTEGRATION_CREATE),
    canUpdateIntegrations: hasAnyAuthority(...PERMISSIONS.INTEGRATION_UPDATE),
    canDeleteIntegrations: hasAnyAuthority(...PERMISSIONS.INTEGRATION_DELETE),
    canReadFiles: hasAnyAuthority(...PERMISSIONS.FILE_READ),
    canCreateFiles: hasAnyAuthority(...PERMISSIONS.FILE_CREATE),
    canUpdateFiles: hasAnyAuthority(...PERMISSIONS.FILE_UPDATE),
    canDeleteFiles: hasAnyAuthority(...PERMISSIONS.FILE_DELETE),
    hasPermissionGroup: (g: PermissionGroup) => hasAnyAuthority(...PERMISSIONS[g]),
  }
}

export type NavItem = {
  label: string
  to: string
  group?: PermissionGroup
  icon?: string
  /** always show regardless of perms (e.g. dashboard) */
  alwaysVisible?: boolean
}

export const ORG_NAV_ITEMS: NavItem[] = [
  { label: 'Dashboard', to: '/organisation/dashboard', alwaysVisible: true, icon: '◧' },
  { label: 'Investigations', to: '/organisation/investigations', alwaysVisible: true, icon: '▣' },
  { label: 'Complaints', to: '/organisation/complaints', alwaysVisible: true, icon: '⚑' },
  { label: 'Evidence Graph', to: '/organisation/evidence-graph', group: 'EVIDENCE_GRAPH', icon: '⬡' },
  { label: 'Users', to: '/organisation/users', group: 'USER_READ', icon: '◈' },
  { label: 'Roles & Permissions', to: '/organisation/roles', group: 'ROLE_READ', icon: '⬢' },
  { label: 'Configurations', to: '/organisation/configurations', group: 'CONFIG_READ', icon: '⚙' },
  { label: 'Integrations', to: '/organisation/integrations', group: 'INTEGRATION_READ', icon: '⇄' },
  { label: 'Files', to: '/organisation/files', group: 'FILE_READ', icon: '▤' },
]

export const PLATFORM_NAV_ITEMS: NavItem[] = [
  { label: 'Dashboard', to: '/platform/dashboard', alwaysVisible: true },
  { label: 'Organisations', to: '/platform/organisations', alwaysVisible: true },
]

export const ORG_BOTTOM_NAV: NavItem[] = [
  { label: 'Organisation Settings', to: '/organisation/settings', alwaysVisible: true, icon: '⚙' },
  { label: 'Profile', to: '/organisation/profile', alwaysVisible: true, icon: '◯' },
]

# Multi-Tenant Investigation Platform SaaS – Backend Foundation

A robust, enterprise-grade Spring Boot 3.x backend foundation for a multi-tenant Investigation Platform SaaS.

---

## 1. Architecture Overview

This project implements a two-level, layered, feature-based modular architecture designed around platform administration, multi-tenancy, strict tenant data isolation, role-based access control (RBAC), and append-only auditing.

```text
PLATFORM LEVEL
    Platform Admin (PLATFORM_ADMIN) ──► Operates across organizations (/api/v1/platform/...)
         │
         ▼
    Organizations (Tenants)
         │
         ▼
ORGANIZATION LEVEL
    Org Admin (ORG_ADMIN) ────────────► Manages tenant configuration, users, roles, integrations
    Investigator (INVESTIGATOR) ──────► Operates on cases, evidence, graphs, timelines
```

```text
Request (with Bearer JWT / X-Tenant-ID)
   │
   ▼
[TenantFilter & JwtAuthenticationFilter] ──► TenantContext (ThreadLocal<UUID>)
   │
   ▼
[REST Controllers (/api/v1/...)]
   │
   ▼
[Service Layer (@Transactional)]
   │
   ▼
[Mappers (Entity ↔ DTO)]
   │
   ▼
[Tenant-Aware Repositories]
   │
   ▼
[PostgreSQL Database (Flyway Migrations)]
```

---

## 2. Multi-Tenancy & Platform Admin Architecture

### Platform Admin vs Tenant Users
1. **Platform Admin (`PLATFORM_ADMIN`)**: Operates above individual tenants without being locked to a specific `org_id`. Can provision organizations, inspect platform health, and adjust tenant statuses across the entire system.
2. **Organization Users (`ORG_ADMIN`, `INVESTIGATOR`, `CUSTOM`)**: Strictly bound to their respective `org_id`.

### Tenant Isolation Rules
1. Every tenant-owned table (`users`, `roles`, `integrations`, `files`, `configurations`, `audit_logs`) contains an `org_id` column.
2. **Context Resolution**: `TenantFilter` and `JwtAuthenticationFilter` resolve `org_id` from JWT claims or the `X-Tenant-ID` header and store it in `TenantContext` (`ThreadLocal<UUID>`).
3. **Context Cleanup**: At the end of each HTTP request cycle, `TenantContext.clear()` is guaranteed to execute in a `finally` block.
4. **Tenant-Aware Queries**: Data access services never perform naked `findById(id)` queries without verifying matching `org_id` (`findByUserIdAndOrgId`, `findByRoleIdAndOrgId`, etc.), preventing cross-tenant data leakage.

---

## 3. Entity-Relationship (ER) Model

```text
Organization (1) ────< (N) User (N) ──── (1) Role
     │                       │                   │
     │ (1:N)                 │ (1:N)             │ (N:M)
     ├──── Role              └─► AuditLog        └─► Permission (via role_permissions)
     ├──── Integration (1) ──< (N) File
     ├──── Configuration
     ├──── File
     └──── AuditLog
```

### Table Mappings
- `organizations`: Root tenant entity (`org_id`, `name`, `domain`, `status`, `description`, timestamps).
- `roles`: Tenant-scoped and platform-level roles (`role_id`, `org_id` [nullable for platform], `name`, `description`).
- `permissions`: Global system permissions (`perm_id`, `name`, `description`).
- `role_permissions`: Join table linking `roles` and `permissions`.
- `users`: Tenant users and platform admins (`user_id`, `org_id` [nullable for platform admins], `role_id`, `name`, `email`, `password_hash`, `status`).
- `integrations`: External tool integrations (`int_id`, `org_id`, `name`, `provider`, `api_key`, `status`, `last_sync_at`).
- `files`: File and evidence metadata (`file_id`, `org_id`, `integration_id`, `file_name`, `file_type`, `storage_key`, `file_size`).
- `configurations`: Tenant key-value settings (`config_id`, `org_id`, `config_key`, `config_value`).
- `audit_logs`: Append-only audit history (`log_id`, `org_id` [nullable for platform actions], `performed_by`, `action`, `entity_type`, `entity_id`, `details`, `created_at`).

---

## 4. REST API Endpoints (`/api/v1/...`)

| Category | Method | Endpoint | Authorization | Description |
|---|---|---|---|---|
| **Platform Admin** | `POST` | `/api/v1/platform/organizations` | `PLATFORM_ADMIN` | Create organization & trigger onboarding bootstrap |
| | `GET` | `/api/v1/platform/organizations` | `PLATFORM_ADMIN` | List all organizations across platform |
| | `GET` | `/api/v1/platform/organizations/{id}` | `PLATFORM_ADMIN` | Get any organization by ID |
| | `PUT` | `/api/v1/platform/organizations/{id}` | `PLATFORM_ADMIN` | Update organization details |
| | `PATCH` | `/api/v1/platform/organizations/{id}/status` | `PLATFORM_ADMIN` | Update organization status |
| **Auth** | `POST` | `/api/v1/auth/login` | Public | Authenticate user/admin and issue JWT |
| | `POST` | `/api/v1/auth/refresh` | Public | Refresh access token |
| | `POST` | `/api/v1/auth/logout` | Authenticated | User logout |
| **Organizations** | `POST` | `/api/v1/organizations` | Public / Admin | Register organization |
| | `GET` | `/api/v1/organizations/{id}` | `ORG_ADMIN` | Get organization by ID |
| | `PUT` | `/api/v1/organizations/{id}` | `ORG_ADMIN` | Update organization |
| | `GET` | `/api/v1/organizations` | `ORG_ADMIN` | List organizations (paginated) |
| | `DELETE` | `/api/v1/organizations/{id}` | `ORG_ADMIN` | Delete organization |
| **Users** | `POST` | `/api/v1/users` | `USER_CREATE` / `ORG_ADMIN` | Create user in current tenant |
| | `GET` | `/api/v1/users/{id}` | `USER_READ` / `ORG_ADMIN` | Get user by ID |
| | `PUT` | `/api/v1/users/{id}` | `USER_UPDATE` / `ORG_ADMIN` | Update user details |
| | `DELETE` | `/api/v1/users/{id}` | `USER_DELETE` / `ORG_ADMIN` | Delete user |
| | `GET` | `/api/v1/users` | `USER_READ` / `ORG_ADMIN` | List users (paginated, filter by status) |
| **Roles** | `POST` | `/api/v1/roles` | `ROLE_CREATE` / `ORG_ADMIN` | Create custom role |
| | `GET` | `/api/v1/roles/{id}` | `ROLE_READ` / `ORG_ADMIN` | Get role by ID with permissions |
| | `PUT` | `/api/v1/roles/{id}` | `ROLE_UPDATE` / `ORG_ADMIN` | Update role |
| | `PUT` | `/api/v1/roles/{id}/permissions` | `PERMISSION_ASSIGN` / `ORG_ADMIN` | Assign permissions to role |
| | `DELETE` | `/api/v1/roles/{id}` | `ROLE_DELETE` / `ORG_ADMIN` | Delete role |
| | `GET` | `/api/v1/roles` | `ROLE_READ` / `ORG_ADMIN` | List roles (paginated) |
| **Permissions** | `GET` | `/api/v1/permissions` | `PERMISSION_READ` / `ORG_ADMIN` | List all system permissions |
| | `GET` | `/api/v1/permissions/{id}` | `PERMISSION_READ` / `ORG_ADMIN` | Get permission by ID |
| **Integrations** | `POST` | `/api/v1/integrations` | `INTEGRATION_CREATE` / `ORG_ADMIN` | Register integration |
| | `GET` | `/api/v1/integrations/{id}` | `INTEGRATION_READ` / `ORG_ADMIN` | Get integration details |
| | `PUT` | `/api/v1/integrations/{id}` | `INTEGRATION_UPDATE` / `ORG_ADMIN` | Update integration |
| | `DELETE` | `/api/v1/integrations/{id}` | `INTEGRATION_DELETE` / `ORG_ADMIN` | Delete integration |
| | `GET` | `/api/v1/integrations` | `INTEGRATION_READ` / `ORG_ADMIN` | List integrations (paginated) |
| | `POST` | `/api/v1/integrations/{id}/sync` | `INTEGRATION_UPDATE` / `ORG_ADMIN` | Trigger sync placeholder |
| **Files** | `POST` | `/api/v1/files` | `FILE_UPLOAD` / `ORG_ADMIN` | Register file metadata |
| | `GET` | `/api/v1/files/{id}` | `FILE_READ` / `ORG_ADMIN` | Get file metadata |
| | `DELETE` | `/api/v1/files/{id}` | `FILE_DELETE` / `ORG_ADMIN` | Delete file |
| | `GET` | `/api/v1/files` | `FILE_READ` / `ORG_ADMIN` | List files (paginated) |
| **Configurations** | `PUT` | `/api/v1/configurations` | `CONFIGURATION_UPDATE` / `ORG_ADMIN` | Upsert configuration key-value |
| | `GET` | `/api/v1/configurations/{key}` | `CONFIGURATION_READ` / `ORG_ADMIN` | Get configuration by key |
| | `DELETE` | `/api/v1/configurations/{key}` | `CONFIGURATION_UPDATE` / `ORG_ADMIN` | Delete configuration |
| | `GET` | `/api/v1/configurations` | `CONFIGURATION_READ` / `ORG_ADMIN` | List configurations (paginated) |
| **Audit Logs** | `GET` | `/api/v1/audit-logs/{id}` | `AUDIT_READ` / `ORG_ADMIN` | Get audit log entry |
| | `GET` | `/api/v1/audit-logs` | `AUDIT_READ` / `ORG_ADMIN` | List audit logs (paginated) |

---

## 5. Security & Sensitive Data Handling

- **Passwords & API Keys**: Passwords are encrypted with BCrypt (`PasswordEncoder`). Responses strictly return DTOs; passwords and raw integration `apiKey` values are never exposed.
- **Two-Level RBAC**: Endpoints are secured using `@PreAuthorize("hasRole('PLATFORM_ADMIN')")` for platform management, and `@PreAuthorize("hasAuthority(...) or hasRole('ORG_ADMIN')")` for tenant-scoped operations.
- **OpenAPI & Swagger**: Available at `/swagger-ui.html` and `/v3/api-docs` with JWT Bearer authentication scheme configured.

---

## 6. Running Locally

### Prerequisites
- Java 21+
- Maven 3.9+
- Docker & Docker Compose (for PostgreSQL)

### 1. Start PostgreSQL with Docker Compose
```bash
docker-compose up -d
```

### 2. Run Database Migrations & Build
```bash
./mvnw clean install
```

### 3. Run Application
```bash
./mvnw spring-boot:run
```

### 4. Access Swagger UI & Health Endpoint
- **Swagger UI**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **Actuator Health**: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)

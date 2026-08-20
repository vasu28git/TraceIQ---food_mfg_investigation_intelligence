# AI-Powered Investigation Intelligence Platform for Food Manufacturing

An AI-assisted decision-support platform that helps food safety investigators trace incidents back to their most probable root cause — by automatically collecting evidence across disconnected enterprise systems, correlating it into an evidence graph, and generating transparent, evidence-grounded explanations.

> This is not a replacement for ERP, MES, LIMS, or existing traceability software. It sits above them, consuming their data to power faster, more consistent investigations.

---

## Multi-Tenant Investigation Platform SaaS – Backend Foundation

A robust, enterprise-grade Spring Boot 3.x backend foundation for a multi-tenant Investigation Platform SaaS.

---

### 1. Architecture Overview

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

### 2. Multi-Tenancy & Platform Admin Architecture

#### Platform Admin vs Tenant Users
1. **Platform Admin (`PLATFORM_ADMIN`)**: Operates above individual tenants without being locked to a specific `org_id`. Can provision organizations, inspect platform health, and adjust tenant statuses across the entire system.
2. **Organization Users (`ORG_ADMIN`, `INVESTIGATOR`, `CUSTOM`)**: Strictly bound to their respective `org_id`.

#### Tenant Isolation Rules
1. Every tenant-owned table (`users`, `roles`, `integrations`, `files`, `configurations`, `audit_logs`) contains an `org_id` column.
2. **Context Resolution**: `TenantFilter` and `JwtAuthenticationFilter` resolve `org_id` from JWT claims or the `X-Tenant-ID` header and store it in `TenantContext` (`ThreadLocal<UUID>`).
3. **Context Cleanup**: At the end of each HTTP request cycle, `TenantContext.clear()` is guaranteed to execute in a `finally` block.
4. **Tenant-Aware Queries**: Data access services never perform naked `findById(id)` queries without verifying matching `org_id` (`findByUserIdAndOrgId`, `findByRoleIdAndOrgId`, etc.), preventing cross-tenant data leakage.

---

### 3. Tech Stack & Architecture

| Layer | Technology |
|---|---|
| Backend / Orchestration | Java 21, Spring Boot 3.x, REST APIs, Security, JWT |
| AI/ML Microservice | Python, FastAPI |
| Graph Database | Neo4j |
| Relational Database | PostgreSQL (Flyway Migrations) |
| Vector Store (RAG) | pgvector |
| LLM | Gemini API |
| Frontend | React, Cytoscape.js / react-force-graph |

---

### 4. Running Locally

#### Prerequisites
- Java 21+
- Maven 3.9+
- Docker & Docker Compose (for PostgreSQL)

#### 1. Start PostgreSQL with Docker Compose
```bash
docker-compose up -d
```

#### 2. Run Database Migrations & Build
```bash
./mvnw clean install
```

#### 3. Run Application
```bash
./mvnw spring-boot:run
```

#### 4. Access Swagger UI & Health Endpoint
- **Swagger UI**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **Actuator Health**: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)


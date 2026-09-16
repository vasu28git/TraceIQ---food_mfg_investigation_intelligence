# TaceIQ Phase 1 – Data Contract (Analysis Only)
> `docs/phase1-data-contract.md` – Inspection baseline, no code/schema changes. Neo4j / ingestion not implemented.

---

## 0. Baseline & Constraints
- Foundation already working and **must not be changed**: `src/main/java/com/taceiq/security/*` JWT/Auth, `Organisation/User/Role/Permission` tenant isolation, 71 permissions `src/main/java/com/taceiq/config/PermissionSeeder.java:21,39` (DO NOT change), 8 global `ConfigurationDefinition` `src/main/java/com/taceiq/config/ConfigurationDefinitionSeeder.java:36` (DO NOT add another config system), PostgreSQL/Neon `src/main/resources/application.properties:5`, `GlobalExceptionHandler.java:12`.
- Phase 1 path: `Org Admin adds Integration/API key → test connection → extract external API → normalize → PostgreSQL → Neo4j nodes/relationships → validate → GRAPH_READY`.

---

## A. Existing Integration Contract (CONFIRMED)

### A1. Fields currently exist
`src/main/java/com/taceiq/entity/Integration.java:11`
```java
@Entity @Table(name="integrations")
Long id @Id @GeneratedValue(IDENTITY)           // PK
Organisation organisation @ManyToOne org_id NOT NULL lazy // FK
String name @Column nullable=false              // trimmed, unique per org
String type @Column                             // free-text, see A3
String status @Column                           // free-text, see A3
String configuration @Column TEXT               // opaque credentials/config
Instant createdAt @Column created_at NOT NULL  // @PrePersist
Instant updatedAt @Column updated_at NOT NULL  // @PreUpdate
List<File> files @OneToMany mappedBy=integration // inverse
```
DB: `integrations` with `org_id` FK, no `UNIQUE(name,org_id)` DDL (`ddl-auto=none` `application.properties:18`) but enforced in service. `files.integration_id` nullable FK `File.java:27`.

No DTO – controller uses entity directly `IntegrationController.java:23`.

### A2. Credentials / Configuration representation
- **Single opaque `configuration TEXT`** `Integration.java:38`. Stored verbatim `IntegrationService.java:158 setConfiguration(updated.getConfiguration())`. Frontend `frontend/src/types/integration.ts:6 configuration?: string|null` textarea JSON-like but not parsed server-side; placeholder `{"url":"https://...","token":"***"}` `IntegrationsPage.tsx:298`.
- **Not typed, not encrypted at rest in code, not logged** `IntegrationService.java:207` comment `Do not expose configuration/secrets`. `GlobalExceptionHandler.java:50` sanitizes password. Frontend `IntegrationsPage.tsx:336` blurred `<pre>` hidden by default.
- **Validation for test only**: `testConnection:199` requires `configuration != blank && type != blank` else `400`. No URL/token validation, no reach to external system – returns `Connection successful for integration 'name' type 'type'` `208`.

### A3. Organisation belonging
- `Organisation 1:N Integration` `Organisation.java:55` `@OneToMany mappedBy=organisation cascade ALL`. Owning side `Integration.organisation` `Integration.java:24`.
- **Enforced isolation**: `IntegrationService.createIntegration(...,orgId):69` ignores client org `getReferenceById(orgId):81`; `getIntegrationById(id,orgId):90 findByIdAndOrganisationOrgId else 403/404`; `update/delete` same; `IntegrationRepository.java:12 findByOrganisationOrgId, :14 findByIdAndOrganisationOrgId`. Controller derives `orgId = authorizationService.getCurrentOrgId():24` `AuthorizationService.java:44` (blocked for `PLATFORM_ADMIN:47`).
- Naming: `existsByNameAndOrganisationOrgId:72 409` `Integration already exists with name for orgId`.

### A4. Statuses / Types (current)
`IntegrationService.java:25`
```java
VALID_STATUSES = {ACTIVE, INACTIVE, ENABLED, DISABLED, SUSPENDED, PENDING, DRAFT}
VALID_TYPES    = {API, WEBHOOK, DATABASE, FILE, SFTP, MANUAL, AUTOMATIC, CUSTOM}
```
- `validateStatus:34` strictly checks `status.trim().toUpperCase() ∈ VALID_STATUSES` else `400 Allowed: ...`.
- `validateType:43` **only blank check** – comment `allow custom types to preserve flexibility` `47`; no strict enforcement.
- Frontend mirrors `frontend/src/pages/organisation/IntegrationsPage.tsx:16-17` same constants + `Custom allowed`.
- **No lifecycle statuses for ingestion** – `PENDING` here is generic, not the `PENDING→RUNNING→SUCCESS` sync state (see D).

### A5. Existing APIs
`src/main/java/com/taceiq/controller/IntegrationController.java:14 @RequestMapping("/api/integrations")` all `@authenticated` `SecurityConfig.java:37` + `requireIntegration*` `AuthorizationService.java:174` aliases (`INTEGRATION_CREATE|CREATE_INTEGRATION|MANAGE_INTEGRATIONS...:175`):
- `POST /` `createIntegration:22 201` `requireIntegrationCreate`
- `GET /{id}` `30 200` `requireIntegrationRead`
- `GET /by-name?name=` `38 200`
- `GET /` `46 200 listByOrg`
- `PUT /{id}` `54 200` `requireIntegrationUpdate` name/type/status/configuration (org immutable `161`)
- `PATCH /{id}/status` `62 body {status}` `requireIntegrationUpdate` `validateStatus`
- `POST /{id}/test` `71 200 {message}` `requireIntegrationRead` (no credential use)
- `DELETE /{id}` `79 204` `requireIntegrationDelete` blocked if `fileRepository.findByIntegrationIdAndOrganisationOrgId != empty:226 409`

Frontend `frontend/src/services/integrationService.ts:4` `listIntegrations/getIntegration/createIntegration/updateIntegration/updateIntegrationStatus/testIntegration/deleteIntegration`.

Related confirmed pieces:
- `File` `File.java:11` `org_id NOT NULL, integration_id nullable, sourceType NOT NULL, originalName, storageKey, contentType, size, status, receivedAt` linked to `Integration.files`.
- `Configuration` global definitions `Configuration.java:9 unique(org_id,definition_id)` per-org values; 8 keys `ConfigurationDefinitionSeeder:36` `EVIDENCE_RETENTION_DAYS:30 INTEGER`, `EVIDENCE_SOURCE_SYNC_INTERVAL:3600 INTEGER`, `EVIDENCE_PROCESSING_MODE:STANDARD/STRICT/DEEP ENUM`, `TRACEABILITY_DEPTH:5 INTEGER`, `TIMELINE_ANALYSIS_ENABLED:true BOOLEAN`, `AI_INVESTIGATION_ENABLED:false BOOLEAN`, `AI_MODEL:DEFAULT ENUM`, `REPORT_DEFAULT_FORMAT:PDF ENUM`.

---

## B. External Data Contract

### B1. What we expect to receive (generic, no external API chosen)
- Transport: external system reachable via data in `Integration.configuration` (URL + credentials) + `Integration.type` hint. Raw payload unknown format (JSON/CSV/XML/binary).
- Shape: **UNKNOWN** until source is specified. Cannot assume fields: no spec for record type, keys, pagination, auth scheme, rate limits.

### B2. Currently UNKNOWN – requires decision before implementation
1. **Source identity**: Which external API/product (SAP, JIRA, SIEM, evidence store...) – vendor, base URL, auth (API key/Bearer/OAuth2/mTLS).
2. **Endpoints & pagination**: list/detail endpoints, cursor/offset, page size, total count header.
3. **Auth rotation**: token expiry, refresh flow, where secrets live (configuration TEXT vs vault).
4. **Record schema**: top-level objects, nested arrays, field names/types, nullable, enums, timestamps, IDs, file attachments.
5. **Identifier**: stable external ID per record (primary key for idempotency, FK for relationships).
6. **Incremental mechanism**: webhook, polling, `updatedSince` query, change feed, or full dump.
7. **Rate limits / quotas**: headers, backoff, retry-after.
8. **Error model**: HTTP codes, error bodies, transient vs permanent.
9. **Large payload handling**: file vs API – `File.sourceType` vs `Integration` route.
10. **Compliance**: PII, retention aligns with `EVIDENCE_RETENTION_DAYS`, audit.

**Do NOT invent** fields or external APIs. Any canonical mapping (C) must be gated on these decisions.

---

## C. Canonical TaceIQ Data Model (minimum)

Goal: `External API → normalized → PostgreSQL → Neo4j`. Clearly separate layers.

### C0. Re-use confirmed (existing code, no change)
- `Organisation` isolation key `org_id` for every row.
- `Integration` as source descriptor (keep opaque `configuration`).
- `Configuration` per-org tunables (`TRACEABILITY_DEPTH`, `EVIDENCE_PROCESSING_MODE`) – reuse, do not duplicate.
- `File` for binary evidence already `FileService.java:22` tenant-checked; can attach raw dumps if needed.
- Permissions `71` intact; new sync operations will map to existing `MANAGE_INTEGRATIONS / INTEGRATION_READ` aliases, no new permission strings without decision.
- `GlobalExceptionHandler` 400/403/409/500 handling.

### C1. PROPOSED – Minimum internal representation (new components, not yet implemented)
> Marked **PROPOSED** – subject to review, do not create tables/code in this phase.

1. **Raw ingress (optional staging) – PostgreSQL**
   - `integration_sync` table (one row per sync run) – see D – stores `integration_id FK, org_id, status PENDING/RUNNING/SUCCESS/FAILED/PARTIAL, startedAt, finishedAt, externalCursor, stats {received, normalized, skipped, failed}, errorSummary TEXT`.
   - `raw_record` (optional) `id, integration_id, org_id, sync_id, externalId TEXT, rawPayload JSONB/TEXT, receivedAt, hash` – keeps original for audit/replay; alternatively skip and normalize directly if payload small.

2. **Normalized canonical – PostgreSQL (source of truth for Neo4j)**
   - Keep normalized tables **organisation-scoped + integration-scoped + stable external IDs**:
     - `canonical_entity` (or domain-specific `evidence_record`) `id PK, org_id, integration_id, sync_id, externalId UNIQUE(org_id, integration_id, externalId), sourceType, normalizedPayload JSONB, hash, firstSeenAt, lastSeenAt, isDeleted`.
     - Extracted indexes: `canonical_attribute` or columns for searchable fields once schema known (at minimum `externalId, org_id, integration_id, updatedAt`).
   - Normalization rules **PROPOSED**: trim strings, uppercase enums, parse timestamps to `Instant`, coerce `INTEGER/BOOLEAN/ENUM` per `ConfigurationDefinition.type` validators `ConfigurationService.java:51` as reference, preserve `sourceType` from `File.sourceType` vocab (`UPLOADED/PROCESSING/...` `FileService.java:26` analogy).

3. **Transform to Neo4j** – not persisted as tables; canonical rows are projection input. Required output per row: `orgId, integrationId, syncId, externalId, labels[], properties Map, relationships [{toExternalId, type, properties}]`.

### C2. UNKNOWN / Requires decision
- Whether raw staging needed vs direct normalized write.
- Exact canonical field list (cannot define columns until external schema known – e.g., `caseId, evidenceId, actorId`?).
- How many canonical entity types (one generic vs multiple domain tables).
- JSONB vs normalized columns trade-off given `ddl-auto=none:18` – migration strategy needs decision.
- Hash/dedup algorithm for `duplicate handling` (D).

---

## D. Sync Contract (conceptual lifecycle)

### D1. States (PROPOSED, not in current `Integration.status`)
Reuse `Integration.status` for **integration enablement** (`ACTIVE/DRAFT`), but sync run needs its own state machine (e.g., `integration_sync.status`):
- `PENDING` – scheduled/queued, not started.
- `RUNNING` – extraction/normalization in progress.
- `SUCCESS` – all records fetched, normalized, validated, Neo4j projection ready.
- `FAILED` – unrecoverable error (auth, schema, DB); `errorSummary` + retryable flag.
- `PARTIAL` – some batches succeeded, some failed/skipped (e.g., 900/1000 records). Requires `stats` breakdown.

### D2. Lifecycle requirements
- **Initial sync**: `Integration` `type/configuration` set → `POST /{id}/test` `200` → trigger `integration_sync PENDING` → paginated fetch → normalize → `INSERT canonical` with `ON CONFLICT (org_id,integration_id,externalId) DO NOTHING` idempotent seed → `SUCCESS`.
- **Repeat sync**: incremental `externalCursor` (e.g., `updatedSince=lastSeenAt`) or full if no cursor. Fetch delta → upsert canonical (`lastSeenAt, hash` check) → tombstone `isDeleted` if source deleted (if API supports).
- **Duplicate handling**: dedup by `(org_id, integration_id, externalId)` + `hash` of normalized payload; skip if hash unchanged; `409` semantics exist for `Integration` name (`IntegrationService.java:72`) but not yet for data – use DB unique constraint + `ON CONFLICT DO NOTHING/UPDATE`.
- **Update handling**: if `externalId` exists and payload hash differs → `UPDATE normalizedPayload, lastSeenAt`; propagate to Neo4j via re-projection (full or delta).
- **Failure handling**: transient (429/5xx, network) → retry with backoff, keep `RUNNING` → not `FAILED` until max retries; permanent (401/403/400 schema) → `FAILED` + `errorSummary` surface via `GET /integrations` status badge (`IntegrationsPage.tsx:221`). `GlobalExceptionHandler 400/403/409/500` preserved; no secret leak `500` sanitization `50`.
- **Organisation isolation**: every sync query `WHERE org_id = :orgId` (`AuthorizationService.getCurrentOrgId:44`), `IntegrationRepository.findByIdAndOrganisationOrgId:14` pattern reused; sync rows never cross org; Neo4j projections must include `orgId` property/label for per-org subgraphs.

---

## E. Graph Contract – HIGH LEVEL ONLY (no Neo4j code)

### E1. What canonical must provide for future graph build
- **Nodes**: derived from `canonical_entity` rows. Each node needs: `stableId = externalId`, `orgId` (tenant partition), `integrationId` + `sourceType` (provenance), `labels[]` (at least one domain label, e.g., `Evidence` placeholder – specific label **UNKNOWN** until domain defined), `properties Map` (subset of normalized fields, no secrets), `timestamps firstSeenAt/lastSeenAt`.
- **Relationships**: derived from FKs inside normalized payload (e.g., `evidence -> case, actor -> evidence`). Each edge needs: `fromExternalId, toExternalId, type (e.g., RELATED_TO placeholder)`, `orgId, integrationId` on edge for isolation, optional `properties`. Cardinality **UNKNOWN** – depends on external schema.
- **Organisation isolation**: every node/relationship carries `orgId` property; queries `MATCH (n {orgId:$orgId})`. No cross-org traversals. `Platform Admin` (`SecurityConfig:36 hasAuthority PLATFORM_ADMIN`) never has graph scope; Org Admin view is org-scoped like `getIntegrationsByOrganisation:118`.
- **Stable external identifiers**: `externalId` from source is **source of truth** for dedup and idempotent merge (`MERGE (n:Entity {orgId, externalId})`). Internal `id` is surrogate PK, not used in graph.
- **Validation gates before GRAPH_READY**: existence of `canonical` rows for org, no `FAILED/PARTIAL` sync without resolution, org isolation verified (no node without `orgId`), externalId uniqueness verified per org+integration.

No specific node/relationship types invented beyond `organisation/integration` which already exist.

---

## F. Phase 1 Completion Criteria – GRAPH_READY
`GRAPH_READY` is true iff:
1. `Integration` exists for org with `type` + `configuration` and `POST /{id}/test 200` `IntegrationController:71`.
2. At least one `integration_sync` `SUCCESS` (or `PARTIAL` with documented skips) for that org/integration.
3. External data extracted and **normalized** to canonical model stored in PostgreSQL with `(org_id, integration_id, externalId)` unique, `org_id` isolated, timestamps populated.
4. Canonical projection covers **full** last sync delta (duplicates skipped via hash, updates applied, failures categorized).
5. High-level graph contract satisfied: every canonical row can be mapped to node + orgId, relationships determinable from canonical references (even if zero edges), no secrets in properties.
6. Validation passes: `count(canonical) >0`, no `RUNNING` stuck, `FAILED` only with triaged `errorSummary`, organisation isolation query returns only own org rows, 71 permissions unchanged, 8 config definitions unchanged, existing tests/build green.

NOT required in Phase 1: physical Neo4j cluster, UI graph viz, incremental scheduler, vault encryption – these are Phase 2.

---

## Appendix

### 1. Files Inspected
`src/main/java/com/taceiq/entity/Integration.java:1, File.java:1, Configuration.java:1, ConfigurationDefinition.java:1, Organisation.java:1` | `controller/IntegrationController.java:1, ConfigurationController.java:1, FileController.java:1` | `service/IntegrationService.java:1, ConfigurationService.java:1, FileService.java:1, IntegrationManagementTest.java` | `repository/IntegrationRepository.java:1, FileRepository.java:1, OrganisationRepository.java:1` | `config/PermissionSeeder.java:1, ConfigurationDefinitionSeeder.java:1` | `security/SecurityConfig.java:1, AuthorizationService.java:1, JwtService.java:1, JwtAuthenticationFilter.java:1, MustChangePasswordFilter.java:1, AuthService.java:1, AuthController.java:1` | `exception/GlobalExceptionHandler.java:1` | `resources/application.properties:1` | `frontend/src/types/integration.ts:1, services/integrationService.ts:1, pages/organisation/IntegrationsPage.tsx:1, api/client.ts:1, store/authStore.ts:1, utils/permissions.ts:1` | `pom.xml:1`

### 2. Existing Pieces We Can Reuse
- `Integration` CRUD + tenant `findByIdAndOrganisationOrgId` + `INTEGRATION_*` permissions `AuthorizationService:174` + `SecurityConfig` JWT filters.
- `File` link `integration_id` nullable `FileService:65` for raw dumps.
- `Configuration` per-org `EVIDENCE_*` tunables for sync intervals/modes (no new config system).
- `PermissionSeeder 71` + `ConfigurationDefinitionSeeder 8` idempotent runners.
- `GlobalExceptionHandler` 409/403/400 mapping, `IntegrationsPage.tsx` status/type badges and `Test connection`.

### 3. New Components That Will Eventually Be Needed (PROPOSED, not now)
- `integration_sync` + `raw_record`/`canonical_entity` PostgreSQL tables + `IntegrationSyncRepository`.
- `IntegrationSyncService` state machine PENDING→RUNNING→SUCCESS/FAILED/PARTIAL + idempotent upsert.
- `ExternalClient` adapter (per `Integration.type`) + `Normalizer` to canonical.
- `GraphProjectionService` (PostgreSQL → Neo4j MERGE) + `GraphValidator` (org isolation, unique externalId).
- Scheduler/webhook to trigger repeat sync; `File` vs API branch handling.

### 4. Unknowns / Blockers (must be decided before implementation)
- External API spec (vendor, endpoints, auth, pagination, schema, stable ID, delta mechanism, rate limits, errors) – Section B2 10 items.
- Canonical schema exact columns/labels – cannot finalize DDL.
- Raw staging required? JSONB vs columns.
- Secret storage for `configuration` (plaintext TEXT today) – vault vs encrypted column.
- Sync frequency & `EVIDENCE_SOURCE_SYNC_INTERVAL=3600` reuse vs new interval.
- Whether one generic canonical table suffices or multiple domain tables needed.
- Neo4j tenancy model (property `orgId` vs separate DB) – impacts Graph Contract.
- Failure retry policy (max retries, backoff).

### 5. Recommended Next Implementation Step
**Decide external source + obtain sample payload & auth spec** – even one anonymized JSON page + auth method + stable ID field + updatedSince param. Then, without code change, finalize canonical column list and create a non-destructive migration design for `integration_sync`/`canonical_entity` (unique `(org_id,integration_id,externalId)`, `hash` column). Next code step after approval: add `integration_sync` entity + `POST /integrations/{id}/sync` `PENDING` endpoint reusing `requireIntegrationCreate` + tenant check, no Neo4j yet.

---

## G. First Integration Source (Step 2 – Evidence-Based Resolution)

> **Result: NONE FOUND – No concrete external integration/source exists in this repository.** All subsections below are therefore predominantly `UNKNOWN` and explicitly documented as missing. No endpoints/payloads invented.

### G.A. Integration Source
| Item | Status | Evidence |
|------|--------|----------|
| **Source name** | **UNKNOWN** | No source named in `Integration.java:11`, `IntegrationService.java:1`, `IntegrationController.java:1`, `IntegrationRepository.java:1`, tests `IntegrationManagementTest.java:52` only uses generic `"IntA"` placeholder, `CODEBASE_OVERVIEW.md:65` lists `IntegrationController` as generic, no `docs/*.md` except this contract, no `frontend/src/types/integration.ts:1` concrete source. |
| **Integration type** | **CONFIRMED generic** `VALID_TYPES:26 API/WEBHOOK/DATABASE/FILE/SFTP/MANUAL/AUTOMATIC/CUSTOM` `IntegrationService.java:25` – but **which type maps to first source UNKNOWN**. Frontend `IntegrationsPage.tsx:17` shows same list + `Custom allowed`. No type is hard-wired to a vendor. |
| **Purpose** | **UNKNOWN** | No purpose comment; `IntegrationService.testConnection:199` comment only `Do not expose configuration/secrets`. No business mapping for evidence types. |
| **Authentication mechanism** | **UNKNOWN** | No auth code: `configuration TEXT` `Integration.java:38` opaque, never parsed, never used to call external system. No `RestTemplate/WebClient`, no `Authorization` header builder, no OAuth/API-key handling anywhere in `src/main/java/**`. |
| **Required credentials / configuration** | **UNKNOWN** | `configuration` is free-form `TEXT` `frontend/src/types/integration.ts:6` textarea; no schema, no validation beyond non-blank for test `IntegrationService:201`. No example in repo except placeholder `{"key":"secret"}` in test `IntegrationManagementTest.java:52` and `{"url":"https://...","token":"***"}` in UI `IntegrationsPage.tsx:298` – both **PROPOSED examples, not CONFIRMED contracts**. |
| **API base URL** | **UNKNOWN** | No URL constant, no `application.properties` key for external API, no `VITE_*` var. |
| **Endpoints** | **UNKNOWN** | No `GET /cases`, `GET /evidence` etc. No `@FeignClient`, no `RestClient`. |
| **HTTP methods** | **UNKNOWN** | `testConnection` does no HTTP `208` mock. No method defined. |

**Verdict**: Repository contains **zero concrete source implementations** to reuse. This is **CONFIRMED by exhaustive inspection** of `src/**/*`, `frontend/src/**/*`, `docs/*`, `CODEBASE_OVERVIEW.md`, `pom.xml` (no external SDK deps).

### G.B. Extraction Contract
All items **UNKNOWN** because no source → no contract to extract. Specifically:

- **CONFIRMED generic only**: `IntegrationService.validateStatus:34` and `validateType:43` exist, but they govern **integration metadata**, not extraction.
- **Pagination** – UNKNOWN (offset/cursor/page size/total header absent).
- **Filtering** – UNKNOWN (query params like `updatedSince` not defined).
- **Incremental sync support** – UNKNOWN (no `updatedAt` cursor, no webhook registration).
- **External timestamps / change markers** – UNKNOWN (no `createdAt/updatedAt` mapping from external).
- **Rate limits** – UNKNOWN (no `Retry-After` handling, no throttling).
- **Retry requirements** – UNKNOWN (no retry policy; current `GlobalExceptionHandler.java:12` only maps 400/403/409/500, no 429).
- **Timeout behavior** – UNKNOWN (only `spring.datasource.hikari.connection-timeout=10000` `application.properties:13` for DB, not external).
- **Error responses** – UNKNOWN (no external error schema; only internal `ResponseStatusException` `409/400` `IntegrationService:72`).
- **Expected data volume** – UNKNOWN (no size estimate, no file threshold vs API).

Do NOT assume pagination – must be provided with source spec.

### G.C. External Data Model
| Object | Status |
|--------|--------|
| **Object name(s)** | **UNKNOWN** – no domain objects (e.g., `Case`, `Evidence`, `Actor`) committed; `File.java:11` sourceType is for **TaceIQ-internal files**, not external objects. |
| **External ID** | **UNKNOWN** – required for dedup `canonical_externalId` but not defined in any entity. |
| **Important fields** | **UNKNOWN** |
| **Relationships to other objects** | **UNKNOWN** – `Integration.files:41` is the only confirmed relationship (Integration 1:N File), not a graph edge from source. |
| **Required / Optional fields** | **UNKNOWN** |
| **Created/updated timestamps** | **UNKNOWN** – `Integration.createdAt/updatedAt:46` are internal TaceIQ timestamps, not external. |
| **Delete / tombstone behavior** | **UNKNOWN** – current `deleteIntegration:222` only blocks if `files` linked `409`, no soft-delete sync from source; no `isDeleted` flag in `Integration.java`. |

### G.D. Sample Payload
- **CONFIRMED**: No real sample exists in repo. `smoke_login.json` is only auth smoke, not integration payload. No `src/test/resources/*.json`, no `docs/sample*.json`.
- **PROPOSED placeholders** (not to be mistaken as real): test `configuration "{\"key\":\"secret\"}"` `IntegrationManagementTest.java:52` and UI placeholder `IntegrationsPage.tsx:298` – explicitly **not a contract**.
- **UNKNOWN**: Any actual JSON/XML/CSV from a vendor. **Must not be manufactured**.

Label: **UNKNOWN – no sample to include**.

### G.E. Canonical TaceIQ Mapping
Since no external object is selected, mapping is **PROPOSED template only**, reusing existing isolation:

```
External object [UNKNOWN] 
  → Canonical TaceIQ entity [PROPOSED generic canonical_entity, see C1]
    → PostgreSQL fields [PROPOSED org_id, integration_id, externalId UNIQUE(org_id,integration_id,externalId), normalizedPayload JSONB, hash, firstSeenAt/lastSeenAt, sync_id]
      → Neo4j representation [PROPOSED orgId property on node/relationship, stableId=externalId, labels UNKNOWN placeholder]
```

- **CONFIRMED reusable**: `org_id` isolation pattern `IntegrationService:81 getReferenceById(orgId)`, `File.org_id NOT NULL` `File.java:21`, `Configuration unique(org_id,definition_id)` `Configuration.java:9`.
- **PROPOSED**: One generic `canonical_entity` suffices until domain objects are defined; per-type tables only if source has clearly distinct entities.
- **UNKNOWN**: Specific labels (`Evidence` vs `Case`), properties, relationship types – cannot be mapped without external schema; must not invent `(:Investigator)-[:AUTHORED]->(:Evidence)`.

No DDL created in this phase; `ConfigurationDefinitionSeeder:36` 8 definitions remain sole config source – do not add columns to `Integration`.

### G.F. Sync Implications (given current unknown source)
Derived from generic lifecycle `D`, but affected as:

- **Initial sync** – **BLOCKED**: cannot implement fetch; would be `PENDING → RUNNING` with paginated GET using `configuration` URL – but URL unknown, so no initial sync code can be written.
- **Repeat sync** – **BLOCKED**: incremental cursor (`?updatedSince=`) unknown field name/type; cannot choose full vs delta.
- **Duplicate detection** – **PROPOSED ready**: pattern `ON CONFLICT(org_id,integration_id,externalId)` exists as `integration name 409` `IntegrationService:72`; extendable to data once `externalId` defined, but field not yet chosen.
- **Updates** – **UNKNOWN**: depends on whether source provides `updatedAt` or hash diff.
- **Deletes / tombstones** – **UNKNOWN**: source may hard-delete, soft-delete flag, or never delete; current `Integration.delete` `409` if files linked is unrelated to source deletes.
- **Partial failures** – **PROPOSED handling ready**: `PARTIAL` state `D1` + `GlobalExceptionHandler:22 400/500` + frontend `ErrorAlert` `IntegrationsPage.tsx:215`; but per-record skip logic needs error schema from source.
- **Retries** – **UNKNOWN**: retry-after header, idempotency key unknown; cannot set backoff without rate-limit spec.
- **Tenant isolation** – **CONFIRMED strong**: every future `integration_sync` must follow `findByIdAndOrganisationOrgId:14` / `getCurrentOrgId:44` pattern; Neo4j must include `orgId` on every node/rel as per `E1`. This implication is **not blocked** – it is already enforceable.

### G.G. Phase 1 Blockers (remaining unknowns before implementation)
**All 10 from B2 plus G-specific, now enumerated as blockers for implementation:**

1. Vendor/source name & purpose (e.g., which evidence store) – required to name `type` concretely.
2. Auth scheme + credential fields (API key header name, OAuth flow) – determines `configuration` schema & vault need.
3. Base URL + endpoint paths + HTTP methods – required for `ExternalClient`.
4. Pagination (cursor vs offset, page size, total) – required for extraction loop.
5. Filtering & incremental cursor param (`updatedSince`, `changeVersion`) + timestamp format (ISO8601/Instant).
6. Stable `externalId` field path per object (e.g., `$.id` vs `$.evidenceId`).
7. Full record schema per object – fields, types, required/optional, enums, nested refs – required for canonical columns/JSONB shape.
8. Relationships between objects (FK fields) – required for Neo4j edges.
9. Created/updated/deleted signals & tombstone – determines `isDeleted` handling.
10. Rate limits, retry-after, error schema (429/5xx + body) + expected data volume – required for retry/timeout design.
11. Secret storage decision – `configuration TEXT` plaintext `Integration.java:38` vs encrypted/vault (blocked by #2).
12. Canonical granularity – one generic table vs per-object tables (blocked by #7, #8).

No blocker was resolved by inspecting `src/test/java/com/taceiq/IntegrationManagementTest.java:30` – it confirms generic CRUD only.

### First Implementation Step

**Exact next step (documentation-only, no code):** **Obtain written source decision** – one paragraph + one artifact:
- Decision doc line: `Source = <vendor/product>, type = <one of VALID_TYPES:25>, purpose = <e.g., evidence ingestion>`.
- Artifact: **anonymized sample response** (1 page JSON, redacted secrets) showing real field names, `externalId` path, `createdAt/updatedAt` format, pagination envelope (e.g., `{data:[...], nextCursor:""}`), plus auth example (`Header: X-Api-Key: ***`).

Only after that artifact is committed to `docs/` (e.g., `docs/sample-source-response.json` redacted), the next **code** step becomes justified: design `canonical_entity` DDL with `UNIQUE(org_id,integration_id,externalId)` and `integration_sync` table, and add `POST /api/integrations/{id}/sync` stub returning `PENDING` with `requireIntegrationRead` check, still without external call, migration file not yet applied. Until sample exists, **stop at analysis** – do not create speculative entities/migrations/Neo4j classes.

---

## H. Sample Source Response (Step 3 – First Real Contract)

> Source: `docs/sample-source-response.json` (anonymized, redacted). No production secrets, no Java/TS/schema changes. This section is **documentation only** – canonical mapping remains PROPOSED, DDL not finalized.

### Source
- **Source name**: `Generic Enterprise Evidence Vault (PROPOSED)` `sample-source-response.json:5` – **PROPOSED** vendor-neutral; actual vendor **UNKNOWN – requires confirmation**. Purpose evidence ingestion for traceability/timeline (`EVIDENCE_*` permissions `PermissionSeeder:39`).
- **Integration type**: `API` `sample:6` – fits `VALID_TYPES:25` `API`; **CONFIRMED** type `API` allowed, mapping to this source **PROPOSED**.
- **Endpoint**: `GET https://evidence.example.internal/api/v1/evidence` `sample:21-23` (base redacted, **UNKNOWN actual host**).
- **HTTP method**: `GET` `sample:24` – **PROPOSED**, detail `GET /api/v1/evidence/{evidenceId}` and `GET /api/v1/cases` also **PROPOSED UNKNOWN**.

### Authentication
- **Mechanism**: `Bearer token` `sample:13` `Authorization: Bearer ***REDACTED***` – **PROPOSED**; alternative `X-Api-Key: ***REDACTED***` **UNKNOWN** `sample:14`. No OAuth refresh flow documented – **UNKNOWN**.
- **Required headers/credentials**: `Authorization` header with token; **CONFIRMED** current `Integration.configuration TEXT:38` could hold it but **UNKNOWN** vault/encryption. Sample shows what must be redacted.
- **What must NOT be persisted as plaintext**: `Authorization token, X-Api-Key, refresh_token` `sample:15-17` – **CONFIRMED** current plaintext `configuration` must not log (`IntegrationService:207`, `GlobalExceptionHandler:50` sanitizes, `IntegrationsPage.tsx:336` blur) – encryption **UNKNOWN** blocker remains.

### Request
- **Required parameters**: `limit` integer `sample:33`; optional `cursor`, `updatedSince`, `caseId` – all **PROPOSED**.
- **Pagination**: Cursor-based `?limit=2&cursor=eyJpZCI6...` `sample:40` envelope `data[] + pagination{nextCursor, hasMore, totalCount:null}` `sample:54` – **PROPOSED**; `totalCount` **UNKNOWN** if vendor returns; offset alternative **UNKNOWN**.
- **Incremental-sync parameters**: `?updatedSince=ISO8601` `sample:49` e.g. `?updatedSince=2026-09-03T00:00:00Z` plus `updatedAt` per record `sample:86` – **PROPOSED**, webhook/change-feed **UNKNOWN**.

### Response
- **Response envelope**: `{ data: Evidence[], pagination: {nextCursor:string|null, hasMore:boolean, totalCount:null} }` `sample:54` – **PROPOSED**, preserves actual nesting `data` array.
- **Record structure**: See `sample:59-99` 2 records `ev_001, ev_002`. Fields `evidenceId, caseId, title, sourceType, status, createdAt, updatedAt, actor{actorId,role}, relationships{caseId_ref,actorId_ref,parentEvidenceId}, attributes{size,contentType,storageRef,tags}`.
- **External ID**: `evidenceId` e.g. `ev_001` `sample:80` – **PROPOSED stable** for dedup `UNIQUE(org_id,integration_id,externalId)`.
- **Timestamps**: `createdAt 2026-09-01T08:12:33.000Z` `updatedAt 2026-09-03T10:15:00.000Z` `sample:84` ISO8601 UTC – **PROPOSED** format, vendor confirmation needed.
- **Relationships**: `caseId->Case many-to-one`, `actorId->Actor many-to-one`, `parentEvidenceId self-reference nullable` `sample:66,98` – all **PROPOSED**, separate `/cases` endpoint **UNKNOWN**.

### Error Handling
`sample:111` four examples – all **PROPOSED illustrative** (actual shape **UNKNOWN**):
- `401 Unauthorized {error, message: Invalid token}` – **non-retryable**
- `429 Too Many Requests + Retry-After:60` – **retryable**
- `400 Bad Request Invalid cursor` – **non-retryable**
- `500 Internal Server Error` – **retryable**
- **Rate limiting**: **UNKNOWN** headers, `Retry-After` PROPOSED `sample:117`
- **Timeout**: **UNKNOWN** – assume 10s/30s retryable `sample:118`
- **Expected data volume**: **UNKNOWN** – sample 2, assume 1k–100k `sample:119`
- Retryable vs non-retryable split as above, tenant 403 `AuthorizationService:41` remains separate.

### Canonical Mapping Preparation

No DDL yet; table shows **proposed** mapping from `sample:59` external field → canonical PostgreSQL → Neo4j. Use `CONFIRMED` only where TaceIQ already enforces.

| External Field | Meaning | Canonical TaceIQ Field | PostgreSQL | Neo4j | Status |
|---|---|---|---|---|---|
| `evidenceId` | Stable evidence identifier | `externalId` | `canonical_entity.externalId TEXT UNIQUE(org_id,integration_id,externalId)` | `Node {stableId: externalId, orgId}` `MERGE (n {orgId, stableId})` | **PROPOSED** externalId; **CONFIRMED** org isolation pattern `IntegrationService:81` |
| `caseId` | Parent case reference | `case_externalId` | `canonical_entity.case_id TEXT + FK index` | `Relationship (Evidence)-[:BELONGS_TO]->(Case)` | **PROPOSED** rel type `BELONGS_TO` placeholder – **UNKNOWN** vendor confirms case object |
| `title` | Human label | `title` | `normalizedPayload JSONB -> title or column TEXT` | `Node property title` | **PROPOSED** |
| `sourceType` | Source channel | `sourceType` | `source_type TEXT check IN (API/FILE/...)` reuse `File.sourceType:31` | `Node property sourceType + provenance` | **CONFIRMED** `File.sourceType` exists; mapping **PROPOSED** |
| `status` | Record state | `status` | `status TEXT check VALID_STATUSES:25` | `Node property status` | **PROPOSED** – maps to `IntegrationService VALID_STATUSES` already |
| `createdAt` | Creation time | `firstSeenAt` | `first_seen_at TIMESTAMPTZ` | `Node property firstSeenAt` | **PROPOSED** ISO8601 parse to `Instant` `File.onCreate:59` pattern **CONFIRMED** |
| `updatedAt` | Last change | `lastSeenAt` + `hash` | `last_seen_at TIMESTAMPTZ, hash TEXT` incremental cursor | `Node property lastSeenAt` | **PROPOSED** `updatedSince` driver |
| `actor.actorId` | Author | `actor_externalId` | `actor_id TEXT` | `(Actor)-[:CREATED]->(Evidence)` | **PROPOSED** – **UNKNOWN** actor entity separate |
| `parentEvidenceId` | Hierarchy | `parent_externalId` | `parent_id TEXT FK self` | `(Evidence)-[:DERIVED_FROM]->(Evidence)` | **PROPOSED nullable** |
| `attributes.size` | Payload size | `size` | `size BIGINT` like `File.size:44` | `Node property size` | **CONFIRMED** type `File.size` exists; use **PROPOSED** |
| `attributes.contentType` | MIME | `contentType` | `content_type TEXT` | `Node property contentType` | **CONFIRMED** `File.contentType:41` |
| `attributes.storageRef` | Storage pointer | `storageRef` | `storage_ref TEXT` – **must NOT expose secret** | **Not mapped to graph** – kept PostgreSQL only | **PROPOSED** redacted |
| `attributes.tags` | Labels | `tags` | `tags TEXT[] or JSONB` | `Node property tags` | **PROPOSED** |
| `pagination.nextCursor` | Paging | `externalCursor` | `integration_sync.external_cursor TEXT` | — | **PROPOSED** new column |
| `error 429 Retry-After` | Throttle | `retryAfter` | `integration_sync.error_summary` | — | **PROPOSED** |
| `orgId` (implicit) | Tenant | `org_id` | `org_id FK NOT NULL` every table | `Node/Edge property orgId` | **CONFIRMED** `Integration.organisation org_id NOT NULL:25` tenant pattern |

**DO NOT finalize schema** – table is preparation; `ConfigurationDefinitionSeeder:36` 8 defs and `71 permissions` unchanged.

### First Implementation Step (updated after sample)

**Next code step now justified (still no Neo4j/ingestion yet):** Design non-destructive DDL for `integration_sync` (org_id, integration_id FK, status PENDING/RUNNING/SUCCESS/FAILED/PARTIAL, external_cursor, stats JSONB, error_summary TEXT) + generic `canonical_evidence` (org_id, integration_id, externalId UNIQUE(org_id,integration_id,externalId), case_id, actor_id, parent_id, title, source_type, status, first_seen_at, last_seen_at, hash, normalized_payload JSONB) – migration not yet applied, review required. Then add `POST /api/integrations/{id}/sync` stub returning `202 {syncId, status:PENDING}` reusing `AuthorizationService.requireIntegrationRead:178` + `getCurrentOrgId:44` tenant check, persisting `integration_sync` row only. No external HTTP call, no Neo4j nodes in this next increment.

---

## I. Graph Projection – Implemented (Step 9)

> Implemented `src/main/java/com/taceiq/graph/**` – PostgreSQL `canonical_evidence` → Neo4j, org-isolated, idempotent. No ingestion change, no new permissions/configs.

**Node labels (actual):**
- `:Evidence {orgId: Long, stableId: String}` – `stableId = canonical_evidence.external_id` `GraphProjectionService.java:45` – **stable identity is (orgId, stableId)**, not PostgreSQL `id` (`CanonicalEvidence.java:20` surrogate). `MERGE (e:Evidence {orgId:$orgId, stableId:$stableId})`
- `:Case {orgId, stableId}` – `stableId = case_id` `sample:76` – created only if `caseId` non-blank `GraphProjectionService.java:58`
- `:Actor {orgId, stableId}` – `stableId = actor_id` `sample:83` – only if `actorId` non-blank `GraphProjectionService.java:72`

**stableId semantics:** `external_id` from source is tenant-scoped dedup key `UNIQUE(org_id,integration_id,external_id)` `phase1-canonical-model.md:91`; two orgs can have same `ev_001` as separate nodes because `orgId` part of MERGE key. Never use DB `id`.

**Evidence properties (only justified fields, not full payload):**
`orgId, stableId, title, sourceType, status, sourceCreatedAt, sourceUpdatedAt` `GraphProjectionService.java:46` – `title/sourceType/status` trimmed, timestamps `Instant.toString` ISO8601. `normalized_payload JSONB` stays PostgreSQL, **NOT copied** to graph (explicit non-projection).

**Relationship types (only 3):**
- `(:Evidence)-[:BELONGS_TO]->(:Case)` if `caseId` present `GraphProjectionService.java:62`
- `(:Evidence)-[:CREATED_BY]->(:Actor)` if `actorId` present `GraphProjectionService.java:75`
- `(:Evidence)-[:DERIVED_FROM]->(:Evidence)` if `parentId` non-blank and not self `GraphProjectionService.java:88` – target is also `:Evidence` placeholder via `MERGE (p:Evidence {orgId:$orgId, stableId:$parentId})` (allows parent not yet canonical)

All `MERGE` – idempotent. Relationships use `MERGE` on same `(orgId, stableId)` endpoints, never `CREATE`. Verified `GraphProjectionServiceTest.idempotentRepeatedProjectionUsesMergeNotCreate` – repeated projection yields `MERGE` not `CREATE`.

**Tenant isolation:** Every Cypher constrains `orgId` – `MERGE (e:Evidence {orgId:$orgId, stableId:$stableId})`, `MATCH (e:Evidence {orgId:$orgId, stableId:$externalId})` `GraphProjectionService.java:55,62`. Projection input is `canonicalRepo.findAllByOrganisationOrgId(orgId)` `GraphProjectionService.java:27` filtered `isDeleted=false`. Cross-org edge impossible because both ends `orgId=$orgId` same param; test `crossTenantRelationshipPrevention` and `sameStableIdAcrossOrganisationsSeparateNodes`.

**Idempotency:** `MERGE` on `(orgId,stableId)` for nodes + `MERGE` for relationships; second run over same `canonical_evidence` creates zero duplicates, `GraphProjectionServiceTest.sameCaseProjectedRepeatedlyOneCaseNode` distinct counts. `Evidence` without `case/actor/parent` creates no bogus nodes `nullCaseActorParentDoesNotCreateBogusNodes`, deleted `isDeleted=true` skipped `deletedEvidenceIsNotProjected`.

**What is NOT projected:** `normalized_payload`, `contentHash`, `size/tags/storageRef` (kept PostgreSQL), `deleted` tombstones, `is_deleted` flag, `sync_id` provenance (kept PostgreSQL), no `Case`/`Actor` enrichment beyond `stableId`, no `traceability/timeline/recommendation/decision/report` labels.

**GRAPH_READY validation criteria (current):**
Canonical `SUCCESS` + graph `MERGE` success + `GraphValidator.validate(orgId)` `GraphValidator.java:18` checks: `Evidence.orgId` present, `stableId` present, no duplicate `(orgId,stableId)` `WHERE c>1`, no cross-org relationship `a.orgId <> b.orgId`, evidence count vs canonical `count(*)` consistency note; returns `GraphValidationResult{valid, evidenceCount, invalidCount, errors}`. `GRAPH_READY` only when `valid=true` and `canonical count == graph count` (service-level, no DB column added).

**API boundary:** `POST /api/graph/projection` and `POST /api/integrations/{id}/graph/projection` + `GET /api/graph/validation` `GraphController.java:13` – `202 ACCEPTED GraphProjectionResult{organisationId, evidenceProjected, casesProjected, actorsProjected, relationshipsProjected, skipped}` DTO, never exposes driver. Reuses `AuthorizationService.getCurrentOrgId:44` + `requireIntegrationRead:178` – no new permission.

**Configuration:** `app.neo4j.uri=${NEO4J_URI:}` `username/password/database` `application.properties:39` + `Neo4jConfig.java:1` `Driver` bean `@Conditional` on `uri` blank → `null` – app starts without Neo4j (`IllegalStateException` on projection if not configured, documented).

---

## J. Graph Readiness – Implemented (Step 10)

> Orchestration `POST /api/integrations/{id}/graph/ready` – PostgreSQL authoritative, Neo4j derived, service-level `GRAPH_READY`, no new status column, no destructive transaction.

**Decision – GRAPH_READY representation:**
Inspected `integration_sync` table `phase1-canonical-model.md:12` `CHECK (status IN ('PENDING','RUNNING','SUCCESS','FAILED','PARTIAL'))` – adding `GRAPH_READY` would require `ALTER TABLE ... CHECK` migration and conflates canonical persistence with derived graph. **Chosen smallest safe: keep 5 statuses, derive readiness via `GraphReadinessService.java:1`** – no second table, no new column, no speculative workflow states. Documented alternative (extend to 6) rejected for now. `SUCCESS` = canonical PostgreSQL persisted; `GRAPH_READY = SUCCESS + projection + validation`.

**Sync lifecycle (actual implemented):**
`PENDING → RUNNING (IntegrationSyncService.createPending:32 / fetchEvidenceForSync:108 / completeSync:148) → SUCCESS/PARTIAL/FAILED → graph projection (GraphReadinessService.checkReadiness:45) → validation → GRAPH_READY` – `docs/phase1-canonical-model.md:8` state machine unchanged. Projection never before canonical commit.

**PostgreSQL/Neo4j transaction boundary:**
`PostgreSQL commit → Neo4j projection → validation → readiness result` – **not one ACID transaction**. `IntegrationSyncService.completeSync` commits canonical `SUCCESS` in PostgreSQL; then `GraphReadinessService` calls `GraphProjectionService.projectForIntegration` (Neo4j `MERGE`) and `GraphValidator.validate` in separate Neo4j sessions. If Neo4j unavailable: canonical remains `SUCCESS`, `graphReady=false`, `graphProjected=false`, `evidenceCount` preserved, error sanitized (`Bearer ***`), no `DELETE`/`is_deleted` change. Test `neo4jUnavailable_canonicalRemainsSuccess`.

**Readiness criteria (all must be true for `graphReady=true`):**
A. `sync` belongs to current `orgId` `AuthorizationService.getCurrentOrgId:44` – `findByIdAndOrganisationOrgId` else `403/404` (existing convention `IntegrationService:90`)
B. `sync.integrationId == requested integrationId`
C. `canonicalSyncStatus == "SUCCESS"` – `PARTIAL/FAILED/PENDING/RUNNING` → `graphReady=false` (tests `partialSync`, `failedSync`)
D. `canonical non-deleted evidence exists` `count>0` `canonicalRepo.findByIntegrationIdAndOrganisationOrgId` filtered `isDeleted=false` – empty → `graphReady=false` `emptyCanonicalEvidence`
E. `Neo4j projection completes` `projectForIntegration` `MERGE` – `IllegalStateException` (uri missing) → `graphReady=false` but canonical preserved
F. `GraphValidator.valid==true` – checks `orgId/stableId` present, no duplicate `(orgId,stableId)`, no cross-org `a.orgId<>b.orgId` `GraphValidator.java:25`
G. `graph evidenceCount == canonicalCount` – mismatch → `graphReady=false` `countMismatch`
H. `evidenceCount>0` – empty graph never ready

If any fails: `graphReady=false`, `validationErrors` contains reason, no credentials.

**Latest sync selection (deterministic):**
`findTopByOrganisationOrgIdAndIntegrationIdAndStatusOrderByCompletedAtDesc(orgId,integrationId,"SUCCESS")` `IntegrationSyncRepository.java:17` – `ORDER BY completed_at DESC LIMIT 1`, org+integration scoped, cross-org never selected `crossTenantSyncNeverSelected`. If none: `graphReady=false` `No successful canonical sync exists`.

**Tenant isolation:** Every operation derives `orgId` from `AuthorizationService.getCurrentOrgId()`, never `orgId` from body/query. `integrationRepository.findByIdAndOrganisationOrgId` `403/404` for cross-tenant `crossTenantIntegrationDenied`. Projection uses `orgId` param in every `MERGE`, validation uses `MATCH (e:Evidence {orgId:$orgId})`.

**Projection failure handling:** Neo4j failure does NOT delete/revert canonical; `graphReady=false`, `graphProjected=false`, `evidenceCount` still canonical count, error sanitized `sanitize()` `GraphReadinessService.java:95` `Bearer\s+\S+ → Bearer ***`, `password` masked.

**Idempotency:** `checkReadiness` does not create new `IntegrationSync` row (`verify(never).save` in test `repeatedReadinessCallNoDuplicateSync`), repeated `projectForIntegration` uses `MERGE` – no duplicate nodes/relationships (`GraphProjectionServiceTest.sameRelationshipNotDuplicated`).

**What is NOT in this step:** No `Case`/`Actor` entities, no `SUCCESS→GRAPH_READY` status column, no `graph_ready` table, no queues/retries/workers, no traceability/timeline/AI/workspace/recommendations/decisions/reports.

**API:** `POST /api/integrations/{integrationId}/graph/ready` + `GET /api/integrations/{integrationId}/graph/ready` `GraphReadinessController.java:1` – `200 GraphReadinessResult{organisationId,integrationId,syncId,canonicalSyncStatus,graphProjected,graphValid,graphReady,evidenceCount,validationErrors,message}` DTO, never exposes driver. Reuses `requireIntegrationRead:178` (no new permission).

## Appendix (extended)

**Source decision log (G→H→I→J→K):** Step 2 found **no concrete source** (exhaustive grep of `src/**/*`, `frontend/**/*`, `docs/*`). Step 3 introduced **PROPOSED Generic Evidence Vault** as illustrative first source to unblock canonical design; vendor name, host, and credentials remain **REDACTED/UNKNOWN** and require confirmation before production use. Step 9 implemented actual `Evidence`/`Case`/`Actor` projection with `orgId+stableId` MERGE and validator as documented above – no speculative labels beyond these three. Step 10 added `GraphReadinessService` derived `GRAPH_READY` (no new status column). Step 11 adds investigator read APIs `GET /api/graph/evidence` + `GET /api/graph/cases/{caseId}/traceability` with `GRAPH_READY` gate.

---

## K. Investigator Graph Read APIs – Implemented (Step 11)

> Read-only `src/main/java/com/taceiq/graph/**` over `GRAPH_READY` graph – no ingestion/projection change, no new permissions/configs, no AI/frontend.

**Endpoints (actual):**
- `GET /api/graph/evidence?caseId=&actorId=&page=&size=` `GraphQueryController.java:14` – `200 GraphEvidencePageResponse{content[], page, size, totalElements, totalPages}` DTO `GraphEvidenceResponse{stableId,title,sourceType,status,sourceCreatedAt,sourceUpdatedAt}` `GraphQueryRepository.java:33` – never `neo4jId/credentials/normalized_payload/contentHash`
- `GET /api/graph/cases/{caseId}/traceability` `GraphQueryController.java:20` – `200 CaseTraceabilityResponse{case:{stableId,label}, nodes[], relationships[], depth}` `TraceabilityDtos.java:1` – `nodes: TraceabilityNodeResponse{stableId,label,title,sourceType,status}`, `relationships: TraceabilityRelationshipResponse{fromStableId,fromLabel,type,toStableId,toLabel}`

**Tenant isolation:** `orgId = AuthorizationService.getCurrentOrgId:44` never query/path `orgId` (`GraphQueryService.java:33`), every Cypher `orgId: $orgId` – `MATCH (e:Evidence {orgId:$orgId})` `WHERE ($caseId IS NULL OR EXISTS { MATCH (e)-[:BELONGS_TO]->(c:Case {orgId:$orgId, stableId:$caseId}) })` `GraphQueryRepository.java:34` – actor filter same, traceability `MATCH (c:Case {orgId:$orgId, stableId:$caseId})` `WHERE all(node IN nodes(path) WHERE node.orgId=$orgId)` `GraphQueryRepository.java:86`.

**GRAPH_READY gate:** Every read calls `GraphQueryService.ensureGraphReady` → `GraphReadinessService.isOrgGraphReady(orgId)` `GraphQueryService.java:70` – if `graphReady=false` → `400 Graph not ready` clean, no Neo4j `driver` error exposed, no investigator data leaked. Reuses readiness criteria `docs J: A-H` (`SUCCESS`, evidence>0, projection+validation, count match). Not duplicated in controller.

**Pagination:** `page=0,size=20` defaults, `size 1-100` else `400` `GraphQueryService.java:40`, `totalElements` via `countEvidence` org+filters, `totalPages=ceil(total/size)`, `SKIP $skip LIMIT $limit` `ORDER BY e.stableId` `GraphQueryRepository.java:53` – no full graph load.

**TRACEABILITY_DEPTH:** `ConfigurationService` `TRACEABILITY_DEPTH` per org `ConfigurationDefinitionSeeder.java:48` default `5`, fallback definition `defaultValue`, org override `configurationRepository.findByDefinitionKeyAndOrganisationOrgId` `GraphQueryService.java:84`, validated `>0` else `400`, ceiling `10` `Math.min(configured,10)` `GraphQueryService.java:94` – depth never from HTTP request, Cypher built via validated integer `String.format("*0..%d",depth)` safe.

**Traceability graph shape:** Start `(:Case {orgId:$orgId, stableId:$caseId})` `404` if not exists (tenant-isolated `caseExists`), traverse only `BELONGS_TO|CREATED_BY|DERIVED_FROM` `GraphQueryRepository.java:85` `*0..depth` undirected, collect `nodes(path)` distinct by `label:stableId` and `relationships` distinct by `from->type->to`, returns `caseNode` + `nodes[]` (Evidence/Case/Actor) + `relationships[]` with `fromStableId/fromLabel/type/toStableId/toLabel`, `depth` echo. No `CREATE`, no new relationship types, no `traceability/timeline/recommendation` logic.

**Org isolation in traversal:** `WHERE all(node IN nodes(path) WHERE node.orgId=$orgId)` DB-side, not Java filter; `all relationships type IN [...]` already constrained.

**Permission:** `requireEvidenceGraphAccess: EVIDENCE_GRAPH_ACCESS|EVIDENCE_ACCESS|INVESTIGATION_ACCESS` `AuthorizationService.java:206` for `GET /evidence`, `requireTraceabilityAccess: TRACEABILITY_ACCESS|EVIDENCE_GRAPH_ACCESS|EVIDENCE_ACCESS` `AuthorizationService.java:210` for traceability – smallest helpers added, **no new permission added to `PermissionSeeder:21` 71 unchanged**, no `71` modification.

**What is NOT:** No `normalized_payload/contentHash` in response, no Neo4j internal IDs, no `Case/Actor` mutation, no `investigation creation`, no AI/LLM, no timeline/recommendations/decisions/reports/workspace/graph editing/new node types, no audit logs, no background jobs.

**Performance:** Evidence uses `orgId` + filters + `SKIP/LIMIT` + `count` constrained; traceability uses exact `caseId` + bounded `*0..depth≤10` + `orgId` filter, never full scan, never thousands by default.

---

## L. Investigator Workspace Read APIs – Implemented (Step 12)

> Read-only investigator workspace `src/main/java/com/taceiq/graph/**` over `GRAPH_READY` – PostgreSQL authoritative for detail, Neo4j for navigation, no new permissions/configs, no AI/frontend.

**Endpoints (actual):**
- `GET /api/graph/evidence/{stableId}` `GraphWorkspaceController.java:12` – `200 GraphEvidenceDetailResponse{stableId,title,sourceType,status,sourceCreatedAt,sourceUpdatedAt,caseId,actorId,parentId,firstSeenAt,lastSeenAt,attributes{size,contentType,tags}}` – authoritative `canonical_evidence` via `CanonicalEvidenceRepository.findByExternalIdAndOrganisationOrgId` `GraphWorkspaceService.java:20` filtered `isDeleted=false` + `GRAPH_READY` gate
- `GET /api/graph/cases/{caseId}` `GraphWorkspaceController.java:18` – `200 CaseFileResponse{case{stableId,label,evidenceCount,actorCount}, evidence[], actors[], relationships[], page,size,totalElements,totalPages}` – bounded pagination `page=0,size=20,max 100` `GraphWorkspaceService.java:55` – evidence via PostgreSQL `canonicalRepo.findByCaseIdAndOrganisationOrgId` paginated `PageRequest`, actors via `actorId` distinct, relationships via `GraphQueryRepository.traceCase(...,1)` filtered `BELONGS_TO|CREATED_BY|DERIVED_FROM` `GraphWorkspaceService.java:85`.

**PostgreSQL vs Neo4j responsibility:**
- **PostgreSQL authoritative:** `GraphEvidenceDetailResponse` scalar fields `externalId→stableId, caseId, actorId, parentId, title, sourceType, status, sourceCreatedAt/UpdatedAt, firstSeenAt/lastSeenAt` from `canonical_evidence` `GraphWorkspaceService.java:40` + `attributes` parsed from `normalizedPayload` JSON `size/contentType/tags` (safe, no `storageRef` secret, no `contentHash/syncId`), never `org_id/internal id` exposed
- **Neo4j derived:** `caseExists` `GraphQueryRepository.caseExists:73`, `traceCase` depth 1 for relationships `GraphWorkspaceService.java:85`, counts `evidenceCount = canonicalRepo.countByCaseIdAndOrganisationOrgId`, `actorCount = distinct actorIds` – Neo4j never source for detailed attributes

**Evidence detail consistency:** Must exist in `canonical_evidence` `findByExternalIdAndOrganisationOrgId` `isDeleted=false` else `404` `GraphWorkspaceService.java:28` – cross-tenant behaves as `404` via org-scoped query, not `403` reveal. `GRAPH_READY` gate before detail query `ensureGraphReady`.

**Case file tenant isolation:** `orgId = AuthorizationService.getCurrentOrgId:44` never `caseId` path `orgId`; `caseExists` Neo4j `MATCH (c:Case {orgId:$orgId, stableId:$caseId})`, evidence `canonicalRepo.findByCaseIdAndOrganisationOrgId(cId,orgId)` org-scoped, actors/relationships filtered `Set.of(BELONGS_TO,CREATED_BY,DERIVED_FROM)` `GraphWorkspaceService.java:90`.

**Pagination/bounds:** Case evidence `page=0,size=20,max 100` else `400` `GraphWorkspaceService.java:57` – `totalElements = countByCaseIdAndOrganisationOrgId`, `totalPages = ceil`, `PageRequest.of(p,s,Sort.by("externalId"))` – actors/relationships bounded via neighborhood of returned page (not entire case if huge, documented).

**What is NOT exposed:** `org_id, integration.configuration, credentials, contentHash, syncId, internal DB IDs, neo4j internal IDs, raw normalized_payload` (only parsed safe `size/contentType/tags`), `Actor.role` graph property omitted (current projection only `stableId`, documented limitation), no `investigation creation/state`, no AI/timeline.

** GRAPH_READY gate:** Both workspace reads call `ensureGraphReady` → `GraphReadinessService.isOrgGraphReady(orgId)` `GraphWorkspaceService.java:95` – if `false` → `400 Graph not ready` clean, no Neo4j internals exposed, same criteria `J: A-H` (`SUCCESS`, evidence>0, projection+validation, count match).

**Permission:** `requireEvidenceGraphAccess` `AuthorizationService.java:206` for both (`EVIDENCE_GRAPH_ACCESS|EVIDENCE_ACCESS|...` 71 unchanged), no `WORKSPACE` permission added.

**Performance:** Evidence detail single `findByExternalIdAndOrganisationOrgId` + `ObjectMapper.readTree` for attributes; case file uses `count + Page` + `findByCaseId` + one `traceCase depth 1` (bounded), no unbounded case dump.

---

## M. Investigation Persistence & Lifecycle – Implemented (Step 13)

> Minimal persisted lifecycle `DRAFT→ACTIVE→COMPLETED→ARCHIVED` – `investigation` table `V20250906__investigation.sql`, no AI/timeline/frontend.

**Database (Flyway):**
`src/main/resources/db/migration/V20250906__investigation.sql:1` `CREATE TABLE IF NOT EXISTS investigation (id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, org_id BIGINT NOT NULL REFERENCES organisations(org_id) ON DELETE CASCADE, investigation_key TEXT NOT NULL CHECK (<>''), title TEXT NOT NULL CHECK (<>''), description TEXT, status TEXT NOT NULL CHECK (IN ('DRAFT','ACTIVE','COMPLETED','ARCHIVED')), created_by_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL, created_at TIMESTAMPTZ DEFAULT now(), updated_at ...) CONSTRAINT uq_investigation_org_key UNIQUE(org_id, investigation_key)` + `INDEX idx_investigation_org, idx_investigation_org_status` `ddl-auto=none` `application.properties:18`, Flyway `spring.flyway.enabled=true` `baseline-on-migrate=true` `application.properties:43`.

**Entity/repository:**
`src/main/java/com/taceiq/entity/Investigation.java:1` `@Table(name="investigation" uniqueConstraints org_id,investigation_key)` `ManyToOne org_id` `ManyToOne created_by_user_id` `InvestigationRepository.java:1` `findByIdAndOrganisationOrgId`, `existsByInvestigationKeyAndOrganisationOrgId`, `findAllByOrganisationOrgId` – tenant-scoped only, no `Case` entity.

**Authorization:** `requireEvidenceGraphAccess() :206` reused for `create/activate/complete/archive` – **no new permission**, `71` `PermissionSeeder:21` unchanged. `orgId` never from body/path, `AuthorizationService.getCurrentOrgId:44` + `getCurrentUser` for `createdBy`.

**GRAPH_READY gate:** Every write `InvestigationService.java:18` `ensureGraphReady(orgId)` → `graphReadinessService.isOrgGraphReady(orgId)` – if `false` → `400 Graph not ready` clean, no Neo4j query after gate fails, no `Integration.configuration` leak. Verified `graphNotReadyCreation400` + `graphNotReadyTransition400` + `noNeo4jAccessWhenGraphNotReady`.

**Create `POST /api/investigations`:** `InvestigationController.java:18` `201 InvestigationResponse{id,investigationKey,title,description,status,createdByUserId,createdAt,updatedAt}` DTO `CreateInvestigationRequest.java:1` `@NotBlank @Size max 100/200/2000` `InvestigationResponse.java:1` `fromEntity` hides `orgId`. Validation trimmed, `duplicate key within org → 409` `existsByInvestigationKey…`, `malformed →400`, initial `DRAFT` `Investigation.java:14` default.

**Lifecycle transitions – only allowed:**
`DRAFT→ACTIVE` `POST /api/investigations/{id}/activate`, `ACTIVE→COMPLETED` `.../complete`, `COMPLETED→ARCHIVED` `.../archive` `InvestigationController.java:23` – service `InvestigationService.java:40` `transition(from,to)` checks `currentStatus==from` else `409 Invalid transition`, `ARCHIVED` no outgoing, `verifyTenant` via `findByIdAndOrganisationOrgId` else `404` without reveal (not `403`), `verify GRAPH_READY`, `verify authorization`, `status=to, updatedAt=now()`.

**Service:** `InvestigationService.java:1` `create, activate, complete, archive` – transition validation in service, no generic workflow engine.

**Controller:** `InvestigationController.java:1` `POST /api/investigations`, `POST /{id}/activate|complete|archive` – `404` for missing/cross-tenant, `409` for invalid lifecycle, `400` for graph not ready, `403` for permission.

**DTOs:** `CreateInvestigationRequest` `InvestigationResponse` – never serialize entity, no `orgId` exposed.

**Lifecycle contract (minimal persisted):**
`DRAFT (created) → ACTIVE (investigation started) → COMPLETED (evidence collected) → ARCHIVED (closed)` – state diagram `DRAFT->ACTIVE->COMPLETED->ARCHIVED` only; `DRAFT->COMPLETED`, `ACTIVE->DRAFT`, `COMPLETED->ACTIVE`, `ARCHIVED->*` all `409`. No reopening. Documented as minimal, not final workflow.

**What is NOT:** No `investigation/evidence` linking, no notes, no evidence/case mutation, no timeline/AI/recommendations/decisions/reports, no frontend, no new permissions/configs, no audit logs.

---

## N. Investigation Evidence Linking + Investigator Notes – Implemented (Step 14)

> Workspace persistence `investigation_evidence` association (no canonical duplication) + `investigation_note` workspace data – `DRAFT`/`ACTIVE` mutable, `COMPLETED`/`ARCHIVED` immutable.

**Database (Flyway):**
`src/main/resources/db/migration/V20250907__investigation_evidence_and_notes.sql:1` – `CREATE TABLE IF NOT EXISTS investigation_evidence (id BIGSERIAL PK, org_id BIGINT NOT NULL REFERENCES organisations ON DELETE CASCADE, investigation_id BIGINT NOT NULL REFERENCES investigation ON DELETE CASCADE, canonical_evidence_id BIGINT NOT NULL REFERENCES canonical_evidence ON DELETE CASCADE, created_by_user_id BIGINT REFERENCES users ON DELETE SET NULL, created_at TIMESTAMPTZ, CONSTRAINT uq_investigation_evidence UNIQUE(org_id,investigation_id,canonical_evidence_id))` + `INDEX idx_investigation_evidence_org_inv, idx_investigation_evidence_org_canonical`; `CREATE TABLE IF NOT EXISTS investigation_note (id BIGSERIAL PK, org_id, investigation_id, author_user_id NOT NULL REFERENCES users ON DELETE CASCADE, content TEXT NOT NULL CHECK (<>'' and <=5000), created_at, updated_at)` + `INDEX idx_investigation_note_org_inv, idx_investigation_note_org_inv_created`.

**Entity/repository:**
`src/main/java/com/taceiq/entity/InvestigationEvidence.java:1` `@Table(unique org_id,investigation_id,canonical_evidence_id)` `ManyToOne org/investigation/canonicalEvidence/createdBy` – tenant-scoped `InvestigationEvidenceRepository.java:1` `findByOrganisationOrgIdAndInvestigationIdAndCanonicalEvidenceId`, `Page findByOrganisationOrgIdAndInvestigationId`, `exists...` `count...` – **no investigation_id in canonical_evidence, no Case/Actor tables**.
`src/main/java/com/taceiq/entity/InvestigationNote.java:1` `@Table(investigation_note)` `ManyToOne org/investigation/author` `content TEXT NOT NULL` `InvestigationNoteRepository.java:1` `Page findByOrganisationOrgIdAndInvestigationId`, `findByIdAndOrganisationOrgIdAndInvestigationId` – append/update/delete workspace, not generic.

**Authorization:** `requireEvidenceGraphAccess() :206` reused for all 7 endpoints – **no new permission**, `71` unchanged. `orgId` never from body/path/query, `AuthorizationService.getCurrentOrgId:44`.

**GRAPH_READY:** Every Step 14 operation `InvestigationEvidenceService.java:18` + `InvestigationNoteService.java:18` `ensureGraphReady(orgId)` → `graphReadinessService.isOrgGraphReady(orgId)` – if `false` → `400 Graph not ready`, no Neo4j access after gate `verify(graphReadinessService).isOrgGraphReady` and `verify(never).save` when not ready.

**Investigation validation:** `loadInvestigation` `findByIdAndOrganisationOrgId` else `404` cross-tenant not reveal `InvestigationEvidenceService.java:20`, `ensureMutable` `DRAFT/ACTIVE` allowed else `409` `COMPLETED/ARCHIVED` immutable `InvestigationEvidenceService.java:28` – same for notes.

**Evidence linking endpoints – `InvestigationEvidenceController.java:1`:**
- `POST /api/investigations/{investigationId}/evidence` `{stableId:"ev_001"}` `LinkEvidenceRequest.java:1` `@NotBlank` → `201 InvestigationEvidenceResponse{stableId,title,sourceType,status,linkedAt}` `InvestigationEvidenceResponse.java:1` `fromLink` hides `orgId/internal canonical ID/contentHash/normalizedPayload/syncId` – duplicate `409`, missing `404`, deleted `404`, cross-tenant `404`, `COMPLETED/ARCHIVED 409`, `graph not ready 400`
- `DELETE /api/investigations/{investigationId}/evidence/{stableId}` → `204` – only deletes association, never `canonicalRepo.delete`
- `GET /api/investigations/{investigationId}/evidence?page=0&size=20` → `Page<InvestigationEvidenceResponse>` `default 20 max 100` `Sort.by(createdAt)`, `findByOrganisationOrgIdAndInvestigationId` paginated, tenant `never findBy org 2`

**Investigator notes – `InvestigationNoteController.java:1`:**
- `POST /api/investigations/{investigationId}/notes` `{content}` `InvestigationNoteRequest.java:1` `@NotBlank @Size 5000` → `201 InvestigationNoteResponse{id,content,authorUserId,createdAt,updatedAt}` `InvestigationNoteResponse.java:1`
- `GET /api/investigations/{investigationId}/notes?page=0&size=20` bounded `20/100` `Page` ordered `createdAt`
- `PATCH /api/investigations/{investigationId}/notes/{noteId}` `{content}` → `200` – only original `authorUserId==currentUserId` else `403` `InvestigationNoteService.java:70`, cross-tenant `404`, `COMPLETED/ARCHIVED 409`
- `DELETE /api/investigations/{investigationId}/notes/{noteId}` → `204` – only author `403` else, `404` missing, hard delete (no soft-delete per spec)

**Services:** `InvestigationEvidenceService.java:1` `linkEvidence, unlinkEvidence, listEvidence` `@Transactional` for link/unlink – keep business rules in service, controllers thin. `InvestigationNoteService.java:1` `createNote, listNotes, updateNote, deleteNote` – `@Transactional` for mutating, no generic CRUD.

**Transactions:** Link/unlink/create/update/delete each `@Transactional` single link/note – PostgreSQL only, Neo4j not mutated by Step 14 (`Neo4j is not mutated by Step 14` per spec).

**DTOs:** Dedicated `LinkEvidenceRequest, InvestigationEvidenceResponse, InvestigationNoteRequest/Response` – never serialize entities, no `orgId/internal DB IDs/normalizedPayload/contentHash/syncId/credentials`.

**Documentation:** `Investigation` → `investigation_evidence` → `canonical_evidence` association, `investigation_note` workspace; `canonical_evidence` remains authoritative, unlink does not delete canonical, notes are investigator-authored, `COMPLETED/ARCHIVED` immutable, `DRAFT/ACTIVE` mutable.

---

## O. Complaint / Investigation Intake – Implemented (Step 15)

> Intake `Manual investigator` **or** `External integration` → `Complaint` → `Investigation` – same internal model, AI not involved, Neo4j not mutated, canonical not modified.

**Domain model (Flyway):**
`src/main/resources/db/migration/V20250908__complaint.sql:1` `CREATE TABLE IF NOT EXISTS complaint (id BIGSERIAL PK, org_id BIGINT NOT NULL REFERENCES organisations ON DELETE CASCADE, complaint_key TEXT NOT NULL CHECK (<>''), investigation_id BIGINT REFERENCES investigation ON DELETE SET NULL, integration_id BIGINT REFERENCES integrations ON DELETE SET NULL, source_type TEXT NOT NULL CHECK IN ('MANUAL','INTEGRATION'), external_reference TEXT, title TEXT NOT NULL CHECK (<>''), description TEXT, batch_reference TEXT, raised_at TIMESTAMPTZ, received_at TIMESTAMPTZ DEFAULT now(), created_by_user_id BIGINT REFERENCES users ON DELETE SET NULL, created_at, updated_at, CONSTRAINT uq_complaint_org_key UNIQUE(org_id, complaint_key), CONSTRAINT uq_complaint_org_integration_external UNIQUE(org_id, integration_id, external_reference))` + `UNIQUE(investigation_id) WHERE investigation_id IS NOT NULL` partial index `uq_complaint_investigation` – enforces one complaint per investigation, `INDEX idx_complaint_org/org_investigation/org_integration_external` `ddl-auto=none`.

**Relationship (actual):**
`Organisation 1:N Complaint (org_id) → 1:1 Investigation (investigation_id FK, unique partial)` → `Investigation 1:N investigation_evidence/note` – `Complaint` holds `investigation_id` FK, `Investigation` remains workspace object without duplicating complaint fields, tenant-safe `org_id` on all tables, no `Case` entity/table.

**Tenant isolation:** Every operation `orgId = AuthorizationService.getCurrentOrgId:44` never `body/path/query orgId`; `ComplaintRepository.findByIdAndOrganisationOrgId` else `404` cross-tenant not reveal, `IntegrationRepository.findByIdAndOrganisationOrgId` for `createFromIntegration` org+integration check, all lookups `orgId` scoped.

**GRAPH_READY:** `ComplaintService.java:18` `ensureGraphReady(orgId)→graphReadinessService.isOrgGraphReady(orgId)` for `createManual` and `createInvestigationFromComplaint` – if `false` → `400 Graph not ready` clean, no Neo4j after gate, reuse `requireEvidenceGraphAccess:206` (71 unchanged).

**Manual intake `POST /api/complaints`:** `ComplaintController.java:13` `201 ComplaintResponse{id,complaintKey,title,description,batchReference,externalReference,sourceType,raisedAt,receivedAt,createdByUserId,createdAt,updatedAt,investigation{summary}}` `CreateComplaintRequest.java:1` `@NotBlank @Size complaintKey 100, title 200, description 2000, batchReference 100, externalReference 200, raisedAt Instant.parse` – `sourceType=MANUAL` forced, `createdBy` from `getCurrentUser`, `orgId` never client, validation trimmed `400`, duplicate `complaintKey` within org `409` `existsByComplaintKeyAndOrganisationOrgId`, sensible max lengths enforced.

**Create investigation from complaint `POST /api/complaints/{complaintId}/investigation`:** `ComplaintController.java:20` `{investigationKey,title,description}` `CreateInvestigationFromComplaintRequest.java:1` → `201 InvestigationResponse` `DRAFT` – loads `Complaint` tenant-scoped `404` if missing/cross-tenant, `Investigation` `existsByInvestigationKey` `409`, atomically `investigationRepository.save` + `complaint.setInvestigation(saved)` + `complaintRepository.save` in single `@Transactional`, cross-tenant complaint `404`, `graph not ready 400`, `permission 403`.

**Complaint read `GET /api/complaints/{complaintId}`:** `ComplaintService.getComplaint` `findByIdAndOrganisationOrgId` `404` else `ComplaintResponse.fromEntity` includes `investigation` summary `{id,investigationKey,title,status}` if associated, hides `orgId`.

**Investigation read:** Via `ComplaintResponse.investigation` summary only, no unbounded investigation listing.

**External / integration intake – domain boundary only:** `ComplaintService.createFromIntegration(orgId,integrationId,externalReference,title,description,complaintKey)` `ComplaintService.java:40` – `sourceType=INTEGRATION`, `integration` FK, `externalReference` required, `orgId` from `integration.organisation` context (tenant isolation), **idempotent via `findByOrganisationOrgIdAndIntegrationIdAndExternalReference` → return existing `200` else create**, no HTTP, no vendor payload mapping, `different externalReference → separate complaints`, `same externalId does not duplicate` verified.

**Idempotency:** Manual `UNIQUE(org_id, complaint_key)` `409`; Integration `UNIQUE(org_id, integration_id, external_reference)` `409` prevented via `exists` check + DB constraint – `externalReference` not timestamp, `complaintKey` derived if missing (`INT-`+extRef).

**Service structure:** `ComplaintService.java:1` `createManual, createFromIntegration, getComplaint, createInvestigationFromComplaint` – business rules in service, controllers thin `ComplaintController.java:1`, no generic CRUD.

**Transactions:** `createManual` `@Transactional`, `createInvestigationFromComplaint` `@Transactional` atomic `Investigation` + `Complaint→Investigation` association, **no PostgreSQL+Neo4j transaction** (Neo4j not mutated).

**DTOs:** `CreateComplaintRequest, ComplaintResponse, CreateInvestigationFromComplaintRequest` – never serialize `Complaint`/`Investigation` entities, safe fields only.

**What is NOT:** No `timeline/ AI/ recommendations/ decisions/ reports/ frontend/ new permissions/ new configs/ audit logs/ queues/ notifications/ vendor-specific complaint APIs/ speculative fields/ generic workflow engine`.

---

## P. Investigation Timeline – Implemented (Step 16)

> Read-only deterministic projection `GET /api/investigations/{investigationId}/timeline` – no persisted table, no AI, PostgreSQL-derived, tenant-scoped, `GRAPH_READY` gated.

**Endpoint (actual):**
`GET /api/investigations/{investigationId}/timeline?page=0&size=50` `InvestigationTimelineController.java:1` → `200 InvestigationTimelineResponse{investigationId,investigationKey,events[],page,size,totalElements,totalPages}` `InvestigationTimelineResponse.java:1` `InvestigationTimelineEventResponse{eventType,eventTime,title,description,sourceType,sourceId,stableId,metadata}` – `page 0, size 50 default, max 100` `InvestigationTimelineService.java:18` `400` if `size>100` or `page<0`, in-memory pagination after deterministic sort, `totalElements=events.size()` `totalPages=ceil`.

**Timeline source data (only fields that actually exist, no fabrication):**
- `Complaint.raisedAt → COMPLAINT_RAISED`, `Complaint.receivedAt → COMPLAINT_RECEIVED`, `Complaint.createdAt → COMPLAINT_CREATED` `InvestigationTimelineService.java:30` – only if timestamp exists
- `Investigation.createdAt → INVESTIGATION_CREATED` `InvestigationTimelineService.java:42` – **no activation/completion transitions** (current `Investigation` has `status` but no historical timestamps, do not invent)
- `InvestigationEvidence.link.createdAt → EVIDENCE_LINKED` `InvestigationTimelineService.java:55` – distinct from `EVIDENCE_CREATED`
- `CanonicalEvidence.sourceCreatedAt → EVIDENCE_CREATED`, `sourceUpdatedAt → EVIDENCE_UPDATED` (only if `updated != created`) `InvestigationTimelineService.java:60` – `sourceCreatedAt` primary, `sourceUpdatedAt` not pretended as created, `firstSeenAt/lastSeenAt` not added to avoid duplication/noise

**Investigation timeline semantics:** `EVIDENCE_CREATED` uses `sourceCreatedAt` when available else no event; `EVIDENCE_UPDATED` only if `sourceUpdatedAt != sourceCreatedAt`; `EVIDENCE_LINKED` is investigator action time vs source time – both kept distinct.

**Data model / query strategy:** No `timeline` table (`docs/phase1-canonical-model.md:12` `ddl-auto=none` unchanged), no `canonical_evidence` modification, use `ComplaintRepository.findByInvestigationIdAndOrganisationOrgId` + `InvestigationEvidenceRepository.findByOrganisationOrgIdAndInvestigationId` bulk `PageRequest 0,1000` + `CanonicalEvidence` via links (avoid N+1, no `GraphQueryRepository` needed) `InvestigationTimelineService.java:30` – tenant-scoped bulk, deterministic ordering after.

**Chronological order:** `eventTime ASC, eventType ASC, stableId ASC` `InvestigationTimelineService.java:68` – `Comparator.comparing(eventTime).thenComparing(eventType).thenComparing(stableId)` deterministic when timestamps equal.

**Pagination:** Retrieve bounded investigation-related data (`1000` limit) → construct events → sort → `subList(from,to)` in memory `InvestigationTimelineService.java:72` – `default 50 max 100` `InvestigationTimelineService.java:18` – investigation evidence is natural scope boundary, no unbounded org scan.

**Tenant isolation:** `orgId = AuthorizationService.getCurrentOrgId:44` never `path/query orgId`; `investigationRepository.findByIdAndOrganisationOrgId` else `404` cross-tenant not reveal, `complaintRepository` and `linkRepository` org-scoped, every evidence via `isDeleted` check already tenant-scoped.

**Authorization:** `requireEvidenceGraphAccess() :206` reused `InvestigationTimelineService.java:20` – **no new permission**, `71` unchanged, no config.

**GRAPH_READY:** `isOrgGraphReady(orgId)` `InvestigationTimelineService.java:19` before building timeline → `400 Graph not ready` clean, **no Neo4j access required** for timeline (PostgreSQL only) – verified `noNeo4jAccessRequired` test.

**Response safety:** `InvestigationTimelineEventResponse` only business-safe fields `eventType,eventTime,title,description,sourceType,sourceId,stableId,metadata` – no `orgId/internal IDs/contentHash/normalizedPayload/syncId/credentials/storageRef`, uses stable `complaintKey/investigationKey/stableId` `GraphEvidenceDetailResponse` pattern.

**What is NOT:** No `timeline` table, no Neo4j mutation, no new nodes/relationships, no AI/Spring AI/LLM, no `timeline persistence`, no `recommendations/checklist/decisions/final result/reports/frontend/new permissions/new configs/audit logs/queues/schedulers/vendor API`.

**Tests:** `InvestigationTimelineServiceTest.java:1` 20 tests `validTimeline`, `complaint events`, `investigation created`, `evidence created/linked`, `chronological ordering`, `deterministic ordering equal timestamps`, `pagination default 50/max 100`, `tenant isolation`, `cross-tenant 404`, `missing 404`, `permission 403`, `graph not ready 400`, `no Neo4j`, `no canonical/investigation mutation`, `no fabricated status transitions`, `null sourceCreatedAt handled`, `duplicate noisy not produced` – **20/20 PASS**.

> **"Timeline is a core deterministic projection of available investigation data. AI may consume the timeline later, but AI does not create or own the timeline."**

---

## Q. Investigator Manual Checks – Implemented (Step 17)

> Investigator-owned verification `investigation_check` – PostgreSQL-backed `OPEN/COMPLETED/SKIPPED`, independent of AI/Neo4j, tenant-scoped, `GRAPH_READY` gated, `DRAFT/ACTIVE` mutable only.

**Database (Flyway):**
`src/main/resources/db/migration/V20250909__investigation_checks.sql:1` `CREATE TABLE IF NOT EXISTS investigation_check (id BIGSERIAL PK, org_id BIGINT NOT NULL REFERENCES organisations ON DELETE CASCADE, investigation_id BIGINT NOT NULL REFERENCES investigation ON DELETE CASCADE, title TEXT NOT NULL CHECK (<>'' and <=200), description TEXT CHECK (<=2000), status TEXT NOT NULL CHECK IN ('OPEN','COMPLETED','SKIPPED'), result TEXT CHECK (<=2000), notes TEXT CHECK (<=5000), created_by_user_id REFERENCES users ON DELETE SET NULL, completed_by_user_id REFERENCES users ON DELETE SET NULL, created_at, updated_at, completed_at, CONSTRAINT chk_check_title_length, chk_description/result/notes)` + `INDEX idx_investigation_check_org_inv, idx_investigation_check_org_inv_status` `ddl-auto=none` `V20250909`.

**Check semantics:** Manual verification task e.g. “Verify batch release records” – investigator creates actual check, not hard-coded, no generic task/workflow/assignment/notification/scheduler/approval engine.

**Investigation lifecycle:** Checks may be created only when `Investigation.status` is `DRAFT` or `ACTIVE` `InvestigationCheckService.java:20` `ensureInvestigationMutable` – `COMPLETED/ARCHIVED` → `409`. Transitions only `OPEN→COMPLETED` `POST .../complete` `InvestigationCheckService.java:70` and `OPEN→SKIPPED` `.../skip` `InvestigationCheckService.java:90` – `COMPLETED→OPEN`, `SKIPPED→OPEN`, `COMPLETED→SKIPPED`, `SKIPPED→COMPLETED` all `409` – keep simple, no reopening.

**Create `POST /api/investigations/{investigationId}/checks`:** `InvestigationCheckController.java:13` `201 InvestigationCheckResponse{id,title,description,status,result,notes,createdByUserId,completedByUserId,createdAt,updatedAt,completedAt}` `CreateInvestigationCheckRequest.java:1` `@NotBlank @Size title 200, description 2000` – `status=OPEN`, `createdBy=getCurrentUser`, `completedAt/result/notes` null.

**List `GET /api/investigations/{investigationId}/checks?page=0&size=20`:** `InvestigationCheckController.java:20` `200 Page<InvestigationCheckResponse>` `default 20 max 100` `InvestigationCheckService.java:40` `PageRequest Sort.by(createdAt, id)` deterministic `createdAt ASC then id ASC`.

**Complete `POST /api/investigations/{investigationId}/checks/{checkId}/complete`:** `InvestigationCheckController.java:32` `{result,notes}` `CompleteInvestigationCheckRequest.java:1` `@Size result 2000, notes 5000` → `status=COMPLETED, result, notes, completedBy=getCurrentUser, completedAt=now` `InvestigationCheckService.java:70` – only `OPEN` else `409`, retains `result`.

**Skip `POST .../skip`:** `SkipInvestigationCheckRequest.java:1` `{notes}` `InvestigationCheckService.java:90` `status=SKIPPED, notes, completedBy, completedAt` – only `OPEN` else `409`, retains `notes` not deleted.

**Update `PATCH /api/investigations/{investigationId}/checks/{checkId}`:** `InvestigationCheckController.java:27` `{title,description}` `UpdateInvestigationCheckRequest.java:1` – only `OPEN` else `409` `InvestigationCheckService.java:50`, immutable `COMPLETED/SKIPPED`.

**Delete `DELETE .../{checkId}`:** `InvestigationCheckController.java:35` `204` – only `OPEN` else `409` `InvestigationCheckService.java:108`, keeps `COMPLETED/SKIPPED` stable.

**Tenant isolation:** `orgId = AuthorizationService.getCurrentOrgId:44` never `body/path/query orgId`; `investigationRepository.findByIdAndOrganisationOrgId` else `404`, `checkRepository.findByIdAndInvestigationIdAndOrganisationOrgId` else `404` cross-tenant not reveal `InvestigationCheckService.java:30`.

**Authorization:** `requireEvidenceGraphAccess() :206` reused for all 6 endpoints `InvestigationCheckService.java:18` – **no new permission**, `71` `PermissionSeeder:21` unchanged, no config.

**GRAPH_READY:** Every `create/list/update/complete/skip/delete` `ensureGraphReady(orgId)` → `graphReadinessService.isOrgGraphReady` `InvestigationCheckService.java:18` – if `false` → `400 Graph not ready`, **no Neo4j access** after gate (`verify(never).save` when not ready), PostgreSQL check data, no `GraphProjectionService`.

**Investigation lookup:** `findByIdAndOrganisationOrgId` pattern else `404` without revealing cross-tenant.

**No AI / No timeline mutation / No evidence mutation:** No `Spring AI/LLM`, no AI-generated checks, `InvestigationTimelineService` not modified, no `timeline` table, no `canonical_evidence`/`investigation_evidence`/`Neo4j` mutation, no `evidence-check` FK, no `audit logs/queues`.

**Entity/DTO/repository:** `InvestigationCheck.java:1` `@Table(investigation_check)` `ManyToOne org/investigation/createdBy/completedBy` `InvestigationCheckRepository.java:1` `findByIdAndOrganisationOrgId`, `Page findByInvestigationIdAndOrganisationOrgId`, `findByIdAndInvestigationIdAndOrganisationOrgId` tenant-scoped, `Pageable` deterministic ordering to avoid N+1.

**Service:** `InvestigationCheckService.java:1` `createCheck, listChecks, updateCheck, completeCheck, skipCheck, deleteCheck` – owns auth, tenant, graphReady, investigation lifecycle, check lifecycle, current user, `@Transactional` on mutations.

**Controller:** `InvestigationCheckController.java:1` `POST /checks`, `GET /checks`, `PATCH /checks/{checkId}`, `DELETE /checks/{checkId}`, `POST /checks/{checkId}/complete|skip` – thin, existing API conventions.

**Tests:** `InvestigationCheckServiceTest.java:1` 34 tests `create OPEN`, `validation failure`, `list`, `pagination`, `update OPEN`, `cannot update COMPLETED/SKIPPED`, `complete OPEN`, `complete already completed/skipped 409`, `skip OPEN`, `skip completed/skipped 409`, `delete OPEN`, `cannot delete COMPLETED/SKIPPED`, `tenant isolation`, `cross-tenant investigation/check 404`, `permission 403`, `graph not ready 400`, `no Neo4j`, `no canonical/investigation mutation`, `DRAFT/ACTIVE allows, COMPLETED/ARCHIVED rejects`, `completedByUserId/completedAt recorded`, `skipped retains notes`, `completed retains result`, `deterministic ordering`, `immutable` – **34/34 PASS**.

> **"Manual checks represent investigator-owned verification work. They are not AI decisions and are not automatically generated in the core workflow."**

---

## R. Optional AI-Assisted Investigation Recommendations – Implemented (Step 18)

> **AI is an assistant, NOT the investigation engine.** `src/main/java/com/taceiq/ai/investigation/**` – stateless, read-only, no persistence, no auto-creation of checks, no evidence/timeline/investigation mutation.

**Provider abstraction:**
`src/main/java/com/taceiq/ai/investigation/InvestigationRecommendationProvider.java:1` `generateRecommendations(InvestigationRecommendationContext)` returns `InvestigationRecommendationResult` `src/main/java/com/taceiq/ai/investigation/InvestigationRecommendationResult.java:1` – vendor-agnostic `OpenAI/Anthropic/Gemini` not coupled, `InvestigationService` not hard-coded. `NoOpInvestigationRecommendationProvider.java:1` `@Component` returns empty `List.of()` – app starts/workflow intact when AI not configured, no fake AI output presented as real.

**AI configuration (reuse existing):**
`AI_INVESTIGATION_ENABLED` + `AI_MODEL` `ConfigurationDefinitionSeeder.java:36` 8 unchanged – `InvestigationRecommendationService.java:40` `isAiEnabled(orgId)` checks `definitionRepository.findByKey` default `false` + `configurationRepository.findByDefinitionKeyAndOrganisationOrgId` org override; if `false` → `400 AI disabled` clean, **do not call provider**. `resolveModel` returns `AI_MODEL` logical model name, never exposes `provider endpoint/API key` – credentials from `env/app.neo4j.*`-style `System.getenv` if real provider added, never DTO/log/DB.

**Recommendation context (controlled, bounded):**
`InvestigationRecommendationContext.java:1` `complaint, investigation, timeline, evidence, openChecks, completedChecks, skippedChecks, model` – only safe business fields (`complaintKey, investigationKey, GraphEvidenceResponse stableId/title, InvestigationCheckResponse title/description`, timeline `InvestigationTimelineResponse` safe DTO) **DO NOT include** `orgId/internal IDs/passwords/integration configuration/API keys/contentHash/syncId/storageRef` `InvestigationRecommendationService.java:70` – evidence via `GraphEvidenceResponse` safe, checks via `InvestigationCheckResponse`.

**Context size / safety:**
`MAX_EVIDENCE 100, MAX_TIMELINE 200, MAX_CHECKS 100, MAX_DESC 500` `InvestigationRecommendationService.java:18` – `PageRequest.of(0, MAX)` bounded `findByOrganisationOrgIdAndInvestigationId`, timeline `getTimeline(...,0,50)` truncated to `200`, deterministic truncation `truncate(s,500)`, documented, no vector/RAG/embeddings.

**Recommendation response:**
`InvestigationRecommendationResponse.java:1` `investigationKey, generatedAt, model, recommendations[]` – each `InvestigationRecommendationItem{recommendationKey, category, title, rationale, suggestedAction, priority}` – `category ∈ {VERIFY,INVESTIGATE,REVIEW,FOLLOW_UP}` `priority ∈ {LOW,MEDIUM,HIGH}` controlled sets `InvestigationRecommendationService.java:20` – suggestion only.

**Structured AI output & validation:**
Prompt builder `InvestigationRecommendationPromptBuilder.java:1` system instructions: `You are an investigation assistant. Analyze only supplied context. Do not invent facts... Distinguish known vs uncertainty... Do not make final decision... Return only structured format {"recommendations":[...]}` + `Recommendations are suggestions...` – `SYSTEM INSTRUCTIONS` separated from `INVESTIGATION DATA` to mitigate prompt injection, evidence content treated as data. Backend validates `recommendationKey, category, title, rationale, suggestedAction, priority` `InvestigationRecommendationService.java:110` throws `502 BAD_GATEWAY` for malformed, never blindly deserializes arbitrary JSON into entities.

**Provider implementation:**
Behind `InvestigationRecommendationProvider`, env `AI_API_KEY, endpoint, timeout, model` (not committed, not exposed), `NoOp` is default; real provider would use `RestTemplate` with `10s/30s` timeout, no retry loop, no queue – not invented vendor contract.

**Error handling (isolated):**
`AI disabled → 400` `isAiEnabled false`, `unavailable → 503`, `timeout → 503`, `malformed → 502`, `validation → 400/502`, never expose `API key/ auth header/ raw payload`, never fail app globally – `InvestigationRecommendationService.java:80` sanitizes.

**Tenant isolation:**
`orgId = AuthorizationService.getCurrentOrgId:44` never `body/path/query orgId`; `investigationRepository.findByIdAndOrganisationOrgId` `404` cross-tenant `R`

**Authorization:** `requireEvidenceGraphAccess():206` reused – **no `AI_RECOMMENDATION_ACCESS`**, `71` unchanged.

**GRAPH_READY:** `requireEvidenceGraphAccess` then `graphReadinessService.isOrgGraphReady(orgId)` `InvestigationRecommendationService.java:18` – if `false` → `400 Graph not ready` before context collection, **no provider call** `verify(never).generateRecommendations` when not ready, no Neo4j after gate.

**Read-only workflow:** May read `complaint, investigation, evidence, timeline, checks` `InvestigationRecommendationService.java:60` – **MUST NOT mutate** `complaint/investigation/investigation_evidence/canonical_evidence/investigation_check/Neo4j` – verified `verify(never).save` for investigation/check/canonical, no `checkService.createCheck()`.

**No persistence:** No `ai_recommendation` table, no `prompts/raw responses` persisted – stateless `InvestigationRecommendationService` returns DTO only, verified `recommendationIsNotPersisted`.

**Service:** `InvestigationRecommendationService.java:1` `generateRecommendations(investigationId)` 12 steps: authorize, org, graphReady, load investigation/complaint/evidence/timeline/checks, enforce bounds, call `provider`, validate, return safe DTO – no mutation.

**Controller:** `InvestigationRecommendationController.java:1` `POST /api/investigations/{investigationId}/recommendations` `200 InvestigationRecommendationResponse` – no request body, no custom prompt injection, investigator cannot inject unrestricted system prompt.

**Tests:** `InvestigationRecommendationServiceTest.java:1` 32 tests `AI enabled→provider called`, `AI disabled→not called`, `valid/empty/malformed/invalid category/priority/missing title rejected`, `timeout/unavailable handled`, `failure does not mutate investigation/check/evidence`, `tenant isolation`, `cross-tenant 404`, `permission 403`, `graph not ready 400`, `provider not called when graph not ready/AI disabled`, `no Neo4j/canonical/investigation/check mutation`, `context contains complaint/timeline/evidence/checks`, `bounds enforced 100`, `secrets excluded`, `safe DTO`, `not persisted`, `no fake when unavailable` – **32/32 PASS** mocked provider, deterministic, `mvn test -o` no external AI call, `NoOp` used in test profile.

> **"AI recommendations are advisory only. The investigator remains responsible for deciding whether a recommendation should become an actual investigation check or action."**

**Security:** Prompt injection review – evidence/complaint text is untrusted data, `SYSTEM INSTRUCTIONS` vs `INVESTIGATION DATA` separated `InvestigationRecommendationPromptBuilder.java:10`, evidence treated as data, recommendation text never triggers backend actions automatically.

---

## S. Investigation Decisions – Implemented (Step 19)

> Investigator-owned authoritative conclusions `investigation_decision` – `DRAFT→ACTIVE` only, `OPEN` checks resolved, `GRAPH_READY` gated, no AI, no new permissions/configs.

**Database (Flyway):**
`src/main/resources/db/migration/V20250910__investigation_decisions.sql:1` `CREATE TABLE IF NOT EXISTS investigation_decision (id BIGSERIAL PK, org_id REFERENCES organisations ON DELETE CASCADE, investigation_id REFERENCES investigation ON DELETE CASCADE, decision_key TEXT NOT NULL CHECK (<>''), title TEXT NOT NULL CHECK (<>''), conclusion TEXT NOT NULL CHECK (<>''), rationale TEXT, created_by_user_id REFERENCES users ON DELETE SET NULL, created_at, updated_at, CONSTRAINT chk_decision_key_length 100, chk_title 200, chk_conclusion 5000, chk_rationale 5000, CONSTRAINT uq_decision_org_inv_key UNIQUE(org_id, investigation_id, decision_key))` + `INDEX idx_decision_org_inv, idx_decision_org_inv_created, idx_decision_org_key` `ddl-auto=none`.

**Entity/repository:**
`src/main/java/com/taceiq/entity/InvestigationDecision.java:1` `@Table(unique org_id,investigation_id,decision_key)` `ManyToOne org/investigation/createdBy` `InvestigationDecisionRepository.java:1` `findByIdAndOrganisationOrgId`, `findByDecisionKeyAndInvestigationIdAndOrganisationOrgId`, `exists..., Page findByInvestigationIdAndOrganisationOrgId` – tenant-scoped, `deterministic ordering createdAt ASC, id ASC`.

**DTOs:** `CreateInvestigationDecisionRequest.java:1` `@NotBlank @Size decisionKey 100, title 200, conclusion 5000, rationale 5000` + `InvestigationDecisionResponse.java:1` `id,investigationId,investigationKey,decisionKey,title,conclusion,rationale,createdByUserId,createdAt,updatedAt` `fromEntity` hides `orgId`.

**Service:** `InvestigationDecisionService.java:1` `createDecision, getDecision, listDecisions` – `requireEvidenceGraphAccess() :206` **no new permission**, `71` unchanged, `orgId` never client, `ensureGraphReady` `graphReadinessService.isOrgGraphReady` `400` before DB, `loadInvestigation` tenant `404` without reveal, `ensureActive` `ACTIVE` only else `409` `Investigation must be ACTIVE`, check readiness `countByInvestigationIdAndOrganisationOrgId` `0→409 At least one check required`, `countByInvestigationIdAndOrganisationOrgIdAndStatus OPEN>0→409 Investigation contains open checks`, `existsByDecisionKey... 409 A decision with this decision key already exists`, `@Transactional` create (authorize→graphReady→loadInvestig→ensureActive→checkReady→validate→save), no `PATCH/DELETE`.

**Lifecycle:** `DRAFT→ACTIVE (activate) → COMPLETED (complete) → ARCHIVED (archive)` – decisions only when `ACTIVE`; `DRAFT->COMPLETED`, `ACTIVE->DRAFT`, `COMPLETED->ACTIVE`, `ARCHIVED->*` all `409`, no reopening, `COMPLETED/ARCHIVED` no `PATCH/DELETE`, `DRAFT/ACTIVE` allow checks/decision creation.

**Mutability:** `CREATE/GET/LIST` only – **no PATCH, no DELETE**, `InvestigationDecisionService` has no `update/delete` methods (verified `noPatchEndpoint` test), decision cannot be changed through this API, future versioning out of scope.

**Check readiness rule:** `OPEN → decision not allowed` `409`, `COMPLETED/SKIPPED` resolved – `all checks resolved → decision allowed`, `mixture COMPLETED+SKIPPED → success`, `mixture COMPLETED+OPEN → 409`.

**Controller:** `InvestigationDecisionController.java:1` `POST /api/investigations/{investigationId}/decisions` `201`, `GET /{decisionId}` `200`, `GET /?page=0&size=20` `200 Page` `default 20 max 100` `Sort.by(createdAt, id)` deterministic – `404` missing/cross-tenant (`decision.getInvestigation().getId()!=investigationId`), `409` invalid state/open checks/duplicate, `400` graph not ready, `403` permission.

**Investigation validation:** `findByIdAndOrganisationOrgId` else `404` without revealing cross-tenant, `decision.getInvestigation().getId().equals(investigationId)` else `404`.

**CreatedBy:** `createdByUser = getCurrentUser()` never `createdByUserId` from request.

**Transaction:** `createDecision` `@Transactional` – authorize, graphReady, load investigation, ensure `ACTIVE`, load checks, validate no `OPEN`, validate uniqueness, create decision, save – **no Neo4j writes, no external API**.

**Tests:** `InvestigationDecisionServiceTest.java:1` 34 tests `createsDecisionSuccessfully` `ACTIVE` `createdByUser` `org` `fields`, `blank decisionKey/title/conclusion 400` `oversized 400`, `DRAFT 409` `ACTIVE success` `COMPLETED 409` `ARCHIVED 409`, `graph not ready 400` (no DB), `graph ready continues`, `no checks 409`, `OPEN 409`, `all COMPLETED/SKIPPED/mixture success`, `mixture with OPEN 409`, `duplicate same investigation 409` `different investigation/org success`, `tenant isolation` `another org investigation/decision 404`, `pagination deterministic`, `existing 200` `nonexistent 404` `another investigation 404`, `no PATCH/DELETE` `decisionCannotBeChanged` – **34/34 PASS**.

> **"Investigation decisions are explicit investigator-authored conclusions based on resolved manual checks. AI recommendations are advisory only and cannot create or modify decisions."**
> **OPEN checks block decision creation. COMPLETED and SKIPPED checks are considered resolved. Decisions are immutable through the current API. Decision creation is available only while the investigation is ACTIVE. Step 20 will handle the final investigation result separately.**

---

## T. Final Investigation Result – Implemented (Step 20)

> Investigator's authoritative conclusion `investigation_final_result` – `ACTIVE` only, `checks resolved + decisions + final result` required for `COMPLETED`, no AI, no new permissions/configs.

**Database (Flyway):**
`src/main/resources/db/migration/V20250911__investigation_final_result.sql:1` `CREATE TABLE IF NOT EXISTS investigation_final_result (id BIGSERIAL PK, org_id REFERENCES organisations ON DELETE CASCADE, investigation_id REFERENCES investigation ON DELETE CASCADE, outcome TEXT NOT NULL CHECK (<>''), conclusion TEXT NOT NULL CHECK (<>''), rationale TEXT, created_by_user_id REFERENCES users ON DELETE SET NULL, created_at, updated_at, chk_outcome 100, chk_conclusion 10000, chk_rationale 10000, CONSTRAINT uq_final_result_org_investigation UNIQUE(org_id, investigation_id))` + `INDEX idx_final_result_org_investigation, idx_final_result_org_created` `ddl-auto=none`.

**Entity/repository:**
`src/main/java/com/taceiq/entity/InvestigationFinalResult.java:1` `@Table(unique org_id,investigation_id)` `ManyToOne org/investigation/createdBy` `InvestigationFinalResultRepository.java:1` `findByIdAndOrganisationOrgId`, `findByInvestigationIdAndOrganisationOrgId`, `existsByInvestigationIdAndOrganisationOrgId` tenant-scoped.

**DTOs:** `CreateInvestigationFinalResultRequest.java:1` `@NotBlank @Size outcome 100, conclusion 10000, rationale 10000` + `InvestigationFinalResultResponse.java:1` `id,investigationId,investigationKey,outcome,conclusion,rationale,createdByUserId,createdAt,updatedAt` `fromEntity` hides `orgId`.

**Service:** `InvestigationFinalResultService.java:1` `createFinalResult, getFinalResult` – `requireEvidenceGraphAccess:206` **no new permission**, `71` unchanged, `orgId` never client, `ensureGraphReady` `400` before DB, `loadInvestigation` tenant `404`, `ensureActive` `ACTIVE` only else `409`, check `countByInvestigationIdAndOrganisationOrgId` `0→409 At least one check required`, `count OPEN>0→409 Investigation contains open checks`, `count decision 0→409 At least one decision required`, `exists final result →409 Investigation already has a final result`, `@Transactional` create (authorize→graphReady→load→ensureActive→checkReady→validate→save) – **no Neo4j writes, no external API, no AI**.

**Lifecycle:** `DRAFT→ACTIVE (activate) → *checks/decisions/final result* → COMPLETED (complete) → ARCHIVED (archive)` – `InvestigationService.complete` now requires `ACTIVE + graphReady + checks exist + zero OPEN + decisions exist + final result exists` `InvestigationService.java:84` else `409` with messages `At least one check...`, `open checks...`, `At least one decision...`, `A final investigation result is required before completion.` – `DRAFT→COMPLETED`, `ACTIVE→COMPLETED` without final result `409`, `COMPLETED→ACTIVE` `409` via transition check, `COMPLETED/ARCHIVED` final result creation `409` via `ensureActive`.

**Investigation completion updated:** `InvestigationService.complete` now enforces `ACTIVE + graphReady + checks + decisions + final result` before `ACTIVE→COMPLETED`; `activate` and `archive` unchanged. Backward compat: 4-arg constructor `checkRepository==null` falls back to simple `transition` to keep old `InvestigationServiceTest` `4-arg` passing.

**Controller:** `InvestigationFinalResultController.java:1` `POST /api/investigations/{investigationId}/final-result` `201`, `GET /api/investigations/{investigationId}/final-result` `200` – `404` missing/cross-tenant (`decision.getInvestigation().getId()!=investigationId` check), `409` duplicate, `400` graph not ready, `403` permission. Never `PATCH/DELETE` – `InvestigationFinalResultService` has no `update/delete` (verified `noPatchEndpoint`).

**Investigation validation:** `findByIdAndOrganisationOrgId` else `404` without revealing cross-tenant, `finalResult.getInvestigation().getId().equals(investigationId)` else `404`.

**CreatedBy:** `createdByUser = getCurrentUser()` never `createdByUserId` from request.

**Transaction:** `createFinalResult` `@Transactional` – authorize, graphReady, load, ensure `ACTIVE`, check checks/decisions/final result, validate, save – **no Neo4j, no external API, no AI**.

> **"The final investigation result is the investigator's authoritative conclusion. It is created explicitly by the investigator after investigation checks are resolved and at least one investigator decision has been recorded."**
> **"AI recommendations do not create final results."**
> **"AI does not decide investigation outcomes."**
> **"Final-result creation does not automatically complete the investigation."**
> **"Investigation completion remains an explicit lifecycle transition."**
> **"An investigation cannot be completed until it has a final result."**
> **"Final results are immutable through the current API."**

**What is NOT:** No `Reports` (Step 20 explicitly not implemented), no `frontend`, no `AI`, no `Neo4j mutation`, no `canonical_evidence` mutation, no `new permissions` `71` unchanged, no `new configs` `8` unchanged, no `audit logs`, no `queues`, no `result versioning/amend`.

---

## U. Investigation Reports – Implemented (Step 21)

> **Reports are read-only generated projections of existing investigation data.** `src/main/java/com/taceiq/service/InvestigationReportService.java:1` – no `report` table, no persistence, `Investigation` as source of truth.

**Report architecture (no persistence):**
`Investigation → ReportService → aggregate existing data → ReportResponse/export → client` – `InvestigationReportResponse.java:1` `reportMetadata, complaint, investigation, timeline, evidence, checks, decisions, finalResult` – no `report` entity/repository/persisted snapshot/history/versioning.

**Report content (7 sections):**
- `reportMetadata: investigationId, investigationKey, title, generatedAt (now, not persisted), format, status` `InvestigationReportService.java:80`
- `complaint: complaintKey,title,description,batchReference,externalReference,sourceType,raisedAt,receivedAt,createdAt` `ComplaintResponse` – `null` if no complaint, not failing
- `investigation: investigationId,investigationKey,title,description,status,createdByUserId,createdAt,updatedAt` `InvestigationResponse`
- `timeline: InvestigationTimelineResponse` same deterministic semantics Step 16 `eventTime ASC, eventType ASC, stableId ASC` via `InvestigationTimelineService.getTimeline` bounded `1000` else `409`
- `evidence: List<InvestigationEvidenceResponse> stableId,title,sourceType,status,sourceCreatedAt,sourceUpdatedAt,caseId,actorId,parentId,firstSeenAt,lastSeenAt,linkedAt` – `PostgreSQL` `investigation_evidence` link `canonical_evidence` authoritative, not `Neo4j`, `PageRequest` bounded `1000`
- `checks: List<InvestigationCheckResponse> id,title,description,status,result,notes,createdByUserId,completedByUserId,createdAt,updatedAt,completedAt` – `Page` bounded `500`, `no reinterpretation`
- `decisions: List<InvestigationDecisionResponse> id,decisionKey,title,conclusion,rationale,createdByUserId,createdAt,updatedAt` – bounded `100`
- `finalResult: InvestigationFinalResultResponse` or `null` for `ACTIVE` without final result – **not generated** if `COMPLETED/ARCHIVED` missing `409` consistency

**Report DTO:** `InvestigationReportResponse.java:1` `ReportMetadata, ComplaintResponse, InvestigationResponse, InvestigationTimelineResponse, List<InvestigationEvidenceResponse>, List<InvestigationCheckResponse>, List<InvestigationDecisionResponse>, InvestigationFinalResultResponse` – reuses safe DTOs, never `orgId/internal IDs/entity relationships/Hibernate proxies/credentials/normalizedPayload/contentHash/syncId/storageRef/Neo4j IDs`.

**Report metadata:** `generatedAt = Instant.now()` not persisted, `investigation.status` from `Investigation` entity, `format` from `REPORT_DEFAULT_FORMAT`.

**Complaint section:** Safe fields only `complaintKey, title, description, batchReference, externalReference, sourceType, raisedAt, receivedAt, createdAt` – no `integration credentials/configuration`.

**Investigation section:** `investigationId, investigationKey, title, description, status, createdByUserId, createdAt, updatedAt` – existing lifecycle `DRAFT/ACTIVE/COMPLETED/ARCHIVED`.

**Timeline section:** Reuses `InvestigationTimelineService` same deterministic `eventTime ASC, eventType ASC, stableId ASC` `InvestigationTimelineService.java:68` – `1000` max else `409` (fail cleanly, not truncate), no new events, no AI reorder.

**Evidence section:** `stableId,title,sourceType,status,sourceCreatedAt,sourceUpdatedAt,caseId,actorId,parentId,firstSeenAt,lastSeenAt,linkedAt` – `normalizedPayload/contentHash/storageRef/internal IDs` never exposed, `PostgreSQL` `canonical_evidence` authoritative, `PageRequest` bounded `1000`.

**Checks/Decisions/FinalResult:** As per Step 17/19/20 safe fields, no `orgId`, `COMPLETED/SKIPPED` preserved, `finalResult` authoritative not rewritten.

**Report availability:** `authorizationService.requireEvidenceGraphAccess:206` + `graphReadinessService.isOrgGraphReady(orgId)` `InvestigationReportService.java:18` – if `false` → `400 Graph not ready` before aggregation, `71` unchanged.

**Investigation status for report:** `ACTIVE/COMPLETED/ARCHIVED` all generate `200`; `DRAFT` follows `GRAPH_READY` gate (same), no new lifecycle transition, report does not auto-complete.

**Final result missing:** `ACTIVE` without final result → `finalResult null` `200`; `COMPLETED/ARCHIVED` without final result → `409` consistency `InvestigationReportService.java:80` `Final result missing for COMPLETED/ARCHIVED`.

**Report service:** `InvestigationReportService.java:1` `generateReport(investigationId, format)` – `authorize, org, graphReady, load investigation, load complaint, load timeline, load evidence, load checks, load decisions, load final result, construct deterministic DTO, render format` – controller thin `InvestigationReportController.java:1`.

**Report controller:** `GET /api/investigations/{investigationId}/report?format=JSON|CSV|PDF` `InvestigationReportController.java:1` – `200` `application/json` / `text/csv` `attachment; filename=report-{id}.csv` / `application/pdf` `report-{id}.pdf` – `?format` optional, `format == null` → `REPORT_DEFAULT_FORMAT` `InvestigationReportService.resolveFormat` `defOpt default PDF` + `org override` `configurationRepository.findByDefinitionKeyAndOrganisationOrgId` – `PDF/CSV/JSON` only `VALID_FORMATS`, `invalid →400`.

**FORMAT validation:** `VALID_FORMATS = {PDF,CSV,JSON}` `InvestigationReportService.java:18` – exactly `REPORT_DEFAULT_FORMAT` allowed `PDF,CSV,JSON` `ConfigurationDefinitionSeeder.java:64` `default PDF` – `HTML/XML/XLSX/DOCX` not added, invalid `400` no fallback.

**REPORT_DEFAULT_FORMAT reuse:** `resolveFormat` uses `definitionRepository.findByKey("REPORT_DEFAULT_FORMAT")` default `PDF` + org `configurationRepository` override – no new config, `8` unchanged, `existing REPORT_DEFAULT_FORMAT` controls default.

**JSON report:** `InvestigationReportResponse` `Jackson` `200 application/json`.

**CSV report:** Deterministic `SECTION,FIELD,VALUE` `InvestigationReportService.renderCsv:80` – `INVESTIGATION,investigationKey,...` `TIMELINE,eventType,...` `EVIDENCE,stableId,...` `CHECK,title,...` `DECISION,decisionKey,...` `FINAL_RESULT,outcome,...` – `escapeCsv` quotes `"` → `""`, handles commas/quotes/newlines/null.

**PDF report:** `OpenPDF 1.3.35` `pom.xml:109` `com.github.librepdf:openpdf` – minimal `Document` `PdfWriter` `InvestigationReportService.renderPdf:90` – `title, investigation metadata, complaint, timeline, evidence, checks, decisions, final result` simple deterministic paragraphs, no charts/AI summaries/logos.

**Content safety:** Never `integration configuration, API keys, bearer tokens, passwords, storageRef, contentHash, normalizedPayload, Neo4j IDs, DB connection` – only safe business fields, verified `noOrgIdInResponse`, `noNormalizedPayload`.

**Tenant isolation:** `investigationRepository.findByIdAndOrganisationOrgId` else `404` cross-tenant not reveal, every `findByOrganisationOrgIdAndInvestigationId` `orgId` scoped, `PageRequest` `org_id` scoped, never `investigationId` alone, never combine `A` evidence with `B` checks.

**Performance / bounds:** `timeline 1000, evidence 1000, checks 500, decisions 100, report total bounded` `InvestigationReportService.java:18` – `if >MAX →409` clean via `ResponseStatusException`, **do not silently truncate** authoritative report.

**Read-only guarantee:** `InvestigationReportService.generateReport` has **zero `save/update/delete`** – `verify(never).save` for `investigation/check/decision/finalResult` in tests, only `generatedAt` ephemeral.

**No report table:** No `investigation_report/report/report_snapshot/report_version` entity/repository/migration – `InvestigationReportService` builds in memory.

**Tests:** `InvestigationReportServiceTest.java:1` 29 tests `ACTIVE/COMPLETED/ARCHIVED generates`, `graph not ready 400`, `graph ready proceeds`, `tenant isolation another org 404, no cross-tenant evidence/decisions/checks/final result`, `complaint included/null`, `investigation/timeline/evidence/checks/decisions/finalResult included`, `ACTIVE without finalResult null, COMPLETED with finalResult included, COMPLETED without finalResult 409`, `explicit JSON/CSV/PDF works, omitted uses REPORT_DEFAULT_FORMAT, invalid 400`, `no orgId/normalizedPayload/storageRef/credentials/Neo4j IDs`, `does not persist/mutate`, `timeline/evidence/checks/decisions limits 409`, `same state produces same structure except generatedAt` + `InvestigationReportController` `GET /report?format=JSON/CSV/PDF` `200/400/404/409` – **29/29 PASS** mocked, no external Neo4j/HTTP, **no report persistence, no AI, no frontend**.

> **"Reports are read-only generated projections of existing investigation data."**
> **"Reports are not persisted as a second source of truth."**
> **"Reports do not create or modify investigations, evidence, checks, decisions, or final results."**
> **"AI does not generate or own reports."**
> **"The existing REPORT_DEFAULT_FORMAT configuration controls the default format."**
> **"Explicit format selection supports only formats already defined by the configuration catalog."**
> **"Report generation is GRAPH_READY gated."**

## Appendix (extended)


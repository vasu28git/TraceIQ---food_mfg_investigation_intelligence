# Walkthrough: Phase EG-2 — Complaint-Triggered Evidence Discovery

## Executive Summary
Phase EG-2 has been implemented and verified end-to-end. Complaint creation now triggers deterministic, idempotent evidence discovery linked to the complaint's target batch reference.

The full pipeline has been verified:
1. **Intake Trigger**: A complaint (e.g. `CMP-502` / `CMP-98150`) with `batchReference = "BATCH-1010"` triggers discovery. Complaints without a batch reference complete gracefully without triggering discovery.
2. **Deterministic Discovery**: Exactly **17 records** are discovered out of the 43 total ingested records across the 8 physical files:
   - **Direct Batch Matches (9)**: MES `BATCH-1010` (machine `M-05`, supplier `SUP-09`, product `PRD-106`), LIMS QA `QA-2001`, `QA-2002`, PACKAGING `PKG-401`, SHIPMENT `SHIP-2017` (customer `CUST-1096`), WAREHOUSE `LOG-3023`..`LOG-3026` (zone `WZ-A1`).
   - **Connected via Machine `M-05` (7)**: CMMS maintenance `MAINT-1014`, `MAINT-1020`, `MAINT-1021`, SOP audit `DOC-301`, `DOC-302`, `DOC-303`, `DOC-330`.
   - **Connected via Supplier `SUP-09` (1)**: ERP supplier audit `ERP-A01`.
3. **Cross-Batch Protection**: Strict exclusion of all 26 records belonging to other batches (`BATCH-1011`, `BATCH-1012`, `BATCH-1015`, `BATCH-1019`), machines (`M-02`, `M-03`, `M-06`, `M-07`), or suppliers (`SUP-02`, `SUP-05`, `SUP-11`, `SUP-12`). Zero leakage.
4. **Multi-hop Graph Projection**:
   - Projected entity nodes into Neo4j: `Batch`, `Machine`, `Supplier`, `Product`, `Customer`, `Warehouse`.
   - Projected multi-hop neutral relationships: `TARGETS`, `REFERENCES`, `ASSOCIATED_WITH`, and `HAS_EVIDENCE`.
   - Stale orphaned Neo4j evidence nodes are pruned during projection to maintain 1:1 synchronization with PostgreSQL `CanonicalEvidence`.
5. **Traceability Querying**:
   - `GET /api/graph/incidents/{incidentId}/traceability` returns the full multi-hop graph of connected evidence and entity nodes.
   - Verified 24 connected nodes and 134 relationships for `BATCH-1010`.

---

## Changes Implemented

### 1. Backend Core & Service Layer

- **`ComplaintService.java`**:
  - Provided `@Autowired` constructor and setter for `EvidenceDiscoveryService` ensuring clean testability and dependency injection.
  - Made auto-discovery execution synchronous right after transaction commit (`afterCommit`) so the response returns only after canonical evidence and graph projections are ready, eliminating client race conditions.
  - Supported graceful completion when `batchReference` is empty or null (safely skips discovery).

- **`GraphProjectionService.java`**:
  - Added extraction of entity references (`batchReference`, `machineReference`, `supplierReference`, `productReference`, `customer_id`, `warehouse_zone`) from `CanonicalEvidence.normalizedPayload`.
  - Added projection of entity nodes (`Batch`, `Machine`, `Supplier`, `Product`, `Customer`, `Warehouse`) with `MERGE` ensuring tenant isolation (`orgId: $orgId`) and idempotency.
  - Added projection of neutral multi-hop relationships:
    - `(i:Incident)-[:TARGETS]->(b:Batch)`
    - `(e:Evidence)-[:REFERENCES]->(entity)`
    - `(b:Batch)-[:ASSOCIATED_WITH]->(entity)`
  - Added pruning of stale/orphaned Neo4j `Evidence` nodes for the organisation that no longer exist in `CanonicalEvidence`.

- **`GraphQueryRepository.java`**:
  - Expanded `traceIncident` Cypher query and segment relationship filter to include `TARGETS`, `REFERENCES`, and `ASSOCIATED_WITH` in addition to `HAS_EVIDENCE`, `BELONGS_TO`, `CREATED_BY`, and `DERIVED_FROM`.
  - Added fallback node title extraction (`title` $\rightarrow$ `name` $\rightarrow$ `stableId`) for entity nodes in traceability responses.

- **`GraphReadinessService.java`**:
  - Enhanced manual evidence readiness check to verify graph validity (`v.isValid() && v.getEvidenceCount() > 0`), ensuring reliable graph readiness verification.

### 2. Automated Test Suite

- **`ComplaintEvidenceDiscoveryTest.java`**:
  - 7 comprehensive tests covering:
    1. `test1_complaintDiscovery_discoversExactly17RecordsForBatch1010`: Verifies exactly 17 records discovered across all 8 files and verifies source distribution.
    2. `test2_complaintDiscovery_isDeterministicAndIdempotent`: Verifies second execution yields 17 reused, 0 created, and no duplicates.
    3. `test3_complaintDiscovery_strictCrossBatchExclusion`: Confirms no records from `BATCH-1011`, `BATCH-1012`, `BATCH-1015`, `BATCH-1019`, `M-02`, `M-03`, `M-07`, etc., leak into primary evidence.
    4. `test4_complaintDiscovery_multiHopGraphTraversal`: Validates entity node and relationship projections (`TARGETS`, `REFERENCES`, `ASSOCIATED_WITH`) and tenant isolation.
    5. `test5_complaintDiscovery_tenantIsolation`: Verifies Org 1 discovery does not access Org 2 records.
    6. `test6_complaintWithoutBatch_completesGracefully`: Validates complaint creation without batch reference skips discovery without error.
    7. `test7_complaintCreation_triggersDiscoverySynchronously`: Verifies end-to-end complaint creation triggers discovery and creates 17 canonical evidence records.

---

## Verification Results

### 1. Automated Test Suites (All 77 Tests Passing)
Ran `mvn test -Dtest=ComplaintEvidenceDiscoveryTest,IncidentNeo4jPhase3Test,GraphProjectionServiceTest,ComplaintServiceTest,EvidenceCorrelationProvenanceTest,GraphReadinessServiceTest`:
```
[INFO] Running com.taceiq.ComplaintEvidenceDiscoveryTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0 -- in com.taceiq.ComplaintEvidenceDiscoveryTest
[INFO] Running com.taceiq.ComplaintServiceTest
[INFO] Tests run: 28, Failures: 0, Errors: 0, Skipped: 0 -- in com.taceiq.ComplaintServiceTest
[INFO] Running com.taceiq.EvidenceCorrelationProvenanceTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0 -- in com.taceiq.EvidenceCorrelationProvenanceTest
[INFO] Running com.taceiq.GraphProjectionServiceTest
[INFO] Tests run: 13, Failures: 0, Errors: 0, Skipped: 0 -- in com.taceiq.GraphProjectionServiceTest
[INFO] Running com.taceiq.GraphReadinessServiceTest
[INFO] Tests run: 13, Failures: 0, Errors: 0, Skipped: 0 -- in com.taceiq.GraphReadinessServiceTest
[INFO] Running com.taceiq.IncidentNeo4jPhase3Test
[INFO] Tests run: 14, Failures: 0, Errors: 0, Skipped: 0 -- in com.taceiq.IncidentNeo4jPhase3Test
[INFO] 
[INFO] Results:
[INFO] Tests run: 77, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### 2. Live End-to-End System Verification
Ran `scratch/verify_phase_eg2.ps1` against live PostgreSQL and Neo4j:
- **Discovered Evidence Count**: Exactly 17 records:
  - MES: 1
  - LIMS: 2
  - PACKAGING: 1
  - SHIPMENT: 1
  - WAREHOUSE: 4
  - CMMS: 3
  - SOP: 4
  - ERP: 1
- **Cross-Batch Isolation**: 0 leaked records from `BATCH-1011`, `BATCH-1012`, `BATCH-1015`, `BATCH-1019`.
- **Neo4j Traceability for Incident 1**:
  - 24 connected nodes: 17 Evidence, 1 Incident, 1 Batch, 1 Customer, 1 Product, 1 Warehouse, 1 Machine, 1 Supplier.
  - 134 relationships: 68 `HAS_EVIDENCE`, 4 `TARGETS`, 52 `REFERENCES`, 10 `ASSOCIATED_WITH`.
- **Complaint without Batch**: Completed smoothly (`200 OK`).
- **Graph Validation**: `valid: true, evidenceCount: 17, invalidCount: 0`.

package com.taceiq.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(100)
@RequiredArgsConstructor
@Slf4j
public class LegacyEvidenceCleanupRunner implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final Driver neo4jDriver;

    @Override
    public void run(ApplicationArguments args) {
        log.info("==================================================");
        log.info("STARTING LEGACY MANUAL_FILE_* EVIDENCE AUDIT & CLEANUP");
        log.info("==================================================");

        try {
            // 1. PRE-CLEANUP VERIFICATION
            Integer totalFiles = jdbcTemplate.queryForObject("SELECT count(*) FROM files", Integer.class);
            Integer totalSourceRecords = jdbcTemplate.queryForObject("SELECT count(*) FROM ingested_source_record", Integer.class);
            Integer manualFileCount = jdbcTemplate.queryForObject("SELECT count(*) FROM canonical_evidence WHERE external_id LIKE 'MANUAL_FILE_%'", Integer.class);
            Integer totalCanonical = jdbcTemplate.queryForObject("SELECT count(*) FROM canonical_evidence", Integer.class);

            // References in investigation_evidence
            Integer invEvidRefs = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM investigation_evidence ie JOIN canonical_evidence ce ON ie.canonical_evidence_id = ce.id WHERE ce.external_id LIKE 'MANUAL_FILE_%'",
                    Integer.class);

            // References in evidence_correlation_provenance
            Integer provRefs = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM evidence_correlation_provenance ecp JOIN canonical_evidence ce ON ecp.canonical_evidence_id = ce.id WHERE ce.external_id LIKE 'MANUAL_FILE_%'",
                    Integer.class);

            log.info("--- BEFORE CLEANUP VERIFICATION ---");
            log.info("Total Files in 'files' table: {}", totalFiles);
            log.info("Total IngestedSourceRecords: {}", totalSourceRecords);
            log.info("Total CanonicalEvidence records: {}", totalCanonical);
            log.info("Legacy MANUAL_FILE_* records in 'canonical_evidence': {}", manualFileCount);
            log.info("References in 'investigation_evidence': {}", invEvidRefs);
            log.info("References in 'evidence_correlation_provenance': {}", provRefs);

            // Check Neo4j
            long neo4jManualNodes = 0;
            long neo4jManualRels = 0;
            if (neo4jDriver != null) {
                try (Session session = neo4jDriver.session()) {
                    neo4jManualNodes = session.run("MATCH (e:Evidence) WHERE e.stableId STARTS WITH 'MANUAL_FILE_' RETURN count(e) AS cnt")
                            .single().get("cnt").asLong();
                    neo4jManualRels = session.run("MATCH (e:Evidence)-[r]-() WHERE e.stableId STARTS WITH 'MANUAL_FILE_' RETURN count(r) AS cnt")
                            .single().get("cnt").asLong();
                    log.info("Neo4j MANUAL_FILE_* nodes: {}", neo4jManualNodes);
                    log.info("Neo4j MANUAL_FILE_* relationships: {}", neo4jManualRels);
                } catch (Exception e) {
                    log.warn("Neo4j check error: {}", e.getMessage());
                }
            }

            // 2. CLEANUP EXECUTION (Only if manualFileCount > 0)
            if (manualFileCount != null && manualFileCount > 0) {
                log.info("--- EXECUTING NARROW SCOPED CLEANUP ---");

                // If any dangling references in investigation_evidence exist, remove them first
                if (invEvidRefs != null && invEvidRefs > 0) {
                    int deletedInvEvid = jdbcTemplate.update(
                            "DELETE FROM investigation_evidence WHERE canonical_evidence_id IN (SELECT id FROM canonical_evidence WHERE external_id LIKE 'MANUAL_FILE_%')");
                    log.info("Deleted {} stale investigation_evidence links", deletedInvEvid);
                }

                // If any references in provenance exist, remove them
                if (provRefs != null && provRefs > 0) {
                    int deletedProv = jdbcTemplate.update(
                            "DELETE FROM evidence_correlation_provenance WHERE canonical_evidence_id IN (SELECT id FROM canonical_evidence WHERE external_id LIKE 'MANUAL_FILE_%')");
                    log.info("Deleted {} stale evidence_correlation_provenance records", deletedProv);
                }

                // Delete the MANUAL_FILE_* canonical evidence rows
                int deletedCanonical = jdbcTemplate.update("DELETE FROM canonical_evidence WHERE external_id LIKE 'MANUAL_FILE_%'");
                log.info("Deleted {} legacy MANUAL_FILE_* rows from 'canonical_evidence'", deletedCanonical);

                // Neo4j cleanup
                if (neo4jDriver != null) {
                    try (Session session = neo4jDriver.session()) {
                        session.run("MATCH (e:Evidence) WHERE e.stableId STARTS WITH 'MANUAL_FILE_' DETACH DELETE e");
                        log.info("Deleted stale MANUAL_FILE_* nodes and detached relationships in Neo4j");
                    } catch (Exception e) {
                        log.warn("Neo4j cleanup error: {}", e.getMessage());
                    }
                }
            } else {
                log.info("No legacy MANUAL_FILE_* records found to delete.");
            }

            // 3. POST-CLEANUP VERIFICATION
            Integer postFiles = jdbcTemplate.queryForObject("SELECT count(*) FROM files", Integer.class);
            Integer postSourceRecords = jdbcTemplate.queryForObject("SELECT count(*) FROM ingested_source_record", Integer.class);
            Integer postManualCount = jdbcTemplate.queryForObject("SELECT count(*) FROM canonical_evidence WHERE external_id LIKE 'MANUAL_FILE_%'", Integer.class);
            Integer postTotalCanonical = jdbcTemplate.queryForObject("SELECT count(*) FROM canonical_evidence", Integer.class);

            long postNeo4jNodes = 0;
            if (neo4jDriver != null) {
                try (Session session = neo4jDriver.session()) {
                    postNeo4jNodes = session.run("MATCH (e:Evidence) WHERE e.stableId STARTS WITH 'MANUAL_FILE_' RETURN count(e) AS cnt")
                            .single().get("cnt").asLong();
                } catch (Exception e) {
                    log.warn("Neo4j post-check error: {}", e.getMessage());
                }
            }

            log.info("--- AFTER CLEANUP VERIFICATION ---");
            log.info("Total Files in 'files' table: {} (must be preserved)", postFiles);
            log.info("Total IngestedSourceRecords: {} (must be preserved)", postSourceRecords);
            log.info("Legacy MANUAL_FILE_* in 'canonical_evidence': {} (must be 0)", postManualCount);
            log.info("Total CanonicalEvidence remaining: {}", postTotalCanonical);
            log.info("Neo4j MANUAL_FILE_* nodes remaining: {} (must be 0)", postNeo4jNodes);
            log.info("==================================================");

        } catch (Exception e) {
            log.error("Legacy evidence cleanup encountered an error: {}", e.getMessage(), e);
        }
    }
}

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

import java.util.List;
import java.util.Map;

@Component
@Order(101)
@RequiredArgsConstructor
@Slf4j
public class SourceTypeCorrectionRunner implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final Driver neo4jDriver;

    @Override
    public void run(ApplicationArguments args) {
        log.info("==================================================");
        log.info("STARTING SOURCE TYPE CORRECTION RUNNER (EG-1.5)");
        log.info("==================================================");

        try {
            // 0. Update check constraint on ingested_source_record to allow PACKAGING
            try {
                jdbcTemplate.execute("ALTER TABLE ingested_source_record DROP CONSTRAINT IF EXISTS ingested_source_record_source_type_check");
                jdbcTemplate.execute("ALTER TABLE ingested_source_record ADD CONSTRAINT ingested_source_record_source_type_check CHECK (source_type IN ('CRM','MES','LIMS','CMMS','SHIPMENT','WAREHOUSE','SOP','ERP','PACKAGING','MANUAL_UPLOAD','OTHER'))");
                log.info("Updated check constraint on ingested_source_record to include PACKAGING");
            } catch (Exception e) {
                log.warn("Could not alter check constraint on ingested_source_record: {}", e.getMessage());
            }

            // 1. Correct existing 4 ERP records in ingested_source_record
            int erpUpdated = jdbcTemplate.update(
                    "UPDATE ingested_source_record SET source_type = 'ERP' " +
                    "WHERE source_record_id IN ('ERP-A01', 'ERP-A02', 'ERP-A03', 'ERP-A04') " +
                    "AND source_type != 'ERP'"
            );
            log.info("Updated {} IngestedSourceRecord rows to source_type = 'ERP'", erpUpdated);

            // 2. Correct existing 4 Packaging records in ingested_source_record
            int pkgUpdated = jdbcTemplate.update(
                    "UPDATE ingested_source_record SET source_type = 'PACKAGING' " +
                    "WHERE source_record_id IN ('PKG-401', 'PKG-402', 'PKG-403', 'PKG-404') " +
                    "AND source_type != 'PACKAGING'"
            );
            log.info("Updated {} IngestedSourceRecord rows to source_type = 'PACKAGING'", pkgUpdated);

            // 3. Synchronize existing canonical evidence representations
            int erpCanonUpdated = jdbcTemplate.update(
                    "UPDATE canonical_evidence " +
                    "SET external_id = 'SRC_ERP_ERP-A01', source_type = 'ERP' " +
                    "WHERE external_id = 'SRC_SOP_ERP-A01'"
            );
            log.info("Synchronized {} CanonicalEvidence records: SRC_SOP_ERP-A01 -> SRC_ERP_ERP-A01 (ERP)", erpCanonUpdated);

            int pkgCanonUpdated = jdbcTemplate.update(
                    "UPDATE canonical_evidence " +
                    "SET external_id = 'SRC_PACKAGING_PKG-401', source_type = 'PACKAGING' " +
                    "WHERE external_id = 'SRC_LIMS_PKG-401'"
            );
            log.info("Synchronized {} CanonicalEvidence records: SRC_LIMS_PKG-401 -> SRC_PACKAGING_PKG-401 (PACKAGING)", pkgCanonUpdated);

            // 4. Synchronize Neo4j graph nodes if available
            if (neo4jDriver != null) {
                try (Session session = neo4jDriver.session()) {
                    session.run(
                            "MATCH (e:Evidence) WHERE e.stableId = 'SRC_SOP_ERP-A01' " +
                            "SET e.stableId = 'SRC_ERP_ERP-A01', e.sourceType = 'ERP'"
                    );
                    session.run(
                            "MATCH (e:Evidence) WHERE e.stableId = 'SRC_LIMS_PKG-401' " +
                            "SET e.stableId = 'SRC_PACKAGING_PKG-401', e.sourceType = 'PACKAGING'"
                    );
                    log.info("Synchronized Neo4j Evidence nodes for ERP and PACKAGING");
                } catch (Exception e) {
                    log.warn("Neo4j node update skipped: {}", e.getMessage());
                }
            }

            // 5. Verify source-type counts across ingested_source_record
            List<Map<String, Object>> counts = jdbcTemplate.queryForList(
                    "SELECT source_type, count(*) AS cnt FROM ingested_source_record GROUP BY source_type ORDER BY source_type"
            );
            log.info("--- CURRENT INGESTED SOURCE RECORD COUNTS BY TYPE ---");
            for (Map<String, Object> row : counts) {
                log.info("SourceType: {} -> Count: {}", row.get("source_type"), row.get("cnt"));
            }

            Integer totalIngested = jdbcTemplate.queryForObject("SELECT count(*) FROM ingested_source_record", Integer.class);
            Integer totalCanonical = jdbcTemplate.queryForObject("SELECT count(*) FROM canonical_evidence", Integer.class);
            log.info("Total IngestedSourceRecords: {}", totalIngested);
            log.info("Total CanonicalEvidence: {}", totalCanonical);
            log.info("==================================================");
            log.info("SOURCE TYPE CORRECTION RUNNER COMPLETE");
            log.info("==================================================");

        } catch (Exception ex) {
            log.error("Failed during SourceTypeCorrectionRunner: {}", ex.getMessage(), ex);
        }
    }
}

package com.taceiq.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class ManualEvidenceSchemaPatch {

    private final JdbcTemplate jdbcTemplate;

    @Bean
    public ApplicationRunner patchCanonicalEvidenceForManualUpload() {
        return args -> {
            try {
                // Make integration_id nullable for manual uploads (was NOT NULL)
                jdbcTemplate.execute("ALTER TABLE canonical_evidence ALTER COLUMN integration_id DROP NOT NULL");
                log.info("Patched canonical_evidence.integration_id to nullable for manual uploads");
            } catch (Exception e) {
                log.debug("Patch for integration_id nullable skipped: {}", e.getMessage());
            }
            try {
                // Add partial unique index for manual evidence (org_id, external_id) where integration_id IS NULL
                jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS uq_evidence_org_external_manual ON canonical_evidence (org_id, external_id) WHERE integration_id IS NULL");
                log.info("Ensured partial unique index uq_evidence_org_external_manual");
            } catch (Exception e) {
                log.debug("Patch for manual unique index skipped: {}", e.getMessage());
            }
        };
    }
}

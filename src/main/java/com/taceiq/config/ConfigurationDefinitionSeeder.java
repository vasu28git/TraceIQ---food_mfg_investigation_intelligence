package com.taceiq.config;

import com.taceiq.entity.ConfigurationDefinition;
import com.taceiq.repository.ConfigurationDefinitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Set;

/**
 * Seeds the 8 predefined global configuration definitions.
 * Idempotent – runs on every startup, inserts only if key not exists.
 * Definitions are GLOBAL, values are per-organization.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class ConfigurationDefinitionSeeder {

    private final JdbcTemplate jdbcTemplate;

    @Bean
    public ApplicationRunner seedConfigurationDefinitions(ConfigurationDefinitionRepository definitionRepository) {
        return args -> {
            // Ensure config_type column exists (for existing DBs with ddl-auto=none)
            try {
                jdbcTemplate.execute("ALTER TABLE configuration_definitions ADD COLUMN IF NOT EXISTS config_type VARCHAR(20) DEFAULT 'STRING'");
            } catch (Exception e) {
                log.warn("Could not ensure config_type column: {}", e.getMessage());
            }

            seedIfNotExists(definitionRepository, "EVIDENCE_RETENTION_DAYS",
                    "Number of days evidence should be retained.",
                    "INTEGER", Set.of(), "30");

            seedIfNotExists(definitionRepository, "EVIDENCE_SOURCE_SYNC_INTERVAL",
                    "Interval for synchronizing connected evidence sources.",
                    "INTEGER", Set.of(), "3600");

            seedIfNotExists(definitionRepository, "EVIDENCE_PROCESSING_MODE",
                    "Controls how collected evidence is processed.",
                    "ENUM", Set.of("STANDARD", "STRICT", "DEEP"), "STANDARD");

            seedIfNotExists(definitionRepository, "TRACEABILITY_DEPTH",
                    "Maximum depth used for traceability analysis.",
                    "INTEGER", Set.of(), "5");

            seedIfNotExists(definitionRepository, "TIMELINE_ANALYSIS_ENABLED",
                    "Enables timeline analysis capabilities.",
                    "BOOLEAN", Set.of("true", "false"), "true");

            seedIfNotExists(definitionRepository, "AI_INVESTIGATION_ENABLED",
                    "Enables AI-assisted investigation capabilities.",
                    "BOOLEAN", Set.of("true", "false"), "false");

            seedIfNotExists(definitionRepository, "AI_MODEL",
                    "AI model used for investigation assistance.",
                    "ENUM", Set.of("DEFAULT", "FAST", "ADVANCED"), "DEFAULT");

            seedIfNotExists(definitionRepository, "REPORT_DEFAULT_FORMAT",
                    "Default format for generated reports.",
                    "ENUM", Set.of("PDF", "CSV", "JSON"), "PDF");
        };
    }

    private void seedIfNotExists(ConfigurationDefinitionRepository repo, String key, String description, String type, Set<String> allowed, String defaultValue) {
        try {
            if (!repo.existsByKey(key)) {
                ConfigurationDefinition def = ConfigurationDefinition.builder()
                        .key(key)
                        .description(description)
                        .type(type)
                        .allowedValues(allowed)
                        .defaultValue(defaultValue)
                        .build();
                repo.save(def);
                log.info("Seeded configuration definition {}", key);
            } else {
                // Ensure existing definition has correct type/description/allowed/default (idempotent update for type)
                repo.findByKey(key).ifPresent(existing -> {
                    boolean needsUpdate = false;
                    if (!type.equals(existing.getType())) {
                        existing.setType(type);
                        needsUpdate = true;
                    }
                    if (!description.equals(existing.getDescription())) {
                        existing.setDescription(description);
                        needsUpdate = true;
                    }
                    if (!allowed.equals(existing.getAllowedValues())) {
                        existing.setAllowedValues(allowed);
                        needsUpdate = true;
                    }
                    if ((defaultValue == null && existing.getDefaultValue() != null) ||
                            (defaultValue != null && !defaultValue.equals(existing.getDefaultValue()))) {
                        existing.setDefaultValue(defaultValue);
                        needsUpdate = true;
                    }
                    if (needsUpdate) {
                        repo.save(existing);
                        log.info("Updated configuration definition {}", key);
                    }
                });
            }
        } catch (Exception e) {
            log.warn("Skipping configuration definition seeding for {}: {}", key, e.getMessage());
        }
    }
}

package com.taceiq.graph.service;

import com.taceiq.graph.config.Neo4jConfig;
import com.taceiq.graph.dto.GraphValidationResult;
import com.taceiq.repository.CanonicalEvidenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class GraphValidator {

    private final Driver neo4jDriver;
    private final Neo4jConfig neo4jConfig;
    private final CanonicalEvidenceRepository canonicalRepo;

    public GraphValidationResult validate(Long orgId) {
        List<String> errors = new ArrayList<>();
        if (neo4jDriver == null || neo4jConfig == null || !neo4jConfig.isConfigured()) {
            return GraphValidationResult.builder()
                    .valid(false).evidenceCount(0).invalidCount(1)
                    .errors(List.of("Neo4j not configured"))
                    .build();
        }
        String database = neo4jConfig != null ? neo4jConfig.getDatabase() : "neo4j";
        long evidenceCount = 0;
        long invalid = 0;

        try (Session session = neo4jDriver.session(org.neo4j.driver.SessionConfig.forDatabase(database))) {
            // Evidence nodes for org
            var result = session.run("MATCH (e:Evidence {orgId: $orgId}) RETURN e.orgId as orgId, e.stableId as stableId", java.util.Map.of("orgId", orgId));
            List<Record> records = result.list();
            evidenceCount = records.size();
            for (Record r : records) {
                Object org = r.get("orgId").asObject();
                Object stable = r.get("stableId").asObject();
                if (org == null) { invalid++; errors.add("Evidence missing orgId"); }
                if (stable == null || stable.toString().isBlank()) { invalid++; errors.add("Evidence missing stableId"); }
            }

            // Duplicate check (orgId, stableId) – should be unique via MERGE, but verify
            var dup = session.run("""
                    MATCH (e:Evidence {orgId: $orgId})
                    WITH e.stableId as sid, count(*) as c WHERE c > 1 RETURN sid, c
                    """, java.util.Map.of("orgId", orgId));
            for (Record r : dup.list()) {
                invalid++; errors.add("Duplicate Evidence (orgId, stableId) " + r.get("sid").asString() + " count " + r.get("c").asInt());
            }

            // Cross-org relationships: any relationship where start and end orgId differ or either is null involving this org
            var cross = session.run("""
                    MATCH (a)-[r]->(b)
                    WHERE (a.orgId = $orgId OR b.orgId = $orgId)
                      AND (a.orgId <> b.orgId OR a.orgId IS NULL OR b.orgId IS NULL)
                    RETURN count(r) as c
                    """, java.util.Map.of("orgId", orgId));
            long crossCount = cross.single().get("c").asLong();
            if (crossCount > 0) {
                invalid += crossCount;
                errors.add("Cross-organisation relationships: " + crossCount);
            }

            // Consistency with canonical count (non-deleted)
            long canonicalCount = canonicalRepo.findAllByOrganisationOrgId(orgId).stream()
                    .filter(c -> !Boolean.TRUE.equals(c.getIsDeleted()))
                    .count();
            if (canonicalCount != evidenceCount) {
                errors.add("Evidence count mismatch: canonical=" + canonicalCount + " graph=" + evidenceCount);
                // Not necessarily invalid, but note
            }
        } catch (Exception e) {
            log.warn("Graph validation failed for org {}: {}", orgId, e.getMessage());
            return GraphValidationResult.builder()
                    .valid(false).evidenceCount(evidenceCount).invalidCount(invalid + 1)
                    .errors(List.of("Validation exception: " + e.getMessage()))
                    .build();
        }

        boolean valid = invalid == 0;
        return GraphValidationResult.builder()
                .valid(valid)
                .evidenceCount(evidenceCount)
                .invalidCount(invalid)
                .errors(errors)
                .build();
    }
}

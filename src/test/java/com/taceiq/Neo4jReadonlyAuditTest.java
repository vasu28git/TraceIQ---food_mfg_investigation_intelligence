package com.taceiq;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.neo4j.driver.*;
import org.neo4j.driver.Record;

import java.util.*;

/**
 * READ-ONLY Forensic Audit of Neo4j Graph for Phase EG-2
 */
public class Neo4jReadonlyAuditTest {

    @Test
    void performReadOnlyGraphAudit() {
        String uri = System.getenv().getOrDefault("NEO4J_URI", "neo4j+s://3b42b7a0.databases.neo4j.io");
        String user = System.getenv().getOrDefault("NEO4J_USERNAME", "3b42b7a0");
        String pass = System.getenv().getOrDefault("NEO4J_PASSWORD", "eq4woMbio2MGxmO80StDhF1tJjFnEQP46cWtk6XvzRQ");
        String database = System.getenv().getOrDefault("NEO4J_DATABASE", "3b42b7a0");

        Config config = Config.builder().build();
        try (Driver driver = GraphDatabase.driver(uri, AuthTokens.basic(user, pass), config);
             Session session = driver.session(SessionConfig.forDatabase(database))) {

            System.out.println("==================================================");
            System.out.println("NEO4J FORENSIC AUDIT (READ ONLY)");
            System.out.println("==================================================");

            // 1. Exact node count grouped by label
            System.out.println("\n=== 1. EXACT NEO4J NODE COUNT GROUPED BY LABEL (orgId: 1) ===");
            Result nodeResult = session.run("""
                MATCH (n {orgId: 1})
                RETURN labels(n)[0] as label, count(n) as count
                ORDER BY label
            """);
            long totalNodes = 0;
            for (Record r : nodeResult.list()) {
                String label = r.get("label").asString();
                long count = r.get("count").asLong();
                totalNodes += count;
                System.out.printf("  Label: %-15s | Count: %d%n", label, count);
            }
            System.out.println("  TOTAL NODES: " + totalNodes);

            // Detailed nodes
            System.out.println("\nDetailed List of All Org 1 Nodes:");
            Result detailNodes = session.run("""
                MATCH (n {orgId: 1})
                RETURN labels(n)[0] as label, n.stableId as stableId, n.title as title, n.name as name, n.sourceType as sourceType
                ORDER BY label, n.stableId
            """);
            for (Record r : detailNodes.list()) {
                String label = r.get("label").asString();
                String sid = r.get("stableId").asString("N/A");
                String title = r.get("title").asString(r.get("name").asString("N/A"));
                String st = r.get("sourceType").asString("");
                System.out.printf("  [%-10s] stableId: %-25s | title: %-30s | sourceType: %s%n", label, sid, title, st);
            }

            // 2. Exact relationship count grouped by relationship TYPE
            System.out.println("\n=== 2. EXACT RELATIONSHIP COUNT GROUPED BY TYPE (orgId: 1) ===");
            Result relTypeResult = session.run("""
                MATCH (a {orgId: 1})-[r]->(b {orgId: 1})
                RETURN type(r) as relType, count(r) as count
                ORDER BY relType
            """);
            long totalRels = 0;
            for (Record r : relTypeResult.list()) {
                String relType = r.get("relType").asString();
                long count = r.get("count").asLong();
                totalRels += count;
                System.out.printf("  Type: %-20s | Count: %d%n", relType, count);
            }
            System.out.println("  TOTAL RELATIONSHIPS: " + totalRels);

            // 3. Every relationship for BATCH-1010 investigation graph
            System.out.println("\n=== 3. COMPLETE RELATIONSHIP LIST (SOURCE -> RELATIONSHIP -> TARGET) ===");
            Result allRelsResult = session.run("""
                MATCH (a {orgId: 1})-[r]->(b {orgId: 1})
                RETURN labels(a)[0] as fromLabel, a.stableId as fromId,
                       type(r) as relType, elementId(r) as relId,
                       labels(b)[0] as toLabel, b.stableId as toId
                ORDER BY type(r), fromLabel, fromId, toLabel, toId
            """);
            List<Record> allRels = allRelsResult.list();
            int i = 1;
            for (Record r : allRels) {
                System.out.printf("%3d. (%s: %s) -[:%s]-> (%s: %s)%n",
                        i++,
                        r.get("fromLabel").asString(), r.get("fromId").asString(),
                        r.get("relType").asString(),
                        r.get("toLabel").asString(), r.get("toId").asString());
            }

            // 4. Duplicate relationships check
            System.out.println("\n=== 4. DUPLICATE RELATIONSHIPS (Same Source + Type + Target) ===");
            Result dupResult = session.run("""
                MATCH (a {orgId: 1})-[r]->(b {orgId: 1})
                WITH labels(a)[0] as fromLabel, a.stableId as fromId, type(r) as relType, labels(b)[0] as toLabel, b.stableId as toId, count(r) as c, collect(elementId(r)) as ids
                WHERE c > 1
                RETURN fromLabel, fromId, relType, toLabel, toId, c, ids
            """);
            List<Record> dups = dupResult.list();
            if (dups.isEmpty()) {
                System.out.println("  NONE: Exactly 0 duplicate relationships found (no source+type+target appears more than once).");
            } else {
                for (Record d : dups) {
                    System.out.printf("  DUPLICATE (%s:%s)-[:%s]->(%s:%s) count=%d ids=%s%n",
                            d.get("fromLabel").asString(), d.get("fromId").asString(),
                            d.get("relType").asString(),
                            d.get("toLabel").asString(), d.get("toId").asString(),
                            d.get("c").asLong(), d.get("ids").asList());
                }
            }

            // 5. Why are there so many relationships? Deconstruct by incident and type
            System.out.println("\n=== 5. DECONSTRUCTION OF RELATIONSHIPS ===");
            // Incidents in Neo4j
            Result incList = session.run("MATCH (inc:Incident {orgId: 1}) RETURN inc.stableId as id, inc.investigationKey as key, inc.batchReference as batch");
            for (Record r : incList.list()) {
                System.out.printf("  Incident stableId: %s | key: %s | batchReference: %s%n",
                        r.get("id").asString(), r.get("key").asString(), r.get("batch").asString());
            }

            // Breakdown of HAS_EVIDENCE by incident
            System.out.println("\nHAS_EVIDENCE Breakdown by Incident:");
            Result hasEvidBreakdown = session.run("""
                MATCH (i:Incident {orgId: 1})-[r:HAS_EVIDENCE]->(e:Evidence)
                RETURN i.stableId as incidentId, count(r) as count
                ORDER BY i.stableId
            """);
            for (Record r : hasEvidBreakdown.list()) {
                System.out.printf("  Incident %s -> HAS_EVIDENCE -> Evidence: %d%n",
                        r.get("incidentId").asString(), r.get("count").asLong());
            }

            // Breakdown of TARGETS by incident
            System.out.println("\nTARGETS Breakdown by Incident:");
            Result targetsBreakdown = session.run("""
                MATCH (i:Incident {orgId: 1})-[r:TARGETS]->(b:Batch)
                RETURN i.stableId as incidentId, b.stableId as batchId, count(r) as count
            """);
            for (Record r : targetsBreakdown.list()) {
                System.out.printf("  Incident %s -> TARGETS -> Batch %s: %d%n",
                        r.get("incidentId").asString(), r.get("batchId").asString(), r.get("count").asLong());
            }

            // Breakdown of REFERENCES by Evidence and Entity
            System.out.println("\nREFERENCES Breakdown (Evidence -> Entity):");
            Result refBreakdown = session.run("""
                MATCH (e:Evidence {orgId: 1})-[r:REFERENCES]->(ent)
                RETURN labels(ent)[0] as entityType, count(r) as count
                ORDER BY entityType
            """);
            for (Record r : refBreakdown.list()) {
                System.out.printf("  Evidence -> REFERENCES -> %-10s: %d%n",
                        r.get("entityType").asString(), r.get("count").asLong());
            }

            // Breakdown of ASSOCIATED_WITH (Batch -> Entity)
            System.out.println("\nASSOCIATED_WITH Breakdown (Batch -> Entity):");
            Result assocBreakdown = session.run("""
                MATCH (b:Batch {orgId: 1})-[r:ASSOCIATED_WITH]->(ent)
                RETURN b.stableId as batchId, labels(ent)[0] as entityType, ent.stableId as entId
                ORDER BY entityType, entId
            """);
            for (Record r : assocBreakdown.list()) {
                System.out.printf("  Batch %s -> ASSOCIATED_WITH -> %s:%s%n",
                        r.get("batchId").asString(), r.get("entityType").asString(), r.get("entId").asString());
            }

            // Check for any cross-batch references
            System.out.println("\n=== 6. CHECK FOR CROSS-BATCH EVIDENCE ===");
            Result otherBatchesResult = session.run("""
                MATCH (n {orgId: 1})
                WHERE n.stableId CONTAINS '1011' OR n.stableId CONTAINS '1012' OR n.stableId CONTAINS '1015' OR n.stableId CONTAINS '1019'
                RETURN labels(n)[0] as label, n.stableId as sid
            """);
            List<Record> otherBatches = otherBatchesResult.list();
            if (otherBatches.isEmpty()) {
                System.out.println("  NONE: 0 nodes contain references to BATCH-1011, 1012, 1015, or 1019.");
            } else {
                for (Record r : otherBatches) {
                    System.out.printf("  FOUND: [%s] %s%n", r.get("label").asString(), r.get("sid").asString());
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    void verifyIncident1TraceabilityReturns49Relationships() {
        String uri = System.getenv().getOrDefault("NEO4J_URI", "neo4j+s://3b42b7a0.databases.neo4j.io");
        String user = System.getenv().getOrDefault("NEO4J_USERNAME", "3b42b7a0");
        String pass = System.getenv().getOrDefault("NEO4J_PASSWORD", "eq4woMbio2MGxmO80StDhF1tJjFnEQP46cWtk6XvzRQ");
        String database = System.getenv().getOrDefault("NEO4J_DATABASE", "3b42b7a0");

        com.taceiq.graph.config.Neo4jConfig cfg = new com.taceiq.graph.config.Neo4jConfig();
        org.springframework.test.util.ReflectionTestUtils.setField(cfg, "uri", uri);
        org.springframework.test.util.ReflectionTestUtils.setField(cfg, "username", user);
        org.springframework.test.util.ReflectionTestUtils.setField(cfg, "password", pass);
        org.springframework.test.util.ReflectionTestUtils.setField(cfg, "database", database);

        Driver driver = cfg.neo4jDriver();
        assertNotNull(driver);
        com.taceiq.graph.GraphQueryRepository queryRepo = new com.taceiq.graph.GraphQueryRepository(driver, cfg);

        com.taceiq.graph.dto.TraceabilityDtos.IncidentTraceabilityResponse trace;
        try {
            trace = queryRepo.traceIncident(1L, 1L, 3);
        } catch (org.neo4j.driver.exceptions.ServiceUnavailableException e) {
            driver.close();
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "Remote Neo4j instance is offline or unreachable: " + e.getMessage());
            return;
        }
        assertNotNull(trace);
        assertEquals("Incident", trace.getIncidentNode().getLabel());
        assertEquals("1", trace.getIncidentNode().getStableId());

        // 23 other connected nodes + 1 incident node = 24 nodes total
        assertEquals(23, trace.getNodes().size());

        // Exactly 49 directed relationships
        assertEquals(49, trace.getRelationships().size());

        Map<String, Long> countByType = new HashMap<>();
        for (var rel : trace.getRelationships()) {
            countByType.put(rel.getType(), countByType.getOrDefault(rel.getType(), 0L) + 1);
        }

        assertEquals(1L, countByType.get("TARGETS"));
        assertEquals(17L, countByType.get("HAS_EVIDENCE"));
        assertEquals(26L, countByType.get("REFERENCES"));
        assertEquals(5L, countByType.get("ASSOCIATED_WITH"));

        // Verify no reverse leakage
        for (var rel : trace.getRelationships()) {
            assertFalse("Batch".equals(rel.getFromLabel()) && "TARGETS".equals(rel.getType()),
                    "TARGETS must never point backwards from Batch to Incident");
            assertFalse("Supplier".equals(rel.getFromLabel()) && "REFERENCES".equals(rel.getType()),
                    "REFERENCES must never point backwards from Supplier to Evidence");
        }

        driver.close();
    }
}

package com.taceiq.graph;

import com.taceiq.graph.config.Neo4jConfig;
import com.taceiq.graph.dto.GraphEvidenceResponse;
import com.taceiq.graph.dto.TraceabilityDtos.CaseTraceabilityResponse;
import com.taceiq.graph.dto.TraceabilityDtos.TraceabilityNodeResponse;
import com.taceiq.graph.dto.TraceabilityDtos.TraceabilityRelationshipResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.TransactionConfig;
import org.neo4j.driver.Values;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
@Slf4j
public class GraphQueryRepository {

    private final Driver neo4jDriver;
    private final Neo4jConfig neo4jConfig;

    private Session session() {
        if (neo4jDriver == null || neo4jConfig == null || !neo4jConfig.isConfigured()) throw new IllegalStateException("Neo4j not configured");
        String db = neo4jConfig.getDatabase();
        return neo4jDriver.session(org.neo4j.driver.SessionConfig.forDatabase(db));
    }

    private TransactionConfig txConfig() {
        return TransactionConfig.builder().withTimeout(Duration.ofSeconds(5)).build();
    }

    public long countEvidence(Long orgId, String caseId, String actorId) {
        String cypher = """
                MATCH (e:Evidence {orgId: $orgId})
                WHERE ($caseId IS NULL OR EXISTS { MATCH (e)-[:BELONGS_TO]->(c:Case {orgId: $orgId, stableId: $caseId}) })
                  AND ($actorId IS NULL OR EXISTS { MATCH (e)-[:CREATED_BY]->(a:Actor {orgId: $orgId, stableId: $actorId}) })
                RETURN count(e) as total
                """;
        try (Session s = session()) {
            Map<String,Object> params = new HashMap<>();
            params.put("orgId", orgId);
            params.put("caseId", caseId);
            params.put("actorId", actorId);
            var result = s.run(cypher, params, txConfig());
            return result.single().get("total").asLong();
        }
    }

    public List<GraphEvidenceResponse> findEvidence(Long orgId, String caseId, String actorId, int skip, int limit) {
        String cypher = """
                MATCH (e:Evidence {orgId: $orgId})
                WHERE ($caseId IS NULL OR EXISTS { MATCH (e)-[:BELONGS_TO]->(c:Case {orgId: $orgId, stableId: $caseId}) })
                  AND ($actorId IS NULL OR EXISTS { MATCH (e)-[:CREATED_BY]->(a:Actor {orgId: $orgId, stableId: $actorId}) })
                RETURN e.stableId as stableId, e.title as title, e.sourceType as sourceType, e.status as status,
                       e.sourceCreatedAt as sourceCreatedAt, e.sourceUpdatedAt as sourceUpdatedAt
                ORDER BY e.stableId
                SKIP $skip LIMIT $limit
                """;
        try (Session s = session()) {
            Map<String,Object> params = new HashMap<>();
            params.put("orgId", orgId);
            params.put("caseId", caseId);
            params.put("actorId", actorId);
            params.put("skip", skip);
            params.put("limit", limit);
            var result = s.run(cypher, params, txConfig());
            List<GraphEvidenceResponse> list = new ArrayList<>();
            for (Record r : result.list()) {
                list.add(GraphEvidenceResponse.builder()
                        .stableId(r.get("stableId").asString(null))
                        .title(r.get("title").asString(null))
                        .sourceType(r.get("sourceType").asString(null))
                        .status(r.get("status").asString(null))
                        .sourceCreatedAt(r.get("sourceCreatedAt").asString(null))
                        .sourceUpdatedAt(r.get("sourceUpdatedAt").asString(null))
                        .build());
            }
            return list;
        }
    }

    public boolean caseExists(Long orgId, String caseId) {
        String cypher = "MATCH (c:Case {orgId: $orgId, stableId: $caseId}) RETURN count(c) as c";
        try (Session s = session()) {
            var result = s.run(cypher, Map.of("orgId", orgId, "caseId", caseId), txConfig());
            return result.single().get("c").asLong() > 0;
        }
    }

    public boolean incidentExists(Long orgId, Long incidentId) {
        String cypher = "MATCH (i:Incident {orgId: $orgId, stableId: $stableId}) RETURN count(i) as c";
        try (Session s = session()) {
            var result = s.run(cypher, Map.of("orgId", orgId, "stableId", String.valueOf(incidentId)), txConfig());
            return result.single().get("c").asLong() > 0;
        }
    }

    public com.taceiq.graph.dto.TraceabilityDtos.IncidentTraceabilityResponse traceIncident(Long orgId, Long incidentId, int depth) {
        String cypher = String.format("""
                MATCH (i:Incident {orgId: $orgId, stableId: $stableId})
                MATCH path = (i)-[:HAS_EVIDENCE|BELONGS_TO|CREATED_BY|DERIVED_FROM|TARGETS|REFERENCES|ASSOCIATED_WITH*0..%d]-(n)
                WHERE all(node IN nodes(path) WHERE node.orgId = $orgId AND (NOT node:Incident OR node = i))
                RETURN path
                """, depth);
        try (Session s = session()) {
            var result = s.run(cypher, Map.of("orgId", orgId, "stableId", String.valueOf(incidentId)), txConfig());
            Map<String, com.taceiq.graph.dto.TraceabilityDtos.TraceabilityNodeResponse> nodes = new LinkedHashMap<>();
            Map<String, com.taceiq.graph.dto.TraceabilityDtos.TraceabilityRelationshipResponse> rels = new LinkedHashMap<>();
            // Incident itself
            String incStable = String.valueOf(incidentId);
            nodes.put("Incident:" + incStable, com.taceiq.graph.dto.TraceabilityDtos.TraceabilityNodeResponse.builder().stableId(incStable).label("Incident").build());
            for (org.neo4j.driver.Record rec : result.list()) {
                var path = rec.get("path").asPath();
                for (var node : path.nodes()) {
                    String label = node.labels().iterator().next();
                    String sid = node.get("stableId").asString(null);
                    Object orgObj = node.get("orgId").asObject();
                    String orgStr = orgObj != null ? orgObj.toString() : null;
                    if (sid == null || orgStr == null || !orgStr.equals(String.valueOf(orgId))) continue;
                    String key = label + ":" + sid;
                    if (!nodes.containsKey(key)) {
                        String title = node.get("title").asString(null);
                        if (title == null || title.isBlank()) {
                            title = node.get("name").asString(sid);
                        }
                        nodes.put(key, com.taceiq.graph.dto.TraceabilityDtos.TraceabilityNodeResponse.builder()
                                .stableId(sid).label(label)
                                .title(title)
                                .sourceType(node.get("sourceType").asString(null))
                                .status(node.get("status").asString(null))
                                .build());
                    }
                }
                for (var segment : path) {
                    var rel = segment.relationship();
                    String type = rel.type();
                    if (!Set.of("HAS_EVIDENCE","BELONGS_TO","CREATED_BY","DERIVED_FROM","TARGETS","REFERENCES","ASSOCIATED_WITH").contains(type)) continue;
                    var start = segment.start();
                    var end = segment.end();

                    // Authoritative relationship direction from the stored relationship itself
                    boolean isForward;
                    try {
                        isForward = rel.startNodeElementId() != null && start.elementId() != null
                                ? rel.startNodeElementId().equals(start.elementId())
                                : rel.startNodeId() == start.id();
                    } catch (Exception e) {
                        isForward = rel.startNodeId() == start.id();
                    }
                    var sourceNode = isForward ? start : end;
                    var targetNode = isForward ? end : start;

                    String fromLabel = sourceNode.labels().iterator().next();
                    String toLabel = targetNode.labels().iterator().next();
                    String fromId = sourceNode.get("stableId").asString(null);
                    String toId = targetNode.get("stableId").asString(null);
                    if (fromId == null || toId == null) continue;
                    String relKey = fromLabel + ":" + fromId + "->" + type + "->" + toLabel + ":" + toId;
                    if (!rels.containsKey(relKey)) {
                        rels.put(relKey, com.taceiq.graph.dto.TraceabilityDtos.TraceabilityRelationshipResponse.builder()
                                .fromStableId(fromId).fromLabel(fromLabel)
                                .type(type)
                                .toStableId(toId).toLabel(toLabel)
                                .build());
                    }
                }
            }
            var incidentNode = nodes.remove("Incident:" + incStable);
            if (incidentNode == null) incidentNode = com.taceiq.graph.dto.TraceabilityDtos.TraceabilityNodeResponse.builder().stableId(incStable).label("Incident").build();
            return com.taceiq.graph.dto.TraceabilityDtos.IncidentTraceabilityResponse.builder()
                    .incidentNode(incidentNode)
                    .nodes(new ArrayList<>(nodes.values()))
                    .relationships(new ArrayList<>(rels.values()))
                    .depth(depth)
                    .build();
        }
    }

    public CaseTraceabilityResponse traceCase(Long orgId, String caseId, int depth) {
        // depth validated 1..10, safe to interpolate
        String cypher = String.format("""
                MATCH (c:Case {orgId: $orgId, stableId: $caseId})
                MATCH path = (c)-[:BELONGS_TO|CREATED_BY|DERIVED_FROM*0..%d]-(n)
                WHERE all(node IN nodes(path) WHERE node.orgId = $orgId AND (NOT node:Case OR node = c))
                RETURN path
                """, depth);
        try (Session s = session()) {
            var result = s.run(cypher, Map.of("orgId", orgId, "caseId", caseId), txConfig());
            Map<String, TraceabilityNodeResponse> nodes = new LinkedHashMap<>();
            Map<String, TraceabilityRelationshipResponse> rels = new LinkedHashMap<>();
            // Add case itself
            nodes.put("Case:" + caseId, TraceabilityNodeResponse.builder().stableId(caseId).label("Case").build());
            for (Record rec : result.list()) {
                var path = rec.get("path").asPath();
                for (var node : path.nodes()) {
                    String label = node.labels().iterator().next();
                    String sid = node.get("stableId").asString(null);
                    String org = node.get("orgId").asObject() != null ? node.get("orgId").toString() : null;
                    if (sid == null || org == null || !org.equals(String.valueOf(orgId))) continue;
                    String key = label + ":" + sid;
                    if (!nodes.containsKey(key)) {
                        nodes.put(key, TraceabilityNodeResponse.builder()
                                .stableId(sid).label(label)
                                .title(node.get("title").asString(null))
                                .sourceType(node.get("sourceType").asString(null))
                                .status(node.get("status").asString(null))
                                .build());
                    }
                }
                for (var segment : path) {
                    var rel = segment.relationship();
                    String type = rel.type();
                    if (!Set.of("BELONGS_TO","CREATED_BY","DERIVED_FROM").contains(type)) continue;
                    var start = segment.start();
                    var end = segment.end();
                    boolean isForward;
                    try {
                        isForward = rel.startNodeElementId() != null && start.elementId() != null
                                ? rel.startNodeElementId().equals(start.elementId())
                                : rel.startNodeId() == start.id();
                    } catch (Exception e) {
                        isForward = rel.startNodeId() == start.id();
                    }
                    var sourceNode = isForward ? start : end;
                    var targetNode = isForward ? end : start;

                    String fromLabel = sourceNode.labels().iterator().next();
                    String toLabel = targetNode.labels().iterator().next();
                    String fromId = sourceNode.get("stableId").asString(null);
                    String toId = targetNode.get("stableId").asString(null);
                    if (fromId == null || toId == null) continue;
                    String relKey = fromLabel + ":" + fromId + "->" + type + "->" + toLabel + ":" + toId;
                    if (!rels.containsKey(relKey)) {
                        rels.put(relKey, TraceabilityRelationshipResponse.builder()
                                .fromStableId(fromId).fromLabel(fromLabel)
                                .type(type)
                                .toStableId(toId).toLabel(toLabel)
                                .build());
                    }
                }
            }
            // Remove case from nodes list for separate field, keep only other nodes
            TraceabilityNodeResponse caseNode = nodes.remove("Case:" + caseId);
            if (caseNode == null) caseNode = TraceabilityNodeResponse.builder().stableId(caseId).label("Case").build();
            return CaseTraceabilityResponse.builder()
                    .caseNode(caseNode)
                    .nodes(new ArrayList<>(nodes.values()))
                    .relationships(new ArrayList<>(rels.values()))
                    .depth(depth)
                    .build();
        }
    }
}

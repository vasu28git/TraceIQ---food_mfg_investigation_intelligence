package com.taceiq;

import com.taceiq.graph.GraphQueryRepository;
import com.taceiq.graph.config.Neo4jConfig;
import com.taceiq.graph.dto.TraceabilityDtos.IncidentTraceabilityResponse;
import com.taceiq.graph.dto.TraceabilityDtos.TraceabilityRelationshipResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.*;
import org.neo4j.driver.Record;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Path;
import org.neo4j.driver.types.Relationship;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class TraceabilityRelationshipDirectionTest {

    private Driver driver;
    private Session session;
    private Neo4jConfig config;
    private GraphQueryRepository repository;

    @BeforeEach
    void setUp() {
        driver = mock(Driver.class);
        session = mock(Session.class);
        config = mock(Neo4jConfig.class);
        when(config.getDatabase()).thenReturn("neo4j");
        when(config.isConfigured()).thenReturn(true);
        when(driver.session(any(SessionConfig.class))).thenReturn(session);
        repository = new GraphQueryRepository(driver, config);
    }

    private Node createMockNode(String elementId, String label, String stableId, Long orgId) {
        Node node = mock(Node.class);
        when(node.elementId()).thenReturn(elementId);
        when(node.labels()).thenReturn(List.of(label));
        Value valSid = mock(Value.class);
        when(valSid.asString(any())).thenReturn(stableId);
        when(node.get("stableId")).thenReturn(valSid);
        Value valTitle = mock(Value.class);
        when(valTitle.asString(any())).thenReturn(stableId);
        when(node.get("title")).thenReturn(valTitle);
        Value valOrg = mock(Value.class);
        when(valOrg.asObject()).thenReturn(orgId);
        when(node.get("orgId")).thenReturn(valOrg);
        Value valSourceType = mock(Value.class);
        when(valSourceType.asString(any())).thenReturn("MES");
        when(node.get("sourceType")).thenReturn(valSourceType);
        Value valStatus = mock(Value.class);
        when(valStatus.asString(any())).thenReturn("ACTIVE");
        when(node.get("status")).thenReturn(valStatus);
        return node;
    }

    private Path.Segment createSegment(Node start, Node end, Relationship rel) {
        Path.Segment segment = mock(Path.Segment.class);
        when(segment.start()).thenReturn(start);
        when(segment.end()).thenReturn(end);
        when(segment.relationship()).thenReturn(rel);
        return segment;
    }

    private Relationship createMockRel(String startElementId, String endElementId, String type) {
        Relationship rel = mock(Relationship.class);
        when(rel.startNodeElementId()).thenReturn(startElementId);
        when(rel.endNodeElementId()).thenReturn(endElementId);
        when(rel.type()).thenReturn(type);
        return rel;
    }

    @Test
    void forwardTraversal_preservesStoredRelationshipDirection() {
        Node inc = createMockNode("elem_inc_1", "Incident", "1", 1L);
        Node batch = createMockNode("elem_batch_1010", "Batch", "BATCH-1010", 1L);
        Relationship targetsRel = createMockRel("elem_inc_1", "elem_batch_1010", "TARGETS");

        Path.Segment segment = createSegment(inc, batch, targetsRel);
        Path path = mock(Path.class);
        when(path.nodes()).thenReturn(List.of(inc, batch));
        when(path.iterator()).thenReturn(List.of(segment).iterator());

        Record record = mock(Record.class);
        Value pathValue = mock(Value.class);
        when(pathValue.asPath()).thenReturn(path);
        when(record.get("path")).thenReturn(pathValue);

        Result result = mock(Result.class);
        when(result.list()).thenReturn(List.of(record));
        when(session.run(anyString(), anyMap(), any())).thenReturn(result);

        IncidentTraceabilityResponse resp = repository.traceIncident(1L, 1L, 3);
        assertEquals(1, resp.getRelationships().size());
        TraceabilityRelationshipResponse r = resp.getRelationships().get(0);
        assertEquals("Incident", r.getFromLabel());
        assertEquals("1", r.getFromStableId());
        assertEquals("TARGETS", r.getType());
        assertEquals("Batch", r.getToLabel());
        assertEquals("BATCH-1010", r.getToStableId());
    }

    @Test
    void reverseTraversal_preservesStoredRelationshipDirection_neverInverts() {
        Node inc = createMockNode("elem_inc_1", "Incident", "1", 1L);
        Node batch = createMockNode("elem_batch_1010", "Batch", "BATCH-1010", 1L);
        Relationship targetsRel = createMockRel("elem_inc_1", "elem_batch_1010", "TARGETS");

        // Traversal reached batch first, then walked backward into incident
        Path.Segment reverseSegment = createSegment(batch, inc, targetsRel);
        Path path = mock(Path.class);
        when(path.nodes()).thenReturn(List.of(batch, inc));
        when(path.iterator()).thenReturn(List.of(reverseSegment).iterator());

        Record record = mock(Record.class);
        Value pathValue = mock(Value.class);
        when(pathValue.asPath()).thenReturn(path);
        when(record.get("path")).thenReturn(pathValue);

        Result result = mock(Result.class);
        when(result.list()).thenReturn(List.of(record));
        when(session.run(anyString(), anyMap(), any())).thenReturn(result);

        IncidentTraceabilityResponse resp = repository.traceIncident(1L, 1L, 3);
        assertEquals(1, resp.getRelationships().size());
        TraceabilityRelationshipResponse r = resp.getRelationships().get(0);
        // Even though path traversed batch -> inc, authoritative relationship is inc -> batch
        assertEquals("Incident", r.getFromLabel());
        assertEquals("1", r.getFromStableId());
        assertEquals("TARGETS", r.getType());
        assertEquals("Batch", r.getToLabel());
        assertEquals("BATCH-1010", r.getToStableId());
    }

    @Test
    void sameRelationshipReachedThroughMultiplePaths_deduplicatesToOne() {
        Node inc = createMockNode("elem_inc_1", "Incident", "1", 1L);
        Node batch = createMockNode("elem_batch_1010", "Batch", "BATCH-1010", 1L);
        Relationship targetsRel = createMockRel("elem_inc_1", "elem_batch_1010", "TARGETS");

        // Path 1 traverses forward: inc -> batch
        Path.Segment segmentForward = createSegment(inc, batch, targetsRel);
        Path path1 = mock(Path.class);
        when(path1.nodes()).thenReturn(List.of(inc, batch));
        when(path1.iterator()).thenReturn(List.of(segmentForward).iterator());

        // Path 2 traverses reverse: batch -> inc
        Path.Segment segmentReverse = createSegment(batch, inc, targetsRel);
        Path path2 = mock(Path.class);
        when(path2.nodes()).thenReturn(List.of(batch, inc));
        when(path2.iterator()).thenReturn(List.of(segmentReverse).iterator());

        Record record1 = mock(Record.class);
        Value pv1 = mock(Value.class);
        when(pv1.asPath()).thenReturn(path1);
        when(record1.get("path")).thenReturn(pv1);

        Record record2 = mock(Record.class);
        Value pv2 = mock(Value.class);
        when(pv2.asPath()).thenReturn(path2);
        when(record2.get("path")).thenReturn(pv2);

        Result result = mock(Result.class);
        when(result.list()).thenReturn(List.of(record1, record2));
        when(session.run(anyString(), anyMap(), any())).thenReturn(result);

        IncidentTraceabilityResponse resp = repository.traceIncident(1L, 1L, 3);
        assertEquals(1, resp.getRelationships().size(), "Must deduplicate multi-path traversal to exactly 1 relationship");
        assertEquals("Incident:1 -> TARGETS -> Batch:BATCH-1010",
                resp.getRelationships().get(0).getFromLabel() + ":" + resp.getRelationships().get(0).getFromStableId()
                + " -> " + resp.getRelationships().get(0).getType() + " -> "
                + resp.getRelationships().get(0).getToLabel() + ":" + resp.getRelationships().get(0).getToStableId());
    }

    @Test
    void distinctLegitimateRelationships_areAllPreserved() {
        Node inc = createMockNode("elem_inc_1", "Incident", "1", 1L);
        Node batch = createMockNode("elem_batch_1010", "Batch", "BATCH-1010", 1L);
        Node machine = createMockNode("elem_m05", "Machine", "M-05", 1L);

        Relationship r1 = createMockRel("elem_inc_1", "elem_batch_1010", "TARGETS");
        Relationship r2 = createMockRel("elem_batch_1010", "elem_m05", "ASSOCIATED_WITH");

        Path.Segment s1 = createSegment(inc, batch, r1);
        Path.Segment s2 = createSegment(batch, machine, r2);

        Path path = mock(Path.class);
        when(path.nodes()).thenReturn(List.of(inc, batch, machine));
        when(path.iterator()).thenReturn(List.of(s1, s2).iterator());

        Record record = mock(Record.class);
        Value pv = mock(Value.class);
        when(pv.asPath()).thenReturn(path);
        when(record.get("path")).thenReturn(pv);

        Result result = mock(Result.class);
        when(result.list()).thenReturn(List.of(record));
        when(session.run(anyString(), anyMap(), any())).thenReturn(result);

        IncidentTraceabilityResponse resp = repository.traceIncident(1L, 1L, 3);
        assertEquals(2, resp.getRelationships().size(), "Distinct relationships must both be retained");
        assertTrue(resp.getRelationships().stream().anyMatch(r -> r.getType().equals("TARGETS")));
        assertTrue(resp.getRelationships().stream().anyMatch(r -> r.getType().equals("ASSOCIATED_WITH")));
    }
}

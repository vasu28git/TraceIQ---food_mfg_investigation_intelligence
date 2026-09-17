package com.taceiq;

import com.taceiq.dto.EvidenceDiscoveryResult;
import com.taceiq.service.EvidenceRelevanceClassifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class EvidenceRelevanceClassifierTest {

    private EvidenceRelevanceClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new EvidenceRelevanceClassifier();
    }

    @Test
    void testDirectBatchRoute() {
        EvidenceDiscoveryResult discovery = EvidenceDiscoveryResult.builder()
                .evidenceStableId("EV-001")
                .distance(1)
                .semanticRoute("DIRECT_BATCH")
                .nodePath(List.of("EV-001", "BATCH-1010"))
                .build();

        var classification = classifier.classify(discovery, "BATCH-1010");
        assertEquals("DIRECT", classification.relevance());
        assertTrue(classification.discoveryReason().contains("batch"));
    }

    @Test
    void testMachineRoute() {
        EvidenceDiscoveryResult discovery = EvidenceDiscoveryResult.builder()
                .evidenceStableId("EV-002")
                .distance(2)
                .semanticRoute("MACHINE_ROUTE")
                .nodePath(List.of("BATCH-1010", "M-05", "EV-002"))
                .intermediateEntityId("M-05")
                .intermediateEntityType("Machine")
                .build();

        var classification = classifier.classify(discovery, "BATCH-1010");
        assertEquals("RELATED", classification.relevance());
        assertTrue(classification.discoveryReason().toLowerCase().contains("machine"));
    }

    @Test
    void testSupplierRoute() {
        EvidenceDiscoveryResult discovery = EvidenceDiscoveryResult.builder()
                .evidenceStableId("EV-003")
                .distance(2)
                .semanticRoute("SUPPLIER_ROUTE")
                .nodePath(List.of("BATCH-1010", "SUP-99", "EV-003"))
                .intermediateEntityId("SUP-99")
                .intermediateEntityType("Supplier")
                .build();

        var classification = classifier.classify(discovery, "BATCH-1010");
        assertEquals("RELATED", classification.relevance());
        assertTrue(classification.discoveryReason().contains("SUP-99"));
    }

    @Test
    void testWarehouseRoute() {
        EvidenceDiscoveryResult discovery = EvidenceDiscoveryResult.builder()
                .evidenceStableId("EV-004")
                .distance(2)
                .semanticRoute("WAREHOUSE_ROUTE")
                .nodePath(List.of("BATCH-1010", "WH-01", "EV-004"))
                .intermediateEntityId("WH-01")
                .intermediateEntityType("Warehouse")
                .build();

        var classification = classifier.classify(discovery, "BATCH-1010");
        assertEquals("RELATED", classification.relevance());
        assertTrue(classification.discoveryReason().contains("WH-01"));
    }

    @Test
    void testDerivedEvidenceRoute() {
        EvidenceDiscoveryResult discovery = EvidenceDiscoveryResult.builder()
                .evidenceStableId("EV-005")
                .distance(2)
                .semanticRoute("DERIVED_EVIDENCE")
                .nodePath(List.of("BATCH-1010", "EV-001", "EV-005"))
                .intermediateEntityId("EV-001")
                .intermediateEntityType("Evidence")
                .build();

        var classification = classifier.classify(discovery, "BATCH-1010");
        assertEquals("SUPPORTING", classification.relevance());
        assertTrue(classification.discoveryReason().toLowerCase().contains("derived"));
    }

    @Test
    void testFallbackRouteClassification() {
        EvidenceDiscoveryResult discovery = EvidenceDiscoveryResult.builder()
                .evidenceStableId("EV-006")
                .distance(3)
                .semanticRoute("UNKNOWN_ROUTE")
                .nodePath(List.of("BATCH-1010", "X", "Y", "EV-006"))
                .build();

        var classification = classifier.classify(discovery, "BATCH-1010");
        assertEquals("SUPPORTING", classification.relevance());
    }
}

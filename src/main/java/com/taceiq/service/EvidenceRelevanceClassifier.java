package com.taceiq.service;

import com.taceiq.dto.EvidenceDiscoveryResult;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;

@Service
public class EvidenceRelevanceClassifier {

    private static final Set<String> DIRECT_SOURCE_TYPES = Set.of(
            "LIMS", "MES", "QUALITY", "QC", "PRODUCTION", "SHIPMENT"
    );

    private static final Set<String> SUPPORTING_SOURCE_TYPES = Set.of(
            "SOP", "DOC", "DOCUMENT", "POLICY", "PROCEDURE", "AUDIT"
    );

    public record ClassificationResult(String relevance, String discoveryReason) {}

    public ClassificationResult classify(EvidenceDiscoveryResult discovery, String batchRef) {
        if (discovery == null) {
            return new ClassificationResult("RELATED", "Discovered evidence");
        }

        String route = discovery.semanticRoute() != null ? discovery.semanticRoute().toUpperCase(Locale.ROOT) : "";
        String sourceType = discovery.sourceType() != null ? discovery.sourceType().toUpperCase(Locale.ROOT) : "";
        String entityId = discovery.intermediateEntityId() != null ? discovery.intermediateEntityId() : "";
        String entityType = discovery.intermediateEntityType() != null ? discovery.intermediateEntityType() : "";
        int distance = discovery.distance();

        // Rule 1: SOP / Documentation / Policy is always SUPPORTING regardless of route
        if (SUPPORTING_SOURCE_TYPES.contains(sourceType)) {
            String reason = entityId.isBlank()
                    ? "Standard operating procedure or compliance documentation associated with investigation"
                    : "Standard operating procedure or compliance documentation for " + entityType + " " + entityId;
            return new ClassificationResult("SUPPORTING", reason);
        }

        // Rule 2: Multi-hop derived evidence (distance >= 3 or child records)
        if ("DERIVED_EVIDENCE".equals(route) || distance >= 3) {
            String reason = entityId.isBlank()
                    ? "Derived evidence record connected through multi-hop traceability"
                    : "Derived evidence record descending from parent " + entityId;
            return new ClassificationResult("SUPPORTING", reason);
        }

        // Rule 3: DIRECT_BATCH route with direct operational source types
        if ("DIRECT_BATCH".equals(route) || (distance == 1 && "Batch".equalsIgnoreCase(entityType))) {
            if ("SHIPMENT".equals(sourceType)) {
                return new ClassificationResult("DIRECT", "Direct shipment dispatch record for batch " + batchRef);
            }
            if (DIRECT_SOURCE_TYPES.contains(sourceType)) {
                return new ClassificationResult("DIRECT", "Direct " + sourceType + " record directly referencing batch " + batchRef);
            }
            return new ClassificationResult("DIRECT", "Direct operational record referencing batch " + batchRef);
        }

        // Rule 4: Intermediate operational entity routes
        if ("MACHINE_ROUTE".equals(route) || "Machine".equalsIgnoreCase(entityType)) {
            return new ClassificationResult("RELATED",
                    "Maintenance or telemetry record for machine " + entityId + " used to produce batch " + batchRef);
        }

        if ("SUPPLIER_ROUTE".equals(route) || "Supplier".equalsIgnoreCase(entityType)) {
            return new ClassificationResult("RELATED",
                    "Raw material or supplier record from " + entityId + " for batch components of " + batchRef);
        }

        if ("WAREHOUSE_ROUTE".equals(route) || "Warehouse".equalsIgnoreCase(entityType)) {
            return new ClassificationResult("RELATED",
                    "Storage environmental log for warehouse zone " + entityId + " staging batch " + batchRef);
        }

        if ("PRODUCT_ROUTE".equals(route) || "Product".equalsIgnoreCase(entityType)) {
            return new ClassificationResult("RELATED",
                    "Product master specification " + entityId + " for manufactured product");
        }

        if ("CUSTOMER_ROUTE".equals(route) || "Customer".equalsIgnoreCase(entityType)) {
            return new ClassificationResult("RELATED",
                    "Customer account record " + entityId + " associated with batch distribution");
        }

        // Default fallback
        if (distance == 1) {
            return new ClassificationResult("DIRECT", "Direct association with batch " + batchRef);
        } else if (distance == 2) {
            return new ClassificationResult("RELATED", "Operational entity record associated with batch " + batchRef);
        } else {
            return new ClassificationResult("SUPPORTING", "Contextual evidence discovered at graph distance " + distance);
        }
    }
}

package com.taceiq.ingestion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SourceFieldMapperTest {

    private SourceFieldMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new SourceFieldMapper();
    }

    @Test
    @DisplayName("ERP and Supplier files should infer ERP even when containing audit keywords")
    void testErpInference() {
        assertEquals("ERP", mapper.inferSourceType("ERP_Supplier_Audits.csv", null));
        assertEquals("ERP", mapper.inferSourceType("supplier_audits_2026.csv", null));
        assertEquals("ERP", mapper.inferSourceType("vendor_materials.csv", null));
        assertEquals("ERP", mapper.inferSourceType("erp_bom_export.csv", null));
    }

    @Test
    @DisplayName("Packaging files should infer PACKAGING even when containing inspection keywords")
    void testPackagingInference() {
        assertEquals("PACKAGING", mapper.inferSourceType("Packaging_Inspections.csv", null));
        assertEquals("PACKAGING", mapper.inferSourceType("pkg_inspections.csv", null));
        assertEquals("PACKAGING", mapper.inferSourceType("pack_integrity_check.csv", null));
    }

    @Test
    @DisplayName("Existing source types should remain preserved and correct")
    void testExistingSourceTypesPreserved() {
        assertEquals("MES", mapper.inferSourceType("MES_batches.csv", null));
        assertEquals("LIMS", mapper.inferSourceType("LIMS_QA.csv", null));
        assertEquals("LIMS", mapper.inferSourceType("incoming_inspections.csv", null));
        assertEquals("CMMS", mapper.inferSourceType("CMMS_maintenance.csv", null));
        assertEquals("SHIPMENT", mapper.inferSourceType("Shipments.csv", null));
        assertEquals("WAREHOUSE", mapper.inferSourceType("Warehouse.csv", null));
        assertEquals("SOP", mapper.inferSourceType("SOP_Audit.csv", null));
        assertEquals("CRM", mapper.inferSourceType("crm_complaints.csv", null));
        assertEquals("OTHER", mapper.inferSourceType("general_notes.csv", null));
    }

    @Test
    @DisplayName("Explicit type overrides inferred type")
    void testExplicitTypeOverride() {
        assertEquals("CUSTOM_TYPE", mapper.inferSourceType("MES_batches.csv", "CUSTOM_TYPE"));
    }
}

package com.taceiq.ingestion;

import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class SourceFieldMapper {

    // Normalized keys
    public static final String BATCH = "BATCH_REFERENCE";
    public static final String MACHINE = "MACHINE_REFERENCE";
    public static final String SUPPLIER = "SUPPLIER_REFERENCE";
    public static final String PRODUCT = "PRODUCT_REFERENCE";
    public static final String ORDER = "ORDER_REFERENCE";
    public static final String EXTERNAL = "EXTERNAL_REFERENCE";

    private final Map<String, String> aliasToNormalized = new HashMap<>();

    public SourceFieldMapper() {
        // BATCH aliases
        for (String a : List.of("batch_id","batchid","batch_number","batchnumber","batch_ref","batchref","batch_reference","batchreference","lot_id","lotid","lot_number","lotnumber","batch","lot")) {
            aliasToNormalized.put(a, BATCH);
        }
        // MACHINE aliases
        for (String a : List.of("machine_id","machineid","equipment_id","equipmentid","machine","equipment","machineid_ref","equipmentid_ref","related_entity","relatedentity","related_entity_id","relatedentityid")) {
            aliasToNormalized.put(a, MACHINE);
        }
        // SUPPLIER aliases
        for (String a : List.of("supplier_id","supplierid","vendor_id","vendorid","supplier","vendor","supplierid_ref")) {
            aliasToNormalized.put(a, SUPPLIER);
        }
        // PRODUCT aliases
        for (String a : List.of("product_id","productid","sku","product_code","productcode","product","productid_ref")) {
            aliasToNormalized.put(a, PRODUCT);
        }
        // ORDER aliases
        for (String a : List.of("order_id","orderid","order_number","ordernumber","sales_order","salesorder","order","orderid_ref")) {
            aliasToNormalized.put(a, ORDER);
        }
        // EXTERNAL aliases - generic id fields (do not overwrite specific mappings above)
        for (String a : List.of("complaint_id","complaintid","case_id","caseid","casenumber","ticket_id","ticketid","report_id","reportid","shipment_id","shipmentid","log_id","logid","doc_id","docid","record_id","recordid","external_id","externalid","id",
                "qa_id","qaid","inspection_id","inspectionid","test_id","testid","sample_id","sampleid","lims_id","limsid","pkg_id","pkgid","maintenance_id","maintenanceid","audit_id","auditid","document_id","documentid")) {
            aliasToNormalized.putIfAbsent(a, EXTERNAL);
        }
    }

    private String normalizeFieldName(String raw) {
        if (raw == null) return "";
        // lower case, trim, remove underscores and spaces
        return raw.trim().toLowerCase().replaceAll("[_\\s]+", "");
    }

    public String mapToNormalized(String fieldName) {
        String norm = normalizeFieldName(fieldName);
        return aliasToNormalized.get(norm);
    }

    public Map<String, String> extractNormalizedReferences(Map<String, String> row) {
        Map<String, String> out = new HashMap<>();
        for (Map.Entry<String, String> e : row.entrySet()) {
            String key = e.getKey();
            String val = e.getValue();
            if (val == null) continue;
            val = val.trim();
            if (val.isEmpty()) continue;
            String norm = mapToNormalized(key);
            if (norm != null) {
                // Prefer first non-empty for each normalized type, but allow batch/machine etc to be distinct
                // For EXTERNAL, we may have multiple; keep first but also keep specific?
                // For now, putIfAbsent to avoid overwriting batch with lot etc.
                out.putIfAbsent(norm, val);
                // Also handle case where both batch_id and lot_id present - keep batch_id, but lot_id also batch
                // So we keep first
            }
        }
        return out;
    }

    public String inferSourceType(String fileName, String explicitType) {
        if (explicitType != null && !explicitType.isBlank()) {
            return explicitType.trim().toUpperCase();
        }
        if (fileName == null) return "OTHER";
        String lower = fileName.toLowerCase();
        if (lower.contains("crm") || lower.contains("complaint")) return "CRM";
        if (lower.contains("packaging") || lower.contains("pkg") || lower.contains("pack")) return "PACKAGING";
        if (lower.contains("supplier") || lower.contains("erp") || lower.contains("vendor")) return "ERP";
        if (lower.contains("mes") || lower.contains("production") || lower.contains("batch")) return "MES";
        if (lower.contains("lims") || lower.contains("qa") || lower.contains("inspection") || lower.contains("quality")) return "LIMS";
        if (lower.contains("cmms") || lower.contains("maintenance") || lower.contains("machine") || lower.contains("equipment")) return "CMMS";
        if (lower.contains("shipment") || lower.contains("distribution")) return "SHIPMENT";
        if (lower.contains("warehouse") || lower.contains("temperature") || lower.contains("wms")) return "WAREHOUSE";
        if (lower.contains("sop") || lower.contains("audit") || lower.contains("investigation_doc")) return "SOP";
        return "OTHER";
    }

    public String deriveSourceRecordId(Map<String, String> row, Map<String, String> normalized, String sourceType) {
        // Try external reference first, then batch, then specific ids, then fallback
        if (normalized.containsKey(EXTERNAL)) return normalized.get(EXTERNAL);
        if (normalized.containsKey(BATCH)) return normalized.get(BATCH);
        if (normalized.containsKey(SUPPLIER)) return normalized.get(SUPPLIER);
        if (normalized.containsKey(PRODUCT)) return normalized.get(PRODUCT);
        if (normalized.containsKey(MACHINE)) return normalized.get(MACHINE);
        // Try common id fields from original row case-insensitive
        for (String k : List.of("report_id","shipment_id","log_id","doc_id","record_id","id")) {
            for (Map.Entry<String,String> e : row.entrySet()) {
                if (e.getKey().equalsIgnoreCase(k) && e.getValue()!=null && !e.getValue().trim().isEmpty()) {
                    return e.getValue().trim();
                }
            }
        }
        // Fallback: hash of row values
        String joined = String.join("|", row.values());
        return "ROW-" + Math.abs(joined.hashCode());
    }
}

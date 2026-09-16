package com.taceiq.ingestion;

import com.taceiq.entity.File;
import com.taceiq.entity.IngestedSourceRecord;
import com.taceiq.entity.Organisation;
import com.taceiq.repository.FileRepository;
import com.taceiq.repository.IngestedSourceRecordRepository;
import com.taceiq.repository.OrganisationRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import com.taceiq.entity.CanonicalEvidence;
import com.taceiq.repository.CanonicalEvidenceRepository;
import java.time.Instant;
import java.nio.file.Paths;
import java.util.*;

@Service
@Slf4j
public class SourceRecordIngestionService {

    private final FileRepository fileRepository;
    private final IngestedSourceRecordRepository sourceRecordRepository;
    private final OrganisationRepository organisationRepository;
    private final SourceFieldMapper fieldMapper;
    private final ObjectMapper objectMapper;
    private final CanonicalEvidenceRepository canonicalEvidenceRepository;

    @org.springframework.beans.factory.annotation.Autowired
    public SourceRecordIngestionService(FileRepository fileRepository,
                                        IngestedSourceRecordRepository sourceRecordRepository,
                                        OrganisationRepository organisationRepository,
                                        SourceFieldMapper fieldMapper,
                                        ObjectMapper objectMapper,
                                        CanonicalEvidenceRepository canonicalEvidenceRepository) {
        this.fileRepository = fileRepository;
        this.sourceRecordRepository = sourceRecordRepository;
        this.organisationRepository = organisationRepository;
        this.fieldMapper = fieldMapper;
        this.objectMapper = objectMapper;
        this.canonicalEvidenceRepository = canonicalEvidenceRepository;
    }

    // Backward compat for tests
    public SourceRecordIngestionService(FileRepository fileRepository,
                                        IngestedSourceRecordRepository sourceRecordRepository,
                                        OrganisationRepository organisationRepository,
                                        SourceFieldMapper fieldMapper,
                                        ObjectMapper objectMapper) {
        this(fileRepository, sourceRecordRepository, organisationRepository, fieldMapper, objectMapper, null);
    }

    public static class IngestionResult {
        public int totalRows;
        public int created;
        public int reused;
        public int failed;
        public List<String> errors = new ArrayList<>();
        public String sourceType;
    }

    @Transactional
    public IngestionResult ingestFile(Long orgId, Long fileId, String explicitSourceType) {
        File file = fileRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("File not found: " + fileId));
        if (!file.getOrganisation().getOrgId().equals(orgId)) {
            throw new SecurityException("File not in organisation");
        }
        return ingestFileInternal(orgId, file, explicitSourceType);
    }

    @Transactional
    public IngestionResult ingestMultipartForIncident(Long orgId, File savedFile, String explicitSourceType) {
        // savedFile already persisted via FileService; just ingest its content
        return ingestFileInternal(orgId, savedFile, explicitSourceType);
    }

    private IngestionResult ingestFileInternal(Long orgId, File file, String explicitSourceType) {
        IngestionResult result = new IngestionResult();
        String fileName = file.getOriginalName();
        String sourceType = fieldMapper.inferSourceType(fileName, explicitSourceType);
        result.sourceType = sourceType;
        Path path = Paths.get(file.getStorageKey());
        if (!Files.exists(path)) {
            // Try relative to uploads
            path = Paths.get(file.getStorageKey());
            if (!Files.exists(path)) {
                result.failed = 1;
                result.errors.add("File not found on disk: " + file.getStorageKey());
                return result;
            }
        }
        String contentType = file.getContentType() != null ? file.getContentType().toLowerCase() : "";
        String lowerName = fileName != null ? fileName.toLowerCase() : "";
        try {
            List<Map<String, String>> rows;
            if (lowerName.endsWith(".csv") || contentType.contains("csv") || contentType.contains("text/plain")) {
                rows = parseCsv(path);
            } else if (lowerName.endsWith(".json") || contentType.contains("json")) {
                rows = parseJson(path);
            } else if (lowerName.endsWith(".xlsx") || lowerName.endsWith(".xls")) {
                rows = parseXlsx(path);
            } else {
                // Try CSV fallback
                try {
                    rows = parseCsv(path);
                    if (rows.isEmpty()) {
                        result.errors.add("Unsupported file type for structured ingestion: " + fileName);
                        return result;
                    }
                } catch (Exception e) {
                    result.errors.add("Unsupported file type: " + fileName);
                    return result;
                }
            }
            result.totalRows = rows.size();
            Organisation org = organisationRepository.getReferenceById(orgId);
            for (Map<String, String> row : rows) {
                try {
                    Map<String, String> normalized = fieldMapper.extractNormalizedReferences(row);
                    String batch = normalized.get(SourceFieldMapper.BATCH);
                    String machine = normalized.get(SourceFieldMapper.MACHINE);
                    String supplier = normalized.get(SourceFieldMapper.SUPPLIER);
                    String product = normalized.get(SourceFieldMapper.PRODUCT);
                    String order = normalized.get(SourceFieldMapper.ORDER);
                    String external = normalized.get(SourceFieldMapper.EXTERNAL);
                    // Fallback external from row id fields
                    String sourceRecordId = fieldMapper.deriveSourceRecordId(row, normalized, sourceType);
                    if (sourceRecordId == null || sourceRecordId.isBlank()) {
                        result.failed++;
                        result.errors.add("Skipped row with no record id: " + row);
                        continue;
                    }
                    // Idempotency check
                    IngestedSourceRecord rec = null;
                    Optional<IngestedSourceRecord> existingRec = sourceRecordRepository
                            .findByOrganisationOrgIdAndSourceTypeAndSourceRecordId(orgId, sourceType, sourceRecordId.trim());
                    if (existingRec.isPresent()) {
                        rec = existingRec.get();
                        result.reused++;
                    } else {
                        // Build payload json
                        String payload = objectMapper.writeValueAsString(row);
                        rec = IngestedSourceRecord.builder()
                                .organisation(org)
                                .sourceFile(file)
                                .sourceType(sourceType)
                                .sourceRecordId(sourceRecordId.trim())
                                .batchReference(batch != null ? batch.trim() : null)
                                .machineReference(machine != null ? machine.trim() : null)
                                .supplierReference(supplier != null ? supplier.trim() : null)
                                .productReference(product != null ? product.trim() : null)
                                .orderReference(order != null ? order.trim() : null)
                                .externalReference(external != null ? external.trim() : sourceRecordId.trim())
                                .payload(payload)
                                .build();
                        rec = sourceRecordRepository.save(rec);
                        result.created++;
                    }

                    // Ensure CanonicalEvidence exists and is updated for this source record in this organisation
                    if (canonicalEvidenceRepository != null) {
                        ensureCanonicalEvidence(org, rec, row);
                    }
                } catch (Exception e) {
                    result.failed++;
                    String msg = e.getMessage() != null ? e.getMessage().substring(0, Math.min(200, e.getMessage().length())) : e.getClass().getSimpleName();
                    result.errors.add(msg);
                }
            }
        } catch (Exception e) {
            result.failed++;
            result.errors.add("Ingestion failed: " + e.getMessage());
            log.warn("Ingestion failed for file {} org {}: {}", file.getId(), orgId, e.getMessage());
        }
        return result;
    }

    private List<Map<String, String>> parseCsv(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        if (lines.isEmpty()) return List.of();
        // Find header (first non-empty)
        String headerLine = null;
        int headerIdx = 0;
        for (int i = 0; i < lines.size(); i++) {
            if (!lines.get(i).trim().isEmpty()) { headerLine = lines.get(i); headerIdx = i; break; }
        }
        if (headerLine == null) return List.of();
        List<String> headers = splitCsvLine(headerLine);
        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = headerIdx + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.trim().isEmpty()) continue;
            List<String> vals = splitCsvLine(line);
            Map<String, String> row = new LinkedHashMap<>();
            for (int c = 0; c < headers.size(); c++) {
                String h = headers.get(c).trim();
                String v = c < vals.size() ? vals.get(c) : "";
                row.put(h, v != null ? v.trim() : "");
            }
            // Skip empty rows
            boolean allEmpty = row.values().stream().allMatch(v -> v == null || v.trim().isEmpty());
            if (!allEmpty) rows.add(row);
        }
        return rows;
    }

    private List<String> splitCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }

    private List<Map<String, String>> parseJson(Path path) throws IOException {
        String content = Files.readString(path, StandardCharsets.UTF_8);
        content = content.trim();
        if (content.isEmpty()) return List.of();
        try {
            // Try array
            List<Map<String, Object>> list = objectMapper.readValue(content, new TypeReference<List<Map<String, Object>>>() {});
            List<Map<String, String>> rows = new ArrayList<>();
            for (Map<String, Object> m : list) {
                Map<String, String> row = new LinkedHashMap<>();
                for (Map.Entry<String, Object> e : m.entrySet()) {
                    row.put(e.getKey(), e.getValue() != null ? e.getValue().toString() : "");
                }
                rows.add(row);
            }
            return rows;
        } catch (Exception e) {
            // Try single object
            try {
                Map<String, Object> obj = objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {});
                Map<String, String> row = new LinkedHashMap<>();
                for (Map.Entry<String, Object> entry : obj.entrySet()) {
                    row.put(entry.getKey(), entry.getValue() != null ? entry.getValue().toString() : "");
                }
                return List.of(row);
            } catch (Exception e2) {
                throw new IOException("JSON parse failed: " + e.getMessage());
            }
        }
    }

    private List<Map<String, String>> parseXlsx(Path path) throws IOException {
        try (java.io.InputStream is = Files.newInputStream(path);
             org.apache.poi.ss.usermodel.Workbook wb = org.apache.poi.ss.usermodel.WorkbookFactory.create(is)) {
            org.apache.poi.ss.usermodel.Sheet sheet = wb.getSheetAt(0);
            if (sheet == null) return List.of();
            List<Map<String, String>> rows = new ArrayList<>();
            List<String> headers = null;
            for (org.apache.poi.ss.usermodel.Row row : sheet) {
                if (row == null) continue;
                List<String> vals = new ArrayList<>();
                int last = row.getLastCellNum();
                if (last < 0) continue;
                for (int c = 0; c < last; c++) {
                    org.apache.poi.ss.usermodel.Cell cell = row.getCell(c, org.apache.poi.ss.usermodel.Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    String v = "";
                    if (cell != null) {
                        switch (cell.getCellType()) {
                            case STRING: v = cell.getStringCellValue(); break;
                            case NUMERIC:
                                if (org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) v = cell.getLocalDateTimeCellValue().toString();
                                else {
                                    double d = cell.getNumericCellValue();
                                    if (d == Math.floor(d)) v = String.valueOf((long) d);
                                    else v = String.valueOf(d);
                                }
                                break;
                            case BOOLEAN: v = String.valueOf(cell.getBooleanCellValue()); break;
                            case FORMULA:
                                try { v = cell.getStringCellValue(); } catch (Exception e) { try { v = String.valueOf(cell.getNumericCellValue()); } catch (Exception ex) { v = cell.getCellFormula(); } }
                                break;
                            default: v = "";
                        }
                    }
                    vals.add(v != null ? v.trim() : "");
                }
                boolean allEmpty = vals.stream().allMatch(s -> s == null || s.trim().isEmpty());
                if (allEmpty) continue;
                if (headers == null) {
                    headers = new ArrayList<>(vals);
                    // trim header strings
                    for (int i = 0; i < headers.size(); i++) headers.set(i, headers.get(i).trim());
                    continue;
                }
                Map<String, String> map = new LinkedHashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    String h = headers.get(i);
                    if (h == null || h.isBlank()) continue;
                    String v = i < vals.size() ? vals.get(i) : "";
                    map.put(h, v != null ? v.trim() : "");
                }
                boolean rowEmpty = map.values().stream().allMatch(s -> s == null || s.trim().isEmpty());
                if (!rowEmpty) rows.add(map);
            }
            return rows;
        } catch (Exception e) {
            throw new IOException("XLSX parse failed: " + e.getMessage(), e);
        }
    }

    public IngestionResult ingestExistingFilesForOrg(Long orgId) {
        List<File> files = fileRepository.findByOrganisationOrgId(orgId);
        log.info("Ingesting {} existing files for org {}", files.size(), orgId);
        IngestionResult total = new IngestionResult();
        total.sourceType = "MULTI";
        for (File f : files) {
            try {
                log.info("Ingesting file {} {} type {}", f.getId(), f.getOriginalName(), fieldMapper.inferSourceType(f.getOriginalName(), null));
                IngestionResult r = ingestFile(orgId, f.getId(), null);
                log.info("File {} result created {} reused {} failed {}", f.getId(), r.created, r.reused, r.failed);
                total.totalRows += r.totalRows;
                total.created += r.created;
                total.reused += r.reused;
                total.failed += r.failed;
                total.errors.addAll(r.errors);
            } catch (Exception e) {
                log.warn("Failed to ingest file {}: {}", f.getId(), e.getMessage());
                total.failed++;
                total.errors.add(e.getMessage());
            }
        }
        log.info("Total ingestion for org {}: created {} reused {} failed {}", orgId, total.created, total.reused, total.failed);
        return total;
    }

    private void ensureCanonicalEvidence(Organisation org, IngestedSourceRecord src, Map<String, String> row) {
        Long orgId = org.getOrgId();
        String externalId = "SRC_" + src.getSourceType() + "_" + src.getSourceRecordId();
        externalId = externalId.trim().replaceAll("\\s+", "_");
        if (externalId.length() > 200) externalId = externalId.substring(0, 200);

        Optional<CanonicalEvidence> existingOpt = canonicalEvidenceRepository
                .findByExternalIdAndOrganisationOrgId(externalId, orgId);

        if (existingOpt.isPresent()) {
            CanonicalEvidence existing = existingOpt.get();
            existing.setLastSeenAt(Instant.now());
            canonicalEvidenceRepository.save(existing);
        } else {
            String title = src.getSourceRecordId();
            if (row != null) {
                for (String k : List.of("title", "description", "product_name", "test_type", "status", "record_id", "name")) {
                    if (row.containsKey(k) && row.get(k) != null && !row.get(k).isBlank()) {
                        title = row.get(k).trim();
                        break;
                    }
                }
            }
            Map<String, Object> norm = new LinkedHashMap<>();
            norm.put("sourceType", src.getSourceType());
            norm.put("sourceRecordId", src.getSourceRecordId());
            norm.put("batchReference", src.getBatchReference());
            norm.put("machineReference", src.getMachineReference());
            norm.put("supplierReference", src.getSupplierReference());
            norm.put("productReference", src.getProductReference());
            norm.put("orderReference", src.getOrderReference());
            norm.put("originalPayload", src.getPayload());
            String normalizedPayload;
            try {
                normalizedPayload = objectMapper.writeValueAsString(norm);
            } catch (Exception e) {
                normalizedPayload = "{}";
            }
            String contentHash = sha256Hex(normalizedPayload);

            CanonicalEvidence evidence = CanonicalEvidence.builder()
                    .organisation(org)
                    .integration(null)
                    .incident(null)
                    .externalId(externalId)
                    .title(title)
                    .sourceType(src.getSourceType())
                    .status("READY")
                    .contentHash(contentHash)
                    .normalizedPayload(normalizedPayload)
                    .firstSeenAt(Instant.now())
                    .lastSeenAt(Instant.now())
                    .isDeleted(false)
                    .build();
            canonicalEvidenceRepository.save(evidence);
        }
    }

    private String sha256Hex(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

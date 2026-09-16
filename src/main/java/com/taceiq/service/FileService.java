package com.taceiq.service;

import com.taceiq.entity.CanonicalEvidence;
import com.taceiq.entity.File;
import com.taceiq.entity.Integration;
import com.taceiq.entity.Investigation;
import com.taceiq.event.ManualEvidenceCommittedEvent;
import com.taceiq.repository.CanonicalEvidenceRepository;
import com.taceiq.repository.FileRepository;
import com.taceiq.repository.IntegrationRepository;
import com.taceiq.repository.InvestigationRepository;
import com.taceiq.repository.OrganisationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

@Service
@Slf4j
public class FileService {

    private final FileRepository fileRepository;
    private final OrganisationRepository organisationRepository;
    private final IntegrationRepository integrationRepository;
    private final CanonicalEvidenceRepository canonicalEvidenceRepository;
    private final InvestigationRepository investigationRepository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired(required = false)
    private com.taceiq.ingestion.SourceRecordIngestionService ingestionService;

    @Autowired(required = false)
    private com.taceiq.ingestion.EvidenceDiscoveryService discoveryService;

    // Primary constructor (used by Spring)
    @org.springframework.beans.factory.annotation.Autowired
    public FileService(FileRepository fileRepository,
                       OrganisationRepository organisationRepository,
                       IntegrationRepository integrationRepository,
                       CanonicalEvidenceRepository canonicalEvidenceRepository,
                       InvestigationRepository investigationRepository,
                       ObjectMapper objectMapper,
                       ApplicationEventPublisher eventPublisher) {
        this.fileRepository = fileRepository;
        this.organisationRepository = organisationRepository;
        this.integrationRepository = integrationRepository;
        this.canonicalEvidenceRepository = canonicalEvidenceRepository;
        this.investigationRepository = investigationRepository;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

    // Backward compat for tests that construct with 6 args (without InvestigationRepository)
    public FileService(FileRepository fileRepository,
                       OrganisationRepository organisationRepository,
                       IntegrationRepository integrationRepository,
                       CanonicalEvidenceRepository canonicalEvidenceRepository,
                       ObjectMapper objectMapper,
                       ApplicationEventPublisher eventPublisher) {
        this(fileRepository, organisationRepository, integrationRepository, canonicalEvidenceRepository, null, objectMapper, eventPublisher);
    }

    @Value("${app.files.storage-path:./uploads}")
    private String storagePath;

    @Value("${app.files.max-size-mb:10}")
    private int maxSizeMb;

    private static final Set<String> VALID_STATUSES = Set.of("UPLOADED", "PROCESSING", "READY", "FAILED", "ARCHIVED", "PENDING");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf", "text/plain", "text/csv", "application/csv",
            "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "image/png", "image/jpeg", "image/jpg", "application/json", "text/html"
    );

    // --- Validation helpers ---

    private void validateSourceType(String sourceType) {
        if (sourceType == null || sourceType.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source type is required");
        }
    }

    private void validateOriginalName(String originalName) {
        if (originalName == null || originalName.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Original name is required");
        }
    }

    private void validateStatus(String status) {
        if (status == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status is required");
        }
        String norm = status.trim().toUpperCase();
        if (!VALID_STATUSES.contains(norm)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status '" + status + "'. Allowed: " + VALID_STATUSES);
        }
    }

    private void validateNotBlank(String field, String fieldName) {
        if (field == null || field.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is required");
        }
    }

    // --- Create ---

    public File createFile(File file) {
        // Default org for backward compat when called without orgId; tests always provide orgId
        return createFile(file, 1L);
    }

    public File createFile(File file, Long currentOrgId) {
        validateSourceType(file.getSourceType());
        validateOriginalName(file.getOriginalName());

        // Force organisation to current user's org, ignore client value
        file.setOrganisation(organisationRepository.getReferenceById(currentOrgId));

        // If integration provided, verify it belongs to same org
        if (file.getIntegration() != null && file.getIntegration().getId() != null) {
            Long integrationId = file.getIntegration().getId();
            var integration = integrationRepository.findById(integrationId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Integration not found: " + integrationId));
            if (integration.getOrganisation() == null || !integration.getOrganisation().getOrgId().equals(currentOrgId)) {
                throw new AccessDeniedException("Integration does not belong to your organisation");
            }
            // Ensure file's integration is managed entity
            file.setIntegration(integration);
        }
        return fileRepository.save(file);
    }

    @org.springframework.transaction.annotation.Transactional
    public File uploadFile(MultipartFile multipartFile, Long currentOrgId) {
        return uploadFile(multipartFile, currentOrgId, null);
    }

    @org.springframework.transaction.annotation.Transactional
    public File uploadFile(MultipartFile multipartFile, Long currentOrgId, Long incidentId) {
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is required and must not be empty");
        }
        String originalName = multipartFile.getOriginalFilename();
        validateOriginalName(originalName);
        String sanitizedName = originalName.trim();
        // Basic filename validation - no path traversal
        if (sanitizedName.contains("..") || sanitizedName.contains("/") || sanitizedName.contains("\\")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid filename");
        }
        String contentType = multipartFile.getContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = "application/octet-stream";
        }
        // Validate content type if needed - allow all but block executables
        if (contentType.toLowerCase().contains("executable") || contentType.toLowerCase().contains("x-msdownload")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported content type: " + contentType);
        }
        long size = multipartFile.getSize();
        long maxBytes = (long) maxSizeMb * 1024 * 1024;
        if (size > maxBytes) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File size exceeds maximum allowed " + maxSizeMb + "MB");
        }
        if (size == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File must not be empty");
        }

        // Validate incident (Incident is Investigation) if provided — tenant-scoped
        Investigation incident = null;
        if (incidentId != null) {
            if (investigationRepository == null) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Incident validation not configured");
            }
            incident = investigationRepository.findByIdAndOrganisationOrgId(incidentId, currentOrgId)
                    .orElseGet(() -> {
                        if (investigationRepository.findById(incidentId).isPresent()) {
                            throw new AccessDeniedException("Incident does not belong to your organisation");
                        }
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found with id: " + incidentId);
                    });
        }

        // Read bytes for hash and storage
        byte[] bytes;
        try {
            bytes = multipartFile.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read file");
        }

        // Compute SHA-256 of file content
        String contentHash = sha256Hex(bytes);

        // Check for duplicate content hash within org (idempotency) - if same file already exists, reuse existing File but still create new File record per requirement (different files with same name must not overwrite)
        // We allow duplicate content but externalId will be unique per File id, so no overwrite

        // Store file to filesystem
        String storageKey = storeFileBytes(sanitizedName, bytes, currentOrgId);

        // Create File entity
        File file = File.builder()
                .organisation(organisationRepository.getReferenceById(currentOrgId))
                .sourceType("MANUAL_UPLOAD")
                .originalName(sanitizedName)
                .storageKey(storageKey)
                .contentType(contentType)
                .size(size)
                .status("UPLOADED")
                .receivedAt(Instant.now())
                .build();
        File savedFile = fileRepository.save(file);

        // Skip creating MANUAL_FILE_* CanonicalEvidence wrapper for CSV uploads (structured data is ingested via SourceRecordIngestionService)
        boolean isCsv = (sanitizedName != null && sanitizedName.toLowerCase().endsWith(".csv"))
                || (contentType != null && contentType.toLowerCase().contains("csv"));

        if (!isCsv) {
            // Create CanonicalEvidence for non-CSV manual uploads (e.g. PDF/documentation)
            String externalId = "MANUAL_FILE_" + savedFile.getId();
            String title = sanitizedName;
            // Build normalized payload with safe metadata only
            Map<String, Object> payloadMap = new TreeMap<>();
            payloadMap.put("originalName", sanitizedName);
            payloadMap.put("contentType", contentType);
            payloadMap.put("size", size);
            payloadMap.put("storageKey", storageKey);
            payloadMap.put("sourceType", "MANUAL_UPLOAD");
            payloadMap.put("fileId", savedFile.getId());
            payloadMap.put("contentHash", contentHash);
            String normalizedPayload;
            try {
                normalizedPayload = objectMapper.writeValueAsString(payloadMap);
            } catch (Exception e) {
                normalizedPayload = "{}";
            }
            // Content hash for canonical_evidence is hash of normalized payload (consistent with EvidenceNormalizer) OR file content hash
            // Use file content hash for manual uploads to ensure idempotency based on actual file bytes
            String canonicalHash = sha256Hex(normalizedPayload.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            CanonicalEvidence evidence = CanonicalEvidence.builder()
                    .organisation(organisationRepository.getReferenceById(currentOrgId))
                    .integration(null) // manual upload has no integration
                    .incident(incident) // nullable for backward compat; set when incidentId provided
                    .externalId(externalId)
                    .title(title)
                    .sourceType("MANUAL_UPLOAD")
                    .status("READY")
                    .contentHash(canonicalHash)
                    .normalizedPayload(normalizedPayload)
                    .firstSeenAt(Instant.now())
                    .lastSeenAt(Instant.now())
                    .isDeleted(false)
                    .build();
            canonicalEvidenceRepository.save(evidence);
        }

        // If upload is incident-scoped and incident has batch reference, attempt automatic discovery best-effort
        if (discoveryService != null && incident != null && incident.getBatchReference() != null && !incident.getBatchReference().isBlank()) {
            try {
                discoveryService.discoverForIncident(incident.getId(), currentOrgId);
                log.info("Auto discovery after file upload triggered for incident {} org {}", incident.getId(), currentOrgId);
            } catch (Exception e) {
                log.warn("Auto discovery after file upload failed for incident {} org {}: {}", incident.getId(), currentOrgId, e.getMessage());
            }
        }

        // Publish event for post-commit graph projection (PostgreSQL commit → Neo4j projection)
        eventPublisher.publishEvent(new ManualEvidenceCommittedEvent(this, currentOrgId, savedFile.getId()));

        return savedFile;
    }

    private String storeFileBytes(String originalName, byte[] bytes, Long orgId) {
        try {
            Path base = Paths.get(storagePath, String.valueOf(orgId));
            Files.createDirectories(base);
            String uniqueName = UUID.randomUUID().toString() + "_" + originalName;
            Path target = base.resolve(uniqueName);
            Files.write(target, bytes, java.nio.file.StandardOpenOption.CREATE_NEW);
            return target.toString();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store file");
        }
    }

    private String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    // --- Get by ID ---

    public File getFileById(Long id) {
        return fileRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found with id: " + id));
    }

    public File getFileById(Long id, Long currentOrgId) {
        File file = fileRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found with id: " + id));
        if (!file.getOrganisation().getOrgId().equals(currentOrgId)) {
            throw new AccessDeniedException("File not found or not in your organisation: " + id);
        }
        return file;
    }

    // --- Get by Integration ---

    public List<File> getFilesByIntegration(Long integrationId) {
        return fileRepository.findByIntegrationId(integrationId);
    }

    public List<File> getFilesByIntegration(Long integrationId, Long currentOrgId) {
        // Verify integration belongs to current org first
        var integration = integrationRepository.findById(integrationId)
                .orElseThrow(() -> new RuntimeException("Integration not found: " + integrationId));
        if (integration.getOrganisation() == null || !integration.getOrganisation().getOrgId().equals(currentOrgId)) {
            throw new AccessDeniedException("Integration does not belong to your organisation");
        }
        return fileRepository.findByIntegrationIdAndOrganisationOrgId(integrationId, currentOrgId);
    }

    // --- Get by Organisation ---

    public List<File> getFilesByOrganisation(Long orgId) {
        return fileRepository.findByOrganisationOrgId(orgId);
    }

    public List<File> getFilesByOrganisation(Long requestedOrgId, Long currentOrgId) {
        if (!requestedOrgId.equals(currentOrgId)) {
            throw new AccessDeniedException("Cannot access files of another organisation");
        }
        return fileRepository.findByOrganisationOrgId(currentOrgId);
    }

    // --- Update ---

    public File updateFile(Long id, File updated) {
        File existing = getFileById(id);
        // Never allow organisation change - preserve existing org
        // existing.setOrganisation() is intentionally not called
        existing.setOriginalName(updated.getOriginalName());
        existing.setStorageKey(updated.getStorageKey());
        existing.setContentType(updated.getContentType());
        existing.setSize(updated.getSize());
        existing.setStatus(updated.getStatus());
        existing.setReceivedAt(updated.getReceivedAt());
        return fileRepository.save(existing);
    }

    @org.springframework.transaction.annotation.Transactional
    public File updateFile(Long id, File updated, Long currentOrgId) {
        File existing = getFileById(id, currentOrgId);
        // Never allow organisation/integration cross-org change
        // Preserve existing organisation - do not apply updated.getOrganisation()

        if (updated.getOriginalName() != null) {
            existing.setOriginalName(updated.getOriginalName().trim());
        }
        if (updated.getStorageKey() != null) {
            existing.setStorageKey(updated.getStorageKey());
        }
        if (updated.getContentType() != null) {
            existing.setContentType(updated.getContentType());
        }
        if (updated.getSize() != null) {
            existing.setSize(updated.getSize());
        }
        if (updated.getStatus() != null) {
            validateStatus(updated.getStatus());
            existing.setStatus(updated.getStatus().trim().toUpperCase());
        }
        if (updated.getReceivedAt() != null) {
            existing.setReceivedAt(updated.getReceivedAt());
        }

        // Never allow integration cross-org change
        if (updated.getIntegration() != null && updated.getIntegration().getId() != null) {
            Long newIntegrationId = updated.getIntegration().getId();
            var integration = integrationRepository.findById(newIntegrationId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Integration not found: " + newIntegrationId));
            if (!integration.getOrganisation().getOrgId().equals(currentOrgId)) {
                throw new AccessDeniedException("Integration does not belong to your organisation");
            }
            existing.setIntegration(integration);
        }

        return fileRepository.save(existing);
    }

    public File updateFileStatus(Long id, String status) {
        File existing = getFileById(id);
        validateStatus(status);
        existing.setStatus(status.trim().toUpperCase());
        return fileRepository.save(existing);
    }

    public File updateFileStatus(Long id, String status, Long currentOrgId) {
        File existing = getFileById(id, currentOrgId);
        validateStatus(status);
        existing.setStatus(status.trim().toUpperCase());
        return fileRepository.save(existing);
    }

    public File updateFileMetadata(Long id, File metadata) {
        File existing = getFileById(id);
        if (metadata.getOriginalName() != null) {
            existing.setOriginalName(metadata.getOriginalName());
        }
        if (metadata.getStorageKey() != null) {
            existing.setStorageKey(metadata.getStorageKey());
        }
        if (metadata.getContentType() != null) {
            existing.setContentType(metadata.getContentType());
        }
        if (metadata.getSize() != null) {
            existing.setSize(metadata.getSize());
        }
        if (metadata.getReceivedAt() != null) {
            existing.setReceivedAt(metadata.getReceivedAt());
        }
        return fileRepository.save(existing);
    }

    public File updateFileMetadata(Long id, File metadata, Long currentOrgId) {
        File existing = getFileById(id, currentOrgId);
        if (metadata.getOriginalName() != null) {
            existing.setOriginalName(metadata.getOriginalName());
        }
        if (metadata.getStorageKey() != null) {
            existing.setStorageKey(metadata.getStorageKey());
        }
        if (metadata.getContentType() != null) {
            existing.setContentType(metadata.getContentType());
        }
        if (metadata.getSize() != null) {
            existing.setSize(metadata.getSize());
        }
        if (metadata.getReceivedAt() != null) {
            existing.setReceivedAt(metadata.getReceivedAt());
        }
        return fileRepository.save(existing);
    }

    // --- Delete ---

    public void deleteFile(Long id) {
        File existing = getFileById(id);
        fileRepository.delete(existing);
    }

    @org.springframework.transaction.annotation.Transactional
    public void deleteFile(Long id, Long currentOrgId) {
        File existing = getFileById(id, currentOrgId);
        // Prevent unsafe deletion if the file is the last reference for an integration
        if (existing.getIntegration() != null && existing.getIntegration().getId() != null) {
            Long integrationId = existing.getIntegration().getId();
            long fileCount = fileRepository.countByIntegrationIdAndOrganisationOrgId(integrationId, currentOrgId);
            if (fileCount <= 1) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot delete file that is the last reference for integration ID: " + integrationId);
            }
        }
        fileRepository.delete(existing);
    }
}
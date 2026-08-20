package com.investigation.platform.file.service.impl;

import com.investigation.platform.audit.enums.AuditAction;
import com.investigation.platform.audit.service.AuditLogService;
import com.investigation.platform.common.dto.PageResponse;
import com.investigation.platform.exception.BadRequestException;
import com.investigation.platform.exception.ResourceNotFoundException;
import com.investigation.platform.file.dto.request.RegisterFileRequest;
import com.investigation.platform.file.dto.response.FileResponse;
import com.investigation.platform.file.entity.File;
import com.investigation.platform.file.mapper.FileMapper;
import com.investigation.platform.file.repository.FileRepository;
import com.investigation.platform.file.service.FileService;
import com.investigation.platform.file.service.FileStorageService;
import com.investigation.platform.integration.repository.IntegrationRepository;
import com.investigation.platform.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    private final FileRepository fileRepository;
    private final IntegrationRepository integrationRepository;
    private final FileMapper fileMapper;
    private final FileStorageService fileStorageService;
    private final AuditLogService auditLogService;

    @Override
    @Transactional
    public FileResponse registerFile(RegisterFileRequest request) {
        UUID orgId = TenantContext.getRequiredTenantId();

        if (request.getIntegrationId() != null) {
            if (!integrationRepository.existsByIntIdAndOrgId(request.getIntegrationId(), orgId)) {
                throw new BadRequestException("Referenced integration does not belong to this organization");
            }
        }

        File file = fileMapper.toEntity(request, orgId);
        File saved = fileRepository.save(file);
        log.info("File registered: {} with key: {}", saved.getFileName(), saved.getStorageKey());

        auditLogService.recordCurrentTenantEvent(
                null,
                AuditAction.FILE_UPLOADED,
                "FILE",
                saved.getFileId().toString(),
                "File registered: " + saved.getFileName()
        );

        return fileMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public FileResponse getFileById(UUID fileId) {
        UUID orgId = TenantContext.getRequiredTenantId();
        File file = fileRepository.findByFileIdAndOrgId(fileId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("File", "fileId", fileId));
        return fileMapper.toResponse(file);
    }

    @Override
    @Transactional
    public void deleteFile(UUID fileId) {
        UUID orgId = TenantContext.getRequiredTenantId();
        File file = fileRepository.findByFileIdAndOrgId(fileId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("File", "fileId", fileId));

        fileStorageService.deleteFile(file.getStorageKey());
        fileRepository.deleteByFileIdAndOrgId(fileId, orgId);
        log.info("File deleted: {} from org: {}", fileId, orgId);

        auditLogService.recordCurrentTenantEvent(
                null,
                AuditAction.FILE_DELETED,
                "FILE",
                fileId.toString(),
                "File deleted: " + file.getFileName()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<FileResponse> getAllFiles(Pageable pageable) {
        UUID orgId = TenantContext.getRequiredTenantId();
        Page<File> page = fileRepository.findByOrgId(orgId, pageable);
        return PageResponse.from(page.map(fileMapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<FileResponse> getFilesByIntegrationId(UUID integrationId, Pageable pageable) {
        UUID orgId = TenantContext.getRequiredTenantId();
        Page<File> page = fileRepository.findByOrgIdAndIntegrationId(orgId, integrationId, pageable);
        return PageResponse.from(page.map(fileMapper::toResponse));
    }
}

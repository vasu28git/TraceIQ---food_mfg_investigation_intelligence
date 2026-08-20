package com.investigation.platform.file.mapper;

import com.investigation.platform.file.dto.request.RegisterFileRequest;
import com.investigation.platform.file.dto.response.FileResponse;
import com.investigation.platform.file.entity.File;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class FileMapper {

    public File toEntity(RegisterFileRequest request, UUID orgId) {
        if (request == null) {
            return null;
        }
        return File.builder()
                .orgId(orgId)
                .fileName(request.getFileName())
                .fileType(request.getFileType())
                .storageKey(request.getStorageKey())
                .fileSize(request.getFileSize())
                .integrationId(request.getIntegrationId())
                .build();
    }

    public FileResponse toResponse(File entity) {
        if (entity == null) {
            return null;
        }
        return FileResponse.builder()
                .fileId(entity.getFileId())
                .orgId(entity.getOrgId())
                .integrationId(entity.getIntegrationId())
                .fileName(entity.getFileName())
                .fileType(entity.getFileType())
                .storageKey(entity.getStorageKey())
                .fileSize(entity.getFileSize())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}

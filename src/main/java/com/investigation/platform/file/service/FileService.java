package com.investigation.platform.file.service;

import com.investigation.platform.common.dto.PageResponse;
import com.investigation.platform.file.dto.request.RegisterFileRequest;
import com.investigation.platform.file.dto.response.FileResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface FileService {

    FileResponse registerFile(RegisterFileRequest request);

    FileResponse getFileById(UUID fileId);

    void deleteFile(UUID fileId);

    PageResponse<FileResponse> getAllFiles(Pageable pageable);

    PageResponse<FileResponse> getFilesByIntegrationId(UUID integrationId, Pageable pageable);
}

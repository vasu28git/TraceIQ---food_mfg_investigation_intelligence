package com.investigation.platform.file.controller;

import com.investigation.platform.common.dto.ApiResponse;
import com.investigation.platform.common.dto.PageResponse;
import com.investigation.platform.file.dto.request.RegisterFileRequest;
import com.investigation.platform.file.dto.response.FileResponse;
import com.investigation.platform.file.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
@Tag(name = "Files", description = "Endpoints for file and evidence metadata management within the tenant")
public class FileController {

    private final FileService fileService;

    @PostMapping
    @Operation(summary = "Register file metadata within tenant")
    @PreAuthorize("hasAuthority('FILE_UPLOAD') or hasRole('ORG_ADMIN')")
    public ResponseEntity<ApiResponse<FileResponse>> registerFile(@Valid @RequestBody RegisterFileRequest request) {
        FileResponse response = fileService.registerFile(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(response, "File registered successfully"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get file metadata by ID within tenant")
    @PreAuthorize("hasAuthority('FILE_READ') or hasRole('ORG_ADMIN')")
    public ResponseEntity<ApiResponse<FileResponse>> getFileById(@PathVariable("id") UUID id) {
        FileResponse response = fileService.getFileById(id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete file within tenant")
    @PreAuthorize("hasAuthority('FILE_DELETE') or hasRole('ORG_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteFile(@PathVariable("id") UUID id) {
        fileService.deleteFile(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "File deleted successfully"));
    }

    @GetMapping
    @Operation(summary = "List files within tenant (paginated)")
    @PreAuthorize("hasAuthority('FILE_READ') or hasRole('ORG_ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<FileResponse>>> getAllFiles(
            @RequestParam(name = "integrationId", required = false) UUID integrationId,
            @PageableDefault(size = 20) Pageable pageable) {
        PageResponse<FileResponse> response;
        if (integrationId != null) {
            response = fileService.getFilesByIntegrationId(integrationId, pageable);
        } else {
            response = fileService.getAllFiles(pageable);
        }
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}

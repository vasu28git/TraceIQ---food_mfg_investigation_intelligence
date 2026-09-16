package com.taceiq.controller;

import com.taceiq.entity.File;
import com.taceiq.security.AuthorizationService;
import com.taceiq.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;
    private final AuthorizationService authorizationService;

    @PostMapping
    public ResponseEntity<File> createFile(@RequestBody File file) {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireFileCreate();
        File created = fileService.createFile(file, orgId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<File> uploadFile(@RequestPart("file") MultipartFile file,
                                           @RequestParam(value = "incidentId", required = false) Long incidentId) {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireFileCreate();
        File created = fileService.uploadFile(file, orgId, incidentId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<File> getFileById(@PathVariable Long id) {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireFileRead();
        File file = fileService.getFileById(id, orgId);
        return ResponseEntity.ok(file);
    }

    @GetMapping("/integration/{integrationId}")
    public ResponseEntity<List<File>> getFilesByIntegration(@PathVariable Long integrationId) {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireFileRead();
        List<File> files = fileService.getFilesByIntegration(integrationId, orgId);
        return ResponseEntity.ok(files);
    }

    @GetMapping("/organisation/{orgId}")
    public ResponseEntity<List<File>> getFilesByOrganisation(@PathVariable Long orgId) {
        Long currentOrgId = authorizationService.getCurrentOrgId();
        authorizationService.requireFileRead();
        List<File> files = fileService.getFilesByOrganisation(orgId, currentOrgId);
        return ResponseEntity.ok(files);
    }

    @PutMapping("/{id}/metadata")
    public ResponseEntity<File> updateFileMetadata(@PathVariable Long id, @RequestBody File metadata) {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireFileUpdate();
        File updated = fileService.updateFileMetadata(id, metadata, orgId);
        return ResponseEntity.ok(updated);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<File> updateFileStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireFileUpdate();
        String status = body.get("status");
        File updated = fileService.updateFileStatus(id, status, orgId);
        return ResponseEntity.ok(updated);
    }

    // keep backward compat for full update
    @PutMapping("/{id}")
    public ResponseEntity<File> updateFile(@PathVariable Long id, @RequestBody File file) {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireFileUpdate();
        File updated = fileService.updateFile(id, file, orgId);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFile(@PathVariable Long id) {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireFileDelete();
        fileService.deleteFile(id, orgId);
        return ResponseEntity.noContent().build();
    }
}

package com.taceiq.controller;

import com.taceiq.ingestion.SourceRecordIngestionService;
import com.taceiq.security.AuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ingestion")
@RequiredArgsConstructor
public class IngestionController {

    private final SourceRecordIngestionService ingestionService;
    private final AuthorizationService authorizationService;

    @PostMapping("/preingest")
    public ResponseEntity<SourceRecordIngestionService.IngestionResult> preingest() {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireEvidenceGraphAccess();
        SourceRecordIngestionService.IngestionResult r = ingestionService.ingestExistingFilesForOrg(orgId);
        return ResponseEntity.ok(r);
    }

    @PostMapping("/file/{fileId}")
    public ResponseEntity<SourceRecordIngestionService.IngestionResult> ingestFile(@PathVariable Long fileId) {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireEvidenceGraphAccess();
        SourceRecordIngestionService.IngestionResult r = ingestionService.ingestFile(orgId, fileId, null);
        return ResponseEntity.ok(r);
    }
}

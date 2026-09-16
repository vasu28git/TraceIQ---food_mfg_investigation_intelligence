package com.taceiq.graph;

import com.taceiq.graph.dto.CaseFileResponse;
import com.taceiq.graph.dto.GraphEvidenceDetailResponse;
import com.taceiq.graph.service.GraphWorkspaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/graph")
@RequiredArgsConstructor
public class GraphWorkspaceController {

    private final GraphWorkspaceService workspaceService;

    @GetMapping("/evidence/{stableId}")
    public ResponseEntity<GraphEvidenceDetailResponse> getEvidence(@PathVariable String stableId) {
        GraphEvidenceDetailResponse resp = workspaceService.getEvidenceDetail(stableId);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/cases/{caseId}")
    public ResponseEntity<CaseFileResponse> getCaseFile(
            @PathVariable String caseId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        CaseFileResponse resp = workspaceService.getCaseFile(caseId, page, size);
        return ResponseEntity.ok(resp);
    }
}

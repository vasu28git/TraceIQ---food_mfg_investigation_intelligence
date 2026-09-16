package com.taceiq.controller;

import com.taceiq.dto.InvestigationTimelineResponse;
import com.taceiq.service.InvestigationTimelineService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/investigations/{investigationId}/timeline")
@RequiredArgsConstructor
public class InvestigationTimelineController {

    private final InvestigationTimelineService timelineService;

    @GetMapping
    public ResponseEntity<InvestigationTimelineResponse> getTimeline(
            @PathVariable Long investigationId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        InvestigationTimelineResponse resp = timelineService.getTimeline(investigationId, page, size);
        return ResponseEntity.ok(resp);
    }
}

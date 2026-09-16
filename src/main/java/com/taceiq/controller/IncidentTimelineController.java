package com.taceiq.controller;

import com.taceiq.dto.InvestigationTimelineResponse;
import com.taceiq.service.InvestigationTimelineService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Incident alias for timeline — delegates to same service, preserves tenant isolation.
 * Investigation remains persistence; Incident is domain alias.
 */
@RestController
@RequestMapping("/api/incidents/{incidentId}/timeline")
@RequiredArgsConstructor
public class IncidentTimelineController {

    private final InvestigationTimelineService timelineService;

    @GetMapping
    public ResponseEntity<InvestigationTimelineResponse> getTimeline(
            @PathVariable Long incidentId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok(timelineService.getTimeline(incidentId, page, size));
    }
}

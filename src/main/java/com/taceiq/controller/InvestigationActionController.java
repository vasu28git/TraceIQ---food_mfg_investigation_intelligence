package com.taceiq.controller;

import com.taceiq.dto.InvestigationActionRequest;
import com.taceiq.dto.InvestigationActionResponse;
import com.taceiq.service.InvestigationActionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/investigations/{investigationId}/actions")
@RequiredArgsConstructor
public class InvestigationActionController {
    private final InvestigationActionService service;
    @GetMapping public List<InvestigationActionResponse> list(@PathVariable Long investigationId) { return service.list(investigationId); }
    @GetMapping("/{actionId}") public InvestigationActionResponse get(@PathVariable Long investigationId, @PathVariable Long actionId) { return service.get(investigationId, actionId); }
    @PostMapping public ResponseEntity<InvestigationActionResponse> create(@PathVariable Long investigationId, @Valid @RequestBody InvestigationActionRequest request) { return ResponseEntity.status(HttpStatus.CREATED).body(service.create(investigationId, request)); }
    @PutMapping("/{actionId}") public InvestigationActionResponse update(@PathVariable Long investigationId, @PathVariable Long actionId, @Valid @RequestBody InvestigationActionRequest request) { return service.update(investigationId, actionId, request); }
}

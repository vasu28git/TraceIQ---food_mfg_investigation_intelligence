package com.taceiq.controller;

import com.taceiq.dto.ComplaintResponse;
import com.taceiq.dto.CreateComplaintRequest;
import com.taceiq.dto.CreateInvestigationFromComplaintRequest;
import com.taceiq.dto.InvestigationResponse;
import com.taceiq.service.ComplaintService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/complaints")
@RequiredArgsConstructor
public class ComplaintController {

    private final ComplaintService complaintService;

    @PostMapping
    public ResponseEntity<ComplaintResponse> createManual(@Valid @RequestBody CreateComplaintRequest req) {
        ComplaintResponse resp = complaintService.createManual(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    @GetMapping
    public ResponseEntity<org.springframework.data.domain.Page<ComplaintResponse>> listComplaints(org.springframework.data.domain.Pageable pageable) {
        org.springframework.data.domain.Page<ComplaintResponse> page = complaintService.listComplaints(pageable);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/{complaintId}")
    public ResponseEntity<ComplaintResponse> getComplaint(@PathVariable Long complaintId) {
        ComplaintResponse resp = complaintService.getComplaint(complaintId);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/{complaintId}/investigation")
    public ResponseEntity<InvestigationResponse> createInvestigationFromComplaint(
            @PathVariable Long complaintId,
            @Valid @RequestBody CreateInvestigationFromComplaintRequest req) {
        InvestigationResponse resp = complaintService.createInvestigationFromComplaint(complaintId, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }
}

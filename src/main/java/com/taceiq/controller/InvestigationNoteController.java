package com.taceiq.controller;

import com.taceiq.dto.InvestigationNoteRequest;
import com.taceiq.dto.InvestigationNoteResponse;
import com.taceiq.service.InvestigationNoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/investigations/{investigationId}/notes")
@RequiredArgsConstructor
public class InvestigationNoteController {

    private final InvestigationNoteService service;

    @PostMapping
    public ResponseEntity<InvestigationNoteResponse> create(@PathVariable Long investigationId,
                                                            @Valid @RequestBody InvestigationNoteRequest req) {
        InvestigationNoteResponse resp = service.createNote(investigationId, req.getContent());
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    @GetMapping
    public ResponseEntity<Page<InvestigationNoteResponse>> list(
            @PathVariable Long investigationId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        Page<InvestigationNoteResponse> resp = service.listNotes(investigationId, page, size);
        return ResponseEntity.ok(resp);
    }

    @PatchMapping("/{noteId}")
    public ResponseEntity<InvestigationNoteResponse> update(@PathVariable Long investigationId,
                                                            @PathVariable Long noteId,
                                                            @Valid @RequestBody InvestigationNoteRequest req) {
        InvestigationNoteResponse resp = service.updateNote(investigationId, noteId, req.getContent());
        return ResponseEntity.ok(resp);
    }

    @DeleteMapping("/{noteId}")
    public ResponseEntity<Void> delete(@PathVariable Long investigationId,
                                       @PathVariable Long noteId) {
        service.deleteNote(investigationId, noteId);
        return ResponseEntity.noContent().build();
    }
}

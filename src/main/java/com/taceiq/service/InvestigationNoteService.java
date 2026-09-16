package com.taceiq.service;

import com.taceiq.dto.InvestigationNoteResponse;
import com.taceiq.entity.Investigation;
import com.taceiq.entity.InvestigationNote;
import com.taceiq.graph.service.GraphReadinessService;
import com.taceiq.repository.InvestigationNoteRepository;
import com.taceiq.repository.InvestigationRepository;
import com.taceiq.security.AuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class InvestigationNoteService {

    private final InvestigationRepository investigationRepository;
    private final InvestigationNoteRepository noteRepository;
    private final AuthorizationService authorizationService;
    private final GraphReadinessService graphReadinessService;

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private Investigation loadInvestigation(Long investigationId) {
        Long orgId = authorizationService.getCurrentOrgId();
        return investigationRepository.findByIdAndOrganisationOrgId(investigationId, orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Investigation not found with id: " + investigationId));
    }

    private void ensureGraphReady(Long orgId) {
        if (!graphReadinessService.isOrgGraphReady(orgId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Graph not ready for organisation " + orgId);
        }
    }

    private void ensureMutable(Investigation inv) {
        String status = inv.getStatus();
        if ("COMPLETED".equals(status) || "ARCHIVED".equals(status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Investigation is " + status + " and cannot be modified");
        }
        if (!"DRAFT".equals(status) && !"ACTIVE".equals(status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Investigation status " + status + " does not allow note mutation");
        }
    }

    @Transactional
    public InvestigationNoteResponse createNote(Long investigationId, String content) {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireEvidenceGraphAccess();
        ensureGraphReady(orgId);
        Investigation inv = loadInvestigation(investigationId);
        ensureMutable(inv);
        String c = content != null ? content.trim() : null;
        if (c == null || c.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "content is required");
        if (c.length() > 5000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "content max 5000");
        InvestigationNote note = InvestigationNote.builder()
                .organisation(inv.getOrganisation())
                .investigation(inv)
                .author(authorizationService.getCurrentUser())
                .content(c)
                .build();
        InvestigationNote saved = noteRepository.save(note);
        return InvestigationNoteResponse.fromEntity(saved);
    }

    public Page<InvestigationNoteResponse> listNotes(Long investigationId, Integer page, Integer size) {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireEvidenceGraphAccess();
        ensureGraphReady(orgId);
        loadInvestigation(investigationId);
        int p = page != null ? page : 0;
        int s = size != null ? size : DEFAULT_SIZE;
        if (p < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be >=0");
        if (s < 1 || s > MAX_SIZE) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "size must be 1-100");
        Page<InvestigationNote> pageResult = noteRepository.findByOrganisationOrgIdAndInvestigationId(orgId, investigationId, PageRequest.of(p, s, Sort.by("createdAt").ascending()));
        return pageResult.map(InvestigationNoteResponse::fromEntity);
    }

    @Transactional
    public InvestigationNoteResponse updateNote(Long investigationId, Long noteId, String content) {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireEvidenceGraphAccess();
        ensureGraphReady(orgId);
        Investigation inv = loadInvestigation(investigationId);
        ensureMutable(inv);
        InvestigationNote note = noteRepository.findByIdAndOrganisationOrgIdAndInvestigationId(noteId, orgId, investigationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Note not found with id: " + noteId));
        if (!note.getAuthor().getId().equals(authorizationService.getCurrentUser().getId())) {
            throw new AccessDeniedException("Only the original author may update the note");
        }
        String c = content != null ? content.trim() : null;
        if (c == null || c.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "content is required");
        if (c.length() > 5000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "content max 5000");
        note.setContent(c);
        InvestigationNote saved = noteRepository.save(note);
        return InvestigationNoteResponse.fromEntity(saved);
    }

    @Transactional
    public void deleteNote(Long investigationId, Long noteId) {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireEvidenceGraphAccess();
        ensureGraphReady(orgId);
        Investigation inv = loadInvestigation(investigationId);
        ensureMutable(inv);
        InvestigationNote note = noteRepository.findByIdAndOrganisationOrgIdAndInvestigationId(noteId, orgId, investigationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Note not found with id: " + noteId));
        if (!note.getAuthor().getId().equals(authorizationService.getCurrentUser().getId())) {
            throw new AccessDeniedException("Only the original author may delete the note");
        }
        noteRepository.delete(note);
    }
}

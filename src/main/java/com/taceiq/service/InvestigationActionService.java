package com.taceiq.service;

import com.taceiq.dto.InvestigationActionRequest;
import com.taceiq.dto.InvestigationActionResponse;
import com.taceiq.entity.*;
import com.taceiq.repository.*;
import com.taceiq.security.AuthorizationService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
public class InvestigationActionService {
    private static final Set<String> PRIORITIES = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    private static final Set<String> STATUSES = Set.of("OPEN", "IN_PROGRESS", "COMPLETED");
    private final InvestigationRepository investigationRepository;
    private final InvestigationActionRepository actionRepository;
    private final InvestigationFindingRepository findingRepository;
    private final InvestigationConclusionRepository conclusionRepository;
    private final UserRepository userRepository;
    private final AuthorizationService authorizationService;

    public InvestigationActionService(InvestigationRepository investigationRepository, InvestigationActionRepository actionRepository,
                                      InvestigationFindingRepository findingRepository, InvestigationConclusionRepository conclusionRepository,
                                      UserRepository userRepository, AuthorizationService authorizationService) {
        this.investigationRepository = investigationRepository; this.actionRepository = actionRepository;
        this.findingRepository = findingRepository; this.conclusionRepository = conclusionRepository;
        this.userRepository = userRepository; this.authorizationService = authorizationService;
    }
    private Long orgId() { return authorizationService.getCurrentOrgId(); }
    private void access() { authorizationService.requireAnyPermission("WORKSPACE_ACCESS", "INVESTIGATION_ACCESS", "DECISION_ACCESS"); }
    private Investigation investigation(Long id) { return investigationRepository.findByIdAndOrganisationOrgId(id, orgId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Investigation not found")); }
    @Transactional(readOnly = true) public List<InvestigationActionResponse> list(Long investigationId) { access(); investigation(investigationId); return actionRepository.findByOrganisationOrgIdAndInvestigationIdOrderByDueDateAscUpdatedAtDesc(orgId(), investigationId).stream().map(this::response).toList(); }
    @Transactional(readOnly = true) public InvestigationActionResponse get(Long investigationId, Long actionId) { access(); investigation(investigationId); return response(actionRepository.findByIdAndOrganisationOrgIdAndInvestigationId(actionId, orgId(), investigationId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Action not found"))); }
    @Transactional public InvestigationActionResponse create(Long investigationId, InvestigationActionRequest request) { access(); Investigation inv = investigation(investigationId); validate(request); InvestigationAction action = newAction(inv, request); return response(actionRepository.save(action)); }
    @Transactional public InvestigationActionResponse update(Long investigationId, Long actionId, InvestigationActionRequest request) { access(); Investigation inv = investigation(investigationId); validate(request); InvestigationAction action = actionRepository.findByIdAndOrganisationOrgIdAndInvestigationId(actionId, orgId(), investigationId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Action not found")); apply(action, inv, request); return response(actionRepository.save(action)); }
    private InvestigationAction newAction(Investigation inv, InvestigationActionRequest r) { InvestigationAction a = InvestigationAction.builder().organisation(inv.getOrganisation()).investigation(inv).createdBy(authorizationService.getCurrentUser()).build(); apply(a, inv, r); return a; }
    private void apply(InvestigationAction a, Investigation inv, InvestigationActionRequest r) {
        a.setTitle(required(r.getTitle(), "Action title is required")); a.setDescription(trim(r.getDescription())); a.setActionType(trim(r.getActionType()));
        a.setPriority(value(r.getPriority(), "MEDIUM")); a.setStatus(value(r.getStatus(), "OPEN")); a.setDueDate(r.getDueDate()); a.setNotes(trim(r.getNotes())); a.setUpdatedBy(authorizationService.getCurrentUser());
        a.setOwner(r.getOwnerUserId() == null ? null : userRepository.findByIdAndOrganisationOrgId(r.getOwnerUserId(), orgId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Owner does not belong to this organisation")));
        a.setFinding(r.getFindingId() == null ? null : findingRepository.findByIdAndOrganisationOrgIdAndInvestigationId(r.getFindingId(), orgId(), inv.getId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Finding does not belong to this investigation")));
        a.setConclusion(r.getConclusionId() == null ? null : conclusionRepository.findById(r.getConclusionId()).filter(c -> orgId().equals(c.getOrganisation().getOrgId()) && inv.getId().equals(c.getInvestigation().getId())).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Conclusion does not belong to this investigation")));
    }
    private void validate(InvestigationActionRequest r) { if (r == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Action request is required"); String p = value(r.getPriority(), "MEDIUM"), s = value(r.getStatus(), "OPEN"); if (!PRIORITIES.contains(p)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "priority must be one of " + PRIORITIES); if (!STATUSES.contains(s)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status must be one of " + STATUSES); }
    private InvestigationActionResponse response(InvestigationAction a) { return InvestigationActionResponse.builder().id(a.getId()).investigationId(a.getInvestigation().getId()).title(a.getTitle()).description(a.getDescription()).actionType(a.getActionType()).ownerUserId(a.getOwner() == null ? null : a.getOwner().getId()).ownerUsername(a.getOwner() == null ? null : a.getOwner().getUsername()).priority(a.getPriority()).dueDate(a.getDueDate()).status(a.getStatus()).notes(a.getNotes()).findingId(a.getFinding() == null ? null : a.getFinding().getId()).findingStatement(a.getFinding() == null ? null : a.getFinding().getTitle()).conclusionId(a.getConclusion() == null ? null : a.getConclusion().getId()).conclusionSummary(a.getConclusion() == null ? null : a.getConclusion().getSummary()).createdByUserId(a.getCreatedBy() == null ? null : a.getCreatedBy().getId()).updatedByUserId(a.getUpdatedBy() == null ? null : a.getUpdatedBy().getId()).createdAt(a.getCreatedAt()).updatedAt(a.getUpdatedAt()).build(); }
    private static String value(String s, String fallback) { return s == null || s.isBlank() ? fallback : s.trim().toUpperCase(Locale.ROOT); }
    private static String trim(String s) { return s == null || s.isBlank() ? null : s.trim(); }
    private static String required(String s, String message) { String v = trim(s); if (v == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message); return v; }
}

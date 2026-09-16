package com.taceiq;

import com.taceiq.dto.InvestigationNoteResponse;
import com.taceiq.entity.Investigation;
import com.taceiq.entity.InvestigationNote;
import com.taceiq.entity.Organisation;
import com.taceiq.entity.User;
import com.taceiq.graph.service.GraphReadinessService;
import com.taceiq.repository.InvestigationNoteRepository;
import com.taceiq.repository.InvestigationRepository;
import com.taceiq.security.AuthorizationService;
import com.taceiq.service.InvestigationNoteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class InvestigationNoteServiceTest {

    @Mock InvestigationRepository investigationRepository;
    @Mock InvestigationNoteRepository noteRepository;
    @Mock AuthorizationService authService;
    @Mock GraphReadinessService graphReadinessService;

    InvestigationNoteService service;

    Organisation org1 = Organisation.builder().orgId(1L).name("Org1").build();
    User user1 = User.builder().id(10L).username("user1").organisation(org1).build();
    User user2 = User.builder().id(20L).username("user2").organisation(org1).build();
    Investigation invDraft = Investigation.builder().id(100L).organisation(org1).investigationKey("INV-1").title("T").status("DRAFT").createdBy(user1).build();
    Investigation invCompleted = Investigation.builder().id(101L).organisation(org1).investigationKey("INV-2").title("T2").status("COMPLETED").createdBy(user1).build();
    Investigation invArchived = Investigation.builder().id(102L).organisation(org1).investigationKey("INV-3").title("T3").status("ARCHIVED").createdBy(user1).build();

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        service = new InvestigationNoteService(investigationRepository, noteRepository, authService, graphReadinessService);
        lenient().when(authService.getCurrentOrgId()).thenReturn(1L);
        lenient().when(authService.getCurrentUser()).thenReturn(user1);
        lenient().doNothing().when(authService).requireEvidenceGraphAccess();
        lenient().when(graphReadinessService.isOrgGraphReady(1L)).thenReturn(true);
        lenient().when(investigationRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.of(invDraft));
        lenient().when(noteRepository.save(any())).thenAnswer(i -> {
            InvestigationNote n = i.getArgument(0);
            n.setId(200L);
            n.setCreatedAt(Instant.now());
            n.setUpdatedAt(Instant.now());
            return n;
        });
    }

    @Test
    void validCreate() {
        InvestigationNoteResponse resp = service.createNote(100L, "my note");
        assertEquals("my note", resp.getContent());
        assertEquals(10L, resp.getAuthorUserId());
        verify(noteRepository).save(any());
    }

    @Test
    void listNotes() {
        InvestigationNote note = InvestigationNote.builder().id(200L).organisation(org1).investigation(invDraft).author(user1).content("c").createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(noteRepository.findByOrganisationOrgIdAndInvestigationId(eq(1L), eq(100L), any())).thenReturn(new PageImpl<>(List.of(note)));
        var page = service.listNotes(100L, 0, 20);
        assertEquals(1, page.getTotalElements());
    }

    @Test
    void updateByAuthor() {
        InvestigationNote note = InvestigationNote.builder().id(200L).organisation(org1).investigation(invDraft).author(user1).content("old").build();
        when(noteRepository.findByIdAndOrganisationOrgIdAndInvestigationId(200L, 1L, 100L)).thenReturn(Optional.of(note));
        InvestigationNoteResponse resp = service.updateNote(100L, 200L, "updated");
        assertEquals("updated", resp.getContent());
    }

    @Test
    void updateByNonAuthor403() {
        InvestigationNote note = InvestigationNote.builder().id(200L).organisation(org1).investigation(invDraft).author(user1).content("old").build();
        when(noteRepository.findByIdAndOrganisationOrgIdAndInvestigationId(200L, 1L, 100L)).thenReturn(Optional.of(note));
        when(authService.getCurrentUser()).thenReturn(user2);
        assertThrows(AccessDeniedException.class, () -> service.updateNote(100L, 200L, "updated"));
    }

    @Test
    void deleteByAuthor() {
        InvestigationNote note = InvestigationNote.builder().id(200L).organisation(org1).investigation(invDraft).author(user1).content("c").build();
        when(noteRepository.findByIdAndOrganisationOrgIdAndInvestigationId(200L, 1L, 100L)).thenReturn(Optional.of(note));
        assertDoesNotThrow(() -> service.deleteNote(100L, 200L));
        verify(noteRepository).delete(note);
    }

    @Test
    void deleteByNonAuthor403() {
        InvestigationNote note = InvestigationNote.builder().id(200L).organisation(org1).investigation(invDraft).author(user1).content("c").build();
        when(noteRepository.findByIdAndOrganisationOrgIdAndInvestigationId(200L, 1L, 100L)).thenReturn(Optional.of(note));
        when(authService.getCurrentUser()).thenReturn(user2);
        assertThrows(AccessDeniedException.class, () -> service.deleteNote(100L, 200L));
    }

    @Test
    void missingNote404() {
        when(noteRepository.findByIdAndOrganisationOrgIdAndInvestigationId(999L, 1L, 100L)).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> service.updateNote(100L, 999L, "x"));
    }

    @Test
    void crossTenantNote404() {
        when(noteRepository.findByIdAndOrganisationOrgIdAndInvestigationId(200L, 1L, 100L)).thenReturn(Optional.empty());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.updateNote(100L, 200L, "x"));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void completedInvestigationRejectedCreate409() {
        when(investigationRepository.findByIdAndOrganisationOrgId(101L, 1L)).thenReturn(Optional.of(invCompleted));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.createNote(101L, "c"));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void archivedInvestigationRejectedCreate409() {
        when(investigationRepository.findByIdAndOrganisationOrgId(102L, 1L)).thenReturn(Optional.of(invArchived));
        assertThrows(ResponseStatusException.class, () -> service.createNote(102L, "c"));
    }

    @Test
    void graphNotReadyCreate400() {
        when(graphReadinessService.isOrgGraphReady(1L)).thenReturn(false);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.createNote(100L, "c"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(noteRepository, never()).save(any());
    }

    @Test
    void permissionDeniedCreate403() {
        doThrow(new AccessDeniedException("Missing")).when(authService).requireEvidenceGraphAccess();
        assertThrows(AccessDeniedException.class, () -> service.createNote(100L, "c"));
    }

    @Test
    void boundedPagination() {
        when(noteRepository.findByOrganisationOrgIdAndInvestigationId(eq(1L), eq(100L), any())).thenReturn(new PageImpl<>(List.of(
                InvestigationNote.builder().id(1L).organisation(org1).investigation(invDraft).author(user1).content("c").build()
        )));
        var page = service.listNotes(100L, 0, 20);
        assertEquals(1, page.getTotalElements());
        assertThrows(ResponseStatusException.class, () -> service.listNotes(100L, 0, 101));
    }

    @Test
    void tenantIsolationList() {
        when(noteRepository.findByOrganisationOrgIdAndInvestigationId(eq(1L), eq(100L), any())).thenReturn(new PageImpl<>(List.of(
                com.taceiq.entity.InvestigationNote.builder().id(1L).organisation(org1).investigation(invDraft).author(user1).content("c").build()
        )));
        service.listNotes(100L, 0, 20);
        verify(noteRepository).findByOrganisationOrgIdAndInvestigationId(eq(1L), eq(100L), any());
        verify(noteRepository, never()).findByOrganisationOrgIdAndInvestigationId(eq(2L), anyLong(), any());
    }

    @Test
    void graphNotReadyDoesNotTriggerNeo4j() {
        when(graphReadinessService.isOrgGraphReady(1L)).thenReturn(false);
        assertThrows(ResponseStatusException.class, () -> service.createNote(100L, "c"));
        verify(noteRepository, never()).save(any());
        // graphReadinessService was called, but no further neo4j access (we don't call graph directly)
        verify(graphReadinessService).isOrgGraphReady(1L);
    }
}

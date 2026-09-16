package com.taceiq;

import com.taceiq.dto.InvestigationActionRequest;
import com.taceiq.entity.*;
import com.taceiq.repository.*;
import com.taceiq.security.AuthorizationService;
import com.taceiq.service.InvestigationActionService;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InvestigationActionServiceTest {
    @Mock InvestigationRepository investigationRepository; @Mock InvestigationActionRepository actionRepository; @Mock InvestigationFindingRepository findingRepository; @Mock InvestigationConclusionRepository conclusionRepository; @Mock UserRepository userRepository; @Mock AuthorizationService authorizationService;
    InvestigationActionService service; Organisation org = Organisation.builder().orgId(1L).name("Org").build(); User user = User.builder().id(10L).username("investigator").organisation(org).build(); Investigation inv = Investigation.builder().id(8L).organisation(org).status("DRAFT").build(); InvestigationFinding finding = InvestigationFinding.builder().id(20L).organisation(org).investigation(inv).title("Finding").build();
    @BeforeEach void setup() { MockitoAnnotations.openMocks(this); service = new InvestigationActionService(investigationRepository, actionRepository, findingRepository, conclusionRepository, userRepository, authorizationService); when(authorizationService.getCurrentOrgId()).thenReturn(1L); when(authorizationService.getCurrentUser()).thenReturn(user); doNothing().when(authorizationService).requireAnyPermission(any(String[].class)); when(investigationRepository.findByIdAndOrganisationOrgId(8L, 1L)).thenReturn(Optional.of(inv)); when(findingRepository.findByIdAndOrganisationOrgIdAndInvestigationId(20L, 1L, 8L)).thenReturn(Optional.of(finding)); when(actionRepository.save(any())).thenAnswer(i -> { var a = i.getArgument(0, InvestigationAction.class); a.setId(4L); return a; }); }
    @Test void createsActionWithFindingReference() { when(userRepository.findByIdAndOrganisationOrgId(10L, 1L)).thenReturn(Optional.of(user)); var r = service.create(8L, request()); assertEquals("Action", r.getTitle()); assertEquals(20L, r.getFindingId()); assertEquals("HIGH", r.getPriority()); verify(actionRepository).save(any()); }
    @Test void updatesAction() { var existing = InvestigationAction.builder().id(4L).organisation(org).investigation(inv).title("Old").build(); when(actionRepository.findByIdAndOrganisationOrgIdAndInvestigationId(4L, 1L, 8L)).thenReturn(Optional.of(existing)); when(userRepository.findByIdAndOrganisationOrgId(10L, 1L)).thenReturn(Optional.of(user)); var r = service.update(8L, 4L, request()); assertEquals("Action", r.getTitle()); verify(actionRepository).save(existing); }
    @Test void rejectsCrossInvestigationFinding() { when(findingRepository.findByIdAndOrganisationOrgIdAndInvestigationId(99L, 1L, 8L)).thenReturn(Optional.empty()); var r = request(); r.setFindingId(99L); assertThrows(ResponseStatusException.class, () -> service.create(8L, r)); }
    @Test void rejectsCrossTenantInvestigation() { when(investigationRepository.findByIdAndOrganisationOrgId(9L, 1L)).thenReturn(Optional.empty()); assertThrows(ResponseStatusException.class, () -> service.create(9L, request())); }
    @Test void rejectsUnauthorizedAccess() { doThrow(new AccessDeniedException("Missing")).when(authorizationService).requireAnyPermission(any(String[].class)); assertThrows(AccessDeniedException.class, () -> service.list(8L)); }
    @Test void reloadsPersistedActionThroughScopedLookup() { var existing = InvestigationAction.builder().id(4L).organisation(org).investigation(inv).title("Persisted").priority("LOW").status("COMPLETED").build(); when(actionRepository.findByOrganisationOrgIdAndInvestigationIdOrderByDueDateAscUpdatedAtDesc(1L, 8L)).thenReturn(List.of(existing)); assertEquals("Persisted", service.list(8L).get(0).getTitle()); verify(actionRepository).findByOrganisationOrgIdAndInvestigationIdOrderByDueDateAscUpdatedAtDesc(1L, 8L); }
    @Test void rejectsInvalidPriority() { var r = request(); r.setPriority("URGENT"); assertThrows(ResponseStatusException.class, () -> service.create(8L, r)); verify(actionRepository, never()).save(any()); }
    private InvestigationActionRequest request() { var r = new InvestigationActionRequest(); r.setTitle("Action"); r.setDescription("Description"); r.setActionType("FOLLOW_UP"); r.setOwnerUserId(10L); r.setPriority("HIGH"); r.setStatus("OPEN"); r.setFindingId(20L); return r; }
}

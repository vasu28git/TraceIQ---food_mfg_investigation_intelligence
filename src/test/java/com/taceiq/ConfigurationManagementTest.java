package com.taceiq;

import com.taceiq.controller.ConfigurationController;
import com.taceiq.entity.Configuration;
import com.taceiq.entity.ConfigurationDefinition;
import com.taceiq.entity.Organisation;
import com.taceiq.repository.ConfigurationDefinitionRepository;
import com.taceiq.repository.ConfigurationRepository;
import com.taceiq.repository.OrganisationRepository;
import com.taceiq.security.AuthorizationService;
import com.taceiq.service.ConfigurationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ConfigurationManagementTest {

    @Mock ConfigurationRepository configurationRepository;
    @Mock ConfigurationDefinitionRepository definitionRepository;
    @Mock OrganisationRepository organisationRepository;
    @Mock AuthorizationService authorizationService;

    ConfigurationService configurationService;
    ConfigurationController configurationController;

    Organisation orgA;
    Organisation orgB;
    ConfigurationDefinition defA;
    ConfigurationDefinition defStrict;
    Configuration configA;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        configurationService = new ConfigurationService(configurationRepository, definitionRepository, organisationRepository);
        // For controller tests we inject a real service with mocked repos + mocked auth
        configurationController = new ConfigurationController(configurationService, authorizationService, organisationRepository);

        orgA = Organisation.builder().orgId(1L).name("OrgA").build();
        orgB = Organisation.builder().orgId(2L).name("OrgB").build();

        defA = ConfigurationDefinition.builder().id(10L).key("THEME").description("theme").allowedValues(new HashSet<>()).defaultValue("LIGHT").build();
        defStrict = ConfigurationDefinition.builder().id(11L).key("MODE").description("mode").allowedValues(new HashSet<>(Set.of("AUTO", "MANUAL"))).defaultValue("AUTO").build();

        configA = Configuration.builder().id(100L).organisation(orgA).definition(defA).value("LIGHT").build();

        // default lenient auth for service-level tests (service does not call auth, but controller does)
        lenient().doNothing().when(authorizationService).requireConfigCreate();
        lenient().doNothing().when(authorizationService).requireConfigRead();
        lenient().doNothing().when(authorizationService).requireConfigUpdate();
        lenient().doNothing().when(authorizationService).requireConfigDelete();
        lenient().when(authorizationService.getCurrentOrgId()).thenReturn(1L);
        lenient().when(authorizationService.isPlatformAdmin()).thenReturn(false);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    void setAuth(String username, String... authorities) {
        List<SimpleGrantedAuthority> granted = Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(username, null, granted);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // --- Create ---
    @Test
    void create_success() {
        when(definitionRepository.findByKey("THEME")).thenReturn(Optional.of(defA));
        when(configurationRepository.existsByDefinitionKeyAndOrganisationOrgId("THEME", 1L)).thenReturn(false);
        when(organisationRepository.getReferenceById(1L)).thenReturn(orgA);
        when(configurationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Configuration toCreate = Configuration.builder().organisation(orgA).definition(defA).value("DARK").build();
        // via service direct (org derived)
        Configuration created = configurationService.createConfiguration(1L, "THEME", "DARK");
        assertEquals("DARK", created.getValue());
        assertEquals(1L, created.getOrganisation().getOrgId());
    }

    @Test
    void create_usesDefaultWhenValueNull() {
        when(definitionRepository.findByKey("THEME")).thenReturn(Optional.of(defA));
        when(configurationRepository.existsByDefinitionKeyAndOrganisationOrgId("THEME", 1L)).thenReturn(false);
        when(organisationRepository.getReferenceById(1L)).thenReturn(orgA);
        when(configurationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Configuration created = configurationService.createConfiguration(1L, "THEME", null);
        assertEquals("LIGHT", created.getValue()); // default
    }

    @Test
    void create_duplicateKey_409() {
        when(definitionRepository.findByKey("THEME")).thenReturn(Optional.of(defA));
        when(configurationRepository.existsByDefinitionKeyAndOrganisationOrgId("THEME", 1L)).thenReturn(true);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> configurationService.createConfiguration(1L, "THEME", "DARK"));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void create_blankKey_400() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> configurationService.createConfiguration(1L, "  ", "val"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        ex = assertThrows(ResponseStatusException.class, () -> configurationService.createConfiguration(1L, null, "val"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void create_invalidValue_400() {
        when(definitionRepository.findByKey("MODE")).thenReturn(Optional.of(defStrict));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> configurationService.createConfiguration(1L, "MODE", "INVALID"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Allowed"));
    }

    @Test
    void create_undefinedKey_400() {
        when(definitionRepository.findByKey("UNKNOWN")).thenReturn(Optional.empty());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> configurationService.createConfiguration(1L, "UNKNOWN", "val"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void create_viaController_forcesOrg() {
        // Controller should ignore client org and force to auth org
        when(authorizationService.getCurrentOrgId()).thenReturn(1L);
        // need to mock definition lookup for controller's service call
        when(definitionRepository.findByKey("THEME")).thenReturn(Optional.of(defA));
        when(configurationRepository.existsByDefinitionKeyAndOrganisationOrgId("THEME", 1L)).thenReturn(false);
        when(organisationRepository.getReferenceById(1L)).thenReturn(orgA);
        when(configurationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Configuration payload = Configuration.builder().organisation(orgB).definition(defA).value("DARK").build(); // client tries orgB
        var resp = configurationController.createConfiguration(payload);
        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        assertEquals(1L, resp.getBody().getOrganisation().getOrgId());
        assertNotEquals(2L, resp.getBody().getOrganisation().getOrgId());
    }

    // --- Read ---
    @Test
    void read_byId_success() {
        when(configurationRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.of(configA));
        Configuration found = configurationService.getConfigurationById(100L, 1L);
        assertEquals(100L, found.getId());
    }

    @Test
    void read_byId_crossOrg_403() {
        // exists globally but not in org -> 403
        when(configurationRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.empty());
        when(configurationRepository.findById(100L)).thenReturn(Optional.of(Configuration.builder().id(100L).organisation(orgB).definition(defA).build()));
        assertThrows(AccessDeniedException.class, () -> configurationService.getConfigurationById(100L, 1L));
    }

    @Test
    void read_byId_notFound_404() {
        when(configurationRepository.findByIdAndOrganisationOrgId(999L, 1L)).thenReturn(Optional.empty());
        when(configurationRepository.findById(999L)).thenReturn(Optional.empty());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> configurationService.getConfigurationById(999L, 1L));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void read_byKey_success() {
        when(configurationRepository.findByDefinitionKeyAndOrganisationOrgId("THEME", 1L)).thenReturn(Optional.of(configA));
        Configuration found = configurationService.getConfigurationByKey("THEME", 1L);
        assertEquals("THEME", found.getDefinition().getKey());
    }

    @Test
    void read_byKey_notFound_404() {
        when(configurationRepository.findByDefinitionKeyAndOrganisationOrgId("MISSING", 1L)).thenReturn(Optional.empty());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> configurationService.getConfigurationByKey("MISSING", 1L));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void list_onlyCurrentOrg() {
        Configuration configB = Configuration.builder().id(101L).organisation(orgB).definition(defA).value("x").build();
        when(configurationRepository.findByOrganisationOrgId(1L)).thenReturn(List.of(configA));
        // controller list
        when(authorizationService.getCurrentOrgId()).thenReturn(1L);
        var resp = configurationController.getConfigurationsByOrganisation();
        assertEquals(1, resp.getBody().size());
        assertEquals(1L, resp.getBody().get(0).getOrganisation().getOrgId());
        // ensure not leaking orgB
        assertNotEquals(2L, resp.getBody().get(0).getOrganisation().getOrgId());
    }

    // --- Update ---
    @Test
    void update_success() {
        when(configurationRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.of(configA));
        when(configurationRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        Configuration payload = Configuration.builder().value("DARK").definition(defA).build();
        Configuration updated = configurationService.updateConfiguration(100L, payload, 1L);
        assertEquals("DARK", updated.getValue());
    }

    @Test
    void update_invalidValue_400() {
        Configuration strictConfig = Configuration.builder().id(100L).organisation(orgA).definition(defStrict).value("AUTO").build();
        when(configurationRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.of(strictConfig));
        Configuration payload = Configuration.builder().value("BAD").definition(defStrict).build();
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> configurationService.updateConfiguration(100L, payload, 1L));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void update_cannotChangeOrganization() {
        when(configurationRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.of(configA));
        Configuration payload2 = Configuration.builder().organisation(orgB).value("DARK").definition(defA).build();
        AccessDeniedException ade = assertThrows(AccessDeniedException.class, () -> configurationService.updateConfiguration(100L, payload2, 1L));
        assertTrue(ade.getMessage().contains("organisation"));
    }

    @Test
    void update_cannotChangeDefinition_400() {
        when(configurationRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.of(configA));
        ConfigurationDefinition otherDef = ConfigurationDefinition.builder().id(99L).key("OTHER").build();
        Configuration payload = Configuration.builder().definition(otherDef).value("x").build();
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> configurationService.updateConfiguration(100L, payload, 1L));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void update_crossOrg_403() {
        when(configurationRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.empty());
        when(configurationRepository.findById(100L)).thenReturn(Optional.of(Configuration.builder().id(100L).organisation(orgB).definition(defA).build()));
        Configuration payload = Configuration.builder().value("DARK").definition(defA).build();
        assertThrows(AccessDeniedException.class, () -> configurationService.updateConfiguration(100L, payload, 1L));
    }

    @Test
    void update_notFound_404() {
        when(configurationRepository.findByIdAndOrganisationOrgId(999L, 1L)).thenReturn(Optional.empty());
        when(configurationRepository.findById(999L)).thenReturn(Optional.empty());
        Configuration payload = Configuration.builder().value("x").definition(defA).build();
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> configurationService.updateConfiguration(999L, payload, 1L));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    // --- Delete ---
    @Test
    void delete_success() {
        when(configurationRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.of(configA));
        assertDoesNotThrow(() -> configurationService.deleteConfiguration(100L, 1L));
        verify(configurationRepository).delete(configA);
    }

    @Test
    void delete_crossOrg_403() {
        when(configurationRepository.findByIdAndOrganisationOrgId(100L, 1L)).thenReturn(Optional.empty());
        when(configurationRepository.findById(100L)).thenReturn(Optional.of(Configuration.builder().id(100L).organisation(orgB).definition(defA).build()));
        assertThrows(AccessDeniedException.class, () -> configurationService.deleteConfiguration(100L, 1L));
    }

    @Test
    void delete_notFound_404() {
        when(configurationRepository.findByIdAndOrganisationOrgId(999L, 1L)).thenReturn(Optional.empty());
        when(configurationRepository.findById(999L)).thenReturn(Optional.empty());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> configurationService.deleteConfiguration(999L, 1L));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    // --- Permission checks ---
    @Test
    void missingPermission_403() {
        doThrow(new AccessDeniedException("Missing required permission: CONFIG_CREATE or CREATE_CONFIGURATION"))
                .when(authorizationService).requireConfigCreate();
        when(authorizationService.getCurrentOrgId()).thenReturn(1L);
        Configuration payload = Configuration.builder().definition(defA).value("x").build();
        assertThrows(AccessDeniedException.class, () -> configurationController.createConfiguration(payload));
        // similarly for read
        doThrow(new AccessDeniedException("Missing")).when(authorizationService).requireConfigRead();
        assertThrows(AccessDeniedException.class, () -> configurationController.getConfigurationById(100L));
        doThrow(new AccessDeniedException("Missing")).when(authorizationService).requireConfigUpdate();
        assertThrows(AccessDeniedException.class, () -> configurationController.updateConfiguration(100L, payload));
        doThrow(new AccessDeniedException("Missing")).when(authorizationService).requireConfigDelete();
        assertThrows(AccessDeniedException.class, () -> configurationController.deleteConfiguration(100L));
    }

    // --- Platform Admin isolation ---
    @Test
    void platformAdmin_403() {
        when(authorizationService.getCurrentOrgId()).thenThrow(new AccessDeniedException("Platform Admin has no organisation scope"));
        when(authorizationService.isPlatformAdmin()).thenReturn(true);
        // All tenant config APIs should be blocked
        Configuration payload = Configuration.builder().definition(defA).value("x").build();
        assertThrows(AccessDeniedException.class, () -> configurationController.createConfiguration(payload));
        assertThrows(AccessDeniedException.class, () -> configurationController.getConfigurationsByOrganisation());
        assertThrows(AccessDeniedException.class, () -> configurationController.getConfigurationById(100L));
        assertThrows(AccessDeniedException.class, () -> configurationController.deleteConfiguration(100L));
    }

    // --- ConfigurationDefinition behavior ---
    @Test
    void definition_allowedValues_enforced() {
        // defStrict allows only AUTO, MANUAL
        when(definitionRepository.findByKey("MODE")).thenReturn(Optional.of(defStrict));
        when(configurationRepository.existsByDefinitionKeyAndOrganisationOrgId("MODE", 1L)).thenReturn(false);
        when(organisationRepository.getReferenceById(1L)).thenReturn(orgA);
        when(configurationRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        // valid
        Configuration ok = configurationService.createConfiguration(1L, "MODE", "MANUAL");
        assertEquals("MANUAL", ok.getValue());
        // invalid
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> configurationService.createConfiguration(1L, "MODE", "WRONG"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        // blank should use default
        when(definitionRepository.findByKey("MODE")).thenReturn(Optional.of(defStrict));
        when(configurationRepository.existsByDefinitionKeyAndOrganisationOrgId("MODE", 1L)).thenReturn(false);
        Configuration withNull = configurationService.createConfiguration(1L, "MODE", null);
        assertEquals("AUTO", withNull.getValue());
    }

    @Test
    void definition_defaultValue_used() {
        when(definitionRepository.findByKey("THEME")).thenReturn(Optional.of(defA)); // default LIGHT
        when(configurationRepository.existsByDefinitionKeyAndOrganisationOrgId("THEME", 1L)).thenReturn(false);
        when(organisationRepository.getReferenceById(1L)).thenReturn(orgA);
        when(configurationRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        Configuration created = configurationService.createConfiguration(1L, "THEME", null);
        assertEquals("LIGHT", created.getValue());
    }
}

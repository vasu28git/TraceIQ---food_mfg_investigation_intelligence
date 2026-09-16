package com.taceiq.service;

import com.taceiq.entity.Configuration;
import com.taceiq.entity.ConfigurationDefinition;
import com.taceiq.repository.ConfigurationDefinitionRepository;
import com.taceiq.repository.ConfigurationRepository;
import com.taceiq.repository.OrganisationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ConfigurationService {

    private final ConfigurationRepository configurationRepository;
    private final ConfigurationDefinitionRepository definitionRepository;
    private final OrganisationRepository organisationRepository;

    // Org Admin can only select existing definitions and valid allowed values
    public Configuration createConfiguration(Long organisationId, String definitionKey, String value) {
        if (definitionKey == null || definitionKey.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Configuration key is required");
        }
        String key = definitionKey.trim();
        ConfigurationDefinition definition = definitionRepository.findByKey(key)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Undefined configuration key: " + key));

        String normalizedValue = value != null ? value.trim() : null;
        // Treat blank as invalid if allowedValues defined
        if (normalizedValue != null && normalizedValue.isEmpty()) {
            normalizedValue = null;
        }
        validateValueForDefinition(normalizedValue, definition);
        if (configurationRepository.existsByDefinitionKeyAndOrganisationOrgId(key, organisationId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Configuration already exists for key: " + key + " in organisation: " + organisationId);
        }
        Configuration config = Configuration.builder()
                .organisation(organisationRepository.getReferenceById(organisationId))
                .definition(definition)
                .value(normalizedValue != null ? normalizedValue : definition.getDefaultValue())
                .build();
        return configurationRepository.save(config);
    }

    private void validateValueForDefinition(String normalizedValue, ConfigurationDefinition definition) {
        if (normalizedValue == null) return;
        String type = definition.getType() != null ? definition.getType().toUpperCase() : "STRING";
        switch (type) {
            case "INTEGER":
                try {
                    Integer.parseInt(normalizedValue);
                } catch (NumberFormatException e) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid integer value '" + normalizedValue + "' for key '" + definition.getKey() + "'");
                }
                break;
            case "BOOLEAN":
                if (!"true".equalsIgnoreCase(normalizedValue) && !"false".equalsIgnoreCase(normalizedValue)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid boolean value '" + normalizedValue + "' for key '" + definition.getKey() + "'. Allowed: [true, false]");
                }
                break;
            case "ENUM":
                if (!definition.getAllowedValues().isEmpty() && !definition.getAllowedValues().contains(normalizedValue)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid value '" + normalizedValue + "' for key '" + definition.getKey() + "'. Allowed: " + definition.getAllowedValues());
                }
                break;
            default:
                if (!definition.getAllowedValues().isEmpty() && !definition.getAllowedValues().contains(normalizedValue)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid value '" + normalizedValue + "' for key '" + definition.getKey() + "'. Allowed: " + definition.getAllowedValues());
                }
                break;
        }
        // For ENUM with allowedValues empty, still validate if needed? No, any value allowed for STRING/INTEGER with no allowedValues already handled
    }

    // Overload for controller that passes Configuration with definition+value but org derived
    public Configuration createConfiguration(Configuration config) {
        if (config.getDefinition() == null || config.getDefinition().getKey() == null || config.getDefinition().getKey().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Configuration definition key is required");
        }
        String key = config.getDefinition().getKey().trim();
        Long orgId = config.getOrganisation() != null ? config.getOrganisation().getOrgId() : null;
        if (orgId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Organisation is required");
        }
        // Never trust client value blank handling - delegate to main method which validates
        return createConfiguration(orgId, key, config.getValue());
    }

    public Configuration getConfigurationById(Long id) {
        return configurationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Configuration not found with id: " + id));
    }

    public Configuration getConfigurationById(Long id, Long orgId) {
        return configurationRepository.findByIdAndOrganisationOrgId(id, orgId)
                .orElseGet(() -> {
                    // Distinguish cross-org (403) vs not-found (404) without leaking
                    if (configurationRepository.findById(id).isPresent()) {
                        throw new AccessDeniedException("Configuration does not belong to your organisation");
                    }
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Configuration not found with id: " + id);
                });
    }

    public Configuration getConfigurationByKey(String key, Long organisationId) {
        if (key == null || key.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Configuration key is required");
        }
        String trimmed = key.trim();
        return configurationRepository.findByDefinitionKeyAndOrganisationOrgId(trimmed, organisationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Configuration not found with key: " + trimmed + " and organisationId: " + organisationId));
    }

    public List<Configuration> getConfigurationsByOrganisation(Long organisationId) {
        return configurationRepository.findByOrganisationOrgId(organisationId);
    }

    public List<ConfigurationDefinition> getAllDefinitions() {
        return definitionRepository.findAll();
    }

    @Transactional
    public Configuration updateConfiguration(Long id, String newValue, Long orgId) {
        Configuration existing = getConfigurationById(id, orgId);
        ConfigurationDefinition definition = existing.getDefinition();
        String normalized = newValue != null ? newValue.trim() : null;
        if (normalized != null && normalized.isEmpty()) normalized = null;
        validateValueForDefinition(normalized, definition);
        // Allow clearing to null? If allowedValues empty, any value allowed, including null
        existing.setValue(normalized);
        return configurationRepository.save(existing);
    }

    public Configuration updateConfiguration(Long id, String newValue) {
        Configuration existing = getConfigurationById(id);
        ConfigurationDefinition definition = existing.getDefinition();
        String normalized = newValue != null ? newValue.trim() : null;
        if (normalized != null && normalized.isEmpty()) normalized = null;
        validateValueForDefinition(normalized, definition);
        existing.setValue(normalized);
        return configurationRepository.save(existing);
    }

    @Transactional
    public Configuration updateConfiguration(Long id, Configuration updated, Long orgId) {
        // NEVER change organisation or definition - only value is mutable (key is via definition)
        Configuration existing = getConfigurationById(id, orgId);
        // If client tries to change organisation -> ignore (already org-scoped fetch ensures same org, but also reject if payload has different org)
        if (updated.getOrganisation() != null && updated.getOrganisation().getOrgId() != null && !updated.getOrganisation().getOrgId().equals(orgId)) {
            throw new AccessDeniedException("Cannot change configuration organisation");
        }
        // if updated contains definition key change attempt, reject
        if (updated.getDefinition() != null && updated.getDefinition().getKey() != null) {
            String newKey = updated.getDefinition().getKey().trim();
            if (!newKey.equals(existing.getDefinition().getKey())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot change configuration definition/key");
            }
        }
        String newValue = updated.getValue();
        return updateConfiguration(id, newValue, orgId);
    }

    public Configuration updateConfiguration(Long id, Configuration updated) {
        // Legacy non-org-scoped path - preserve for internal use, but still prevent org/definition change
        Configuration existing = getConfigurationById(id);
        if (updated.getDefinition() != null && updated.getDefinition().getKey() != null) {
            String newKey = updated.getDefinition().getKey().trim();
            if (!newKey.equals(existing.getDefinition().getKey())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot change configuration definition/key");
            }
        }
        String newValue = updated.getValue();
        String normalized = newValue != null ? newValue.trim() : null;
        if (normalized != null && normalized.isEmpty()) normalized = null;
        ConfigurationDefinition definition = existing.getDefinition();
        if (normalized != null && !definition.getAllowedValues().isEmpty() && !definition.getAllowedValues().contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid value '" + normalized + "' for key '" + definition.getKey() + "'. Allowed: " + definition.getAllowedValues());
        }
        existing.setValue(normalized);
        return configurationRepository.save(existing);
    }

    @Transactional
    public void deleteConfiguration(Long id, Long orgId) {
        Configuration existing = getConfigurationById(id, orgId);
        configurationRepository.delete(existing);
    }

    public void deleteConfiguration(Long id) {
        Configuration existing = getConfigurationById(id);
        configurationRepository.delete(existing);
    }
}

package com.taceiq.service;

import com.taceiq.entity.Organisation;
import com.taceiq.repository.OrganisationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrganisationService {

    private final OrganisationRepository organisationRepository;

    // create org
    public Organisation createOrg(Organisation organisation) {
        return organisationRepository.save(organisation);
    }

    // get org by id
    public Organisation getOrg(Long orgId) {
        return organisationRepository.findById(orgId)
                .orElseThrow(() -> new RuntimeException("Organisation not found with id: " + orgId));
    }

    // get all org
    public List<Organisation> getAllOrg() {
        return organisationRepository.findAll();
    }

    // update org
    public Organisation updateOrg(Long orgId, Organisation updated) {
        Organisation existing = getOrg(orgId);
        existing.setName(updated.getName());
        existing.setDomain(updated.getDomain());
        existing.setStatus(updated.getStatus());
        existing.setDescription(updated.getDescription());
        return organisationRepository.save(existing);
    }

    // delete org
    public void deleteOrg(Long orgId) {
        Organisation existing = getOrg(orgId);
        organisationRepository.delete(existing);
    }
}

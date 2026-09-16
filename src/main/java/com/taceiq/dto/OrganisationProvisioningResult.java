package com.taceiq.dto;

import com.taceiq.entity.Organisation;
import com.taceiq.entity.Role;
import com.taceiq.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrganisationProvisioningResult {

    private Organisation organisation;
    private Role adminRole;
    private User ogUser;
}

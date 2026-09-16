package com.taceiq.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserCreateRequest {
    @NotBlank(message = "Username is required")
    private String username;

    @NotBlank(message = "Password is required")
    private String password;

    private String status; // ACTIVE/INACTIVE, defaults to ACTIVE

    private Long roleId; // must belong to same org

    // Explicitly do not accept organisationId - always forced from auth
}

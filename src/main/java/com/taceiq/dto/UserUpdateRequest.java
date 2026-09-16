package com.taceiq.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserUpdateRequest {
    private String username; // if provided, must be unique within org
    private String status; // ACTIVE/INACTIVE
    private Long roleId; // if provided, must belong to same org and not escalate
    // organisation change is never allowed - ignored if present
}

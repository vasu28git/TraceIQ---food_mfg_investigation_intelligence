package com.taceiq.security.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    // Platform Admin uses email, User uses username - either is accepted
    private String email;
    private String username;

    private String password;
}

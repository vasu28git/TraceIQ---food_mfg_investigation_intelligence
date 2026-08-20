package com.investigation.platform.credential.service;

import java.util.UUID;

/**
 * FUTURE DATABASE MODULE: Dedicated API Credential & Vault Encryption Service placeholder.
 */
public interface ApiCredentialService {

    String generateClientSecret(UUID orgId, String keyName);

    boolean validateClientSecret(UUID orgId, String keyName, String secret);

    void revokeClientSecret(UUID orgId, String keyName);
}

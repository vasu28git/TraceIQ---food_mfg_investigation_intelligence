package com.investigation.platform.invitation.service;

import java.util.UUID;

/**
 * FUTURE MODULE: Invitation & Onboarding Email Service placeholder.
 */
public interface InvitationService {

    void sendUserInvitation(UUID orgId, String email, UUID roleId);

    void acceptInvitation(String token, String newPassword);
}

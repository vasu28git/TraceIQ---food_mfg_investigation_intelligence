package com.taceiq.service;

import com.taceiq.dto.UserCreateRequest;
import com.taceiq.dto.UserUpdateRequest;
import com.taceiq.entity.Role;
import com.taceiq.entity.User;
import com.taceiq.repository.OrganisationRepository;
import com.taceiq.repository.RoleRepository;
import com.taceiq.repository.UserRepository;
import com.taceiq.security.AuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final OrganisationRepository organisationRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthorizationService authorizationService;

    public User createUser(User user) {
        // Used by provisioning (system, no auth context) - bypass RBAC
        if (user.getUsername() == null || user.getUsername().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username is required");
        }
        if (userRepository.existsByUsername(user.getUsername())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists: " + user.getUsername());
        }
        if (user.getPassword() == null || user.getPassword().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is required");
        }
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        if (user.getStatus() == null) user.setStatus("ACTIVE");
        if (user.getMustChangePassword() == null) user.setMustChangePassword(true);
        return userRepository.save(user);
    }

    /**
     * Organization-scoped creation: always assigns to currentOrgId, ignores client-provided org.
     * Also validates role belongs to same org if provided and enforces privilege escalation guard.
     */
    public User createUser(User user, Long currentOrgId) {
        if (user.getUsername() == null || user.getUsername().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username is required");
        }
        String username = user.getUsername().trim();
        if (userRepository.existsByUsername(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists: " + username);
        }
        if (user.getPassword() == null || user.getPassword().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is required");
        }
        if (user.getPassword().length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 8 characters");
        }
        // Force organisation to current user's org
        user.setUsername(username);
        user.setOrganisation(organisationRepository.getReferenceById(currentOrgId));

        // If role is provided, verify it belongs to the same org
        if (user.getRole() != null && user.getRole().getId() != null) {
            Long roleId = user.getRole().getId();
            var role = roleRepository.findByIdAndOrganisationOrgId(roleId, currentOrgId)
                    .orElseThrow(() -> new AccessDeniedException("Role does not belong to your organisation"));
            // Privilege escalation: cannot assign role with permissions you don't have
            authorizationService.requireCanAssignRole(role);
            user.setRole(role);
        } else if (user.getRole() != null && user.getRole().getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role must be provided via id belonging to your organisation");
        }

        user.setPassword(passwordEncoder.encode(user.getPassword()));
        if (user.getStatus() == null || user.getStatus().isBlank()) user.setStatus("ACTIVE");
        else user.setStatus(user.getStatus().toUpperCase());
        if (user.getMustChangePassword() == null) user.setMustChangePassword(true);
        // normalize status
        if (!List.of("ACTIVE", "INACTIVE", "DISABLED", "SUSPENDED").contains(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status: " + user.getStatus());
        }
        return userRepository.save(user);
    }

    public User createUserFromRequest(UserCreateRequest req, Long currentOrgId) {
        if (req.getUsername() == null || req.getUsername().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username is required");
        }
        String username = req.getUsername().trim();
        if (userRepository.existsByUsername(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists: " + username);
        }
        if (req.getPassword() == null || req.getPassword().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is required");
        }
        if (req.getPassword().length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 8 characters");
        }
        User user = User.builder()
                .username(username)
                .password(req.getPassword())
                .status(req.getStatus() != null ? req.getStatus().toUpperCase() : "ACTIVE")
                .mustChangePassword(true)
                .organisation(organisationRepository.getReferenceById(currentOrgId))
                .build();
        if (!List.of("ACTIVE", "INACTIVE", "DISABLED", "SUSPENDED").contains(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status: " + user.getStatus());
        }
        if (req.getRoleId() != null) {
            Role role = roleRepository.findByIdAndOrganisationOrgId(req.getRoleId(), currentOrgId)
                    .orElseThrow(() -> new AccessDeniedException("Role does not belong to your organisation"));
            authorizationService.requireCanAssignRole(role);
            user.setRole(role);
        }
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return userRepository.save(user);
    }

    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with id: " + id));
    }

    public User getUserById(Long id, Long currentOrgId) {
        return userRepository.findByIdAndOrganisationOrgId(id, currentOrgId)
                .orElseThrow(() -> new AccessDeniedException("User not found or not in your organisation: " + id));
    }

    public User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with username: " + username));
    }

    public User getUserByUsername(String username, Long currentOrgId) {
        return userRepository.findByUsernameAndOrganisationOrgId(username, currentOrgId)
                .orElseThrow(() -> new AccessDeniedException("User not found in your organisation: " + username));
    }

    public List<User> getUsersByOrg(Long orgId) {
        return userRepository.findByOrganisationOrgId(orgId);
    }

    public List<User> getUsersByOrg(Long requestedOrgId, Long currentOrgId) {
        if (!requestedOrgId.equals(currentOrgId)) {
            throw new AccessDeniedException("Cannot access users of another organisation");
        }
        return userRepository.findByOrganisationOrgId(currentOrgId);
    }

    public List<User> listUsers(Long currentOrgId) {
        return userRepository.findByOrganisationOrgId(currentOrgId);
    }

    @Transactional
    public User updateUser(Long id, Long currentOrgId, UserUpdateRequest req) {
        User existing = getUserById(id, currentOrgId);

        // Never allow changing organisation - ignore if present in request
        // Validate username change
        if (req.getUsername() != null && !req.getUsername().isBlank()) {
            String newUsername = req.getUsername().trim();
            if (!newUsername.equals(existing.getUsername())) {
                if (userRepository.existsByUsername(newUsername)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists: " + newUsername);
                }
                // also check within org for safety (global unique already covers)
                Optional<User> dup = userRepository.findByUsernameAndOrganisationOrgId(newUsername, currentOrgId);
                if (dup.isPresent() && !dup.get().getId().equals(id)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists in organization: " + newUsername);
                }
                existing.setUsername(newUsername);
            }
        }

        // Validate status change
        if (req.getStatus() != null && !req.getStatus().isBlank()) {
            String newStatus = req.getStatus().trim().toUpperCase();
            if (!List.of("ACTIVE", "INACTIVE", "DISABLED", "SUSPENDED").contains(newStatus)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status: " + newStatus);
            }
            // Prevent self-deactivation/last-admin checks
            if (!newStatus.equals(existing.getStatus())) {
                if ("INACTIVE".equals(newStatus) || "DISABLED".equals(newStatus) || "SUSPENDED".equals(newStatus)) {
                    preventLastAdminDeactivation(existing, currentOrgId);
                    preventSelfDeactivation(existing);
                }
                existing.setStatus(newStatus);
            }
        }

        // Validate role change
        if (req.getRoleId() != null) {
            Role targetRole = roleRepository.findByIdAndOrganisationOrgId(req.getRoleId(), currentOrgId)
                    .orElseThrow(() -> new AccessDeniedException("Role does not belong to your organisation"));
            // Privilege escalation: cannot assign role with perms you don't have
            authorizationService.requireCanAssignRole(targetRole);
            // If moving away from ADMIN, ensure not last admin
            if (isAdminRole(existing.getRole()) && !isAdminRole(targetRole)) {
                preventLastAdminChange(existing, currentOrgId);
            }
            existing.setRole(targetRole);
        }

        return userRepository.save(existing);
    }

    @Transactional
    public User updateUserStatus(Long id, Long currentOrgId, String status) {
        User existing = getUserById(id, currentOrgId);
        if (status == null || status.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status is required");
        }
        String newStatus = status.trim().toUpperCase();
        if (!List.of("ACTIVE", "INACTIVE", "DISABLED", "SUSPENDED").contains(newStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status: " + newStatus);
        }
        if (!newStatus.equals(existing.getStatus())) {
            if ("INACTIVE".equals(newStatus) || "DISABLED".equals(newStatus) || "SUSPENDED".equals(newStatus)) {
                preventLastAdminDeactivation(existing, currentOrgId);
                preventSelfDeactivation(existing);
            }
            existing.setStatus(newStatus);
            return userRepository.save(existing);
        }
        return existing;
    }

    @Transactional
    public void deleteUser(Long id, Long currentOrgId) {
        User existing = getUserById(id, currentOrgId);
        preventSelfDelete(existing);
        preventLastAdminDelete(existing, currentOrgId);
        userRepository.delete(existing);
    }

    private boolean isAdminRole(Role role) {
        return role != null && "ADMIN".equalsIgnoreCase(role.getName());
    }

    private void preventSelfDelete(User target) {
        try {
            User current = authorizationService.getCurrentUser();
            if (current.getId() != null && current.getId().equals(target.getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot delete your own account");
            }
        } catch (AccessDeniedException ignored) {
            // if no auth context (system), allow
        }
    }

    private void preventSelfDeactivation(User target) {
        try {
            User current = authorizationService.getCurrentUser();
            if (current.getId() != null && current.getId().equals(target.getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot deactivate your own account");
            }
        } catch (AccessDeniedException ignored) {}
    }

    private void preventLastAdminDelete(User target, Long orgId) {
        if (!isAdminRole(target.getRole())) return;
        long adminCount = userRepository.findByOrganisationOrgId(orgId).stream()
                .filter(u -> isAdminRole(u.getRole()) && "ACTIVE".equalsIgnoreCase(u.getStatus()))
                .count();
        if (adminCount <= 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot delete last active ADMIN in organization");
        }
    }

    private void preventLastAdminDeactivation(User target, Long orgId) {
        if (!isAdminRole(target.getRole())) return;
        if (!"ACTIVE".equalsIgnoreCase(target.getStatus())) return; // already inactive
        long adminCount = userRepository.findByOrganisationOrgId(orgId).stream()
                .filter(u -> isAdminRole(u.getRole()) && "ACTIVE".equalsIgnoreCase(u.getStatus()))
                .count();
        if (adminCount <= 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot deactivate last active ADMIN in organization");
        }
    }

    private void preventLastAdminChange(User target, Long orgId) {
        if (!isAdminRole(target.getRole())) return;
        long adminCount = userRepository.findByOrganisationOrgId(orgId).stream()
                .filter(u -> isAdminRole(u.getRole()) && "ACTIVE".equalsIgnoreCase(u.getStatus()))
                .count();
        if (adminCount <= 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot change role of last active ADMIN");
        }
    }
}

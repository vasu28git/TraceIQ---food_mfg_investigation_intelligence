package com.investigation.platform.security;

import com.investigation.platform.user.entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Getter
public class SecurityUser implements UserDetails {

    private final UUID userId;
    private final UUID orgId;
    private final String email;
    private final String password;
    private final boolean enabled;
    private final Collection<? extends GrantedAuthority> authorities;

    public SecurityUser(User user) {
        this.userId = user.getUserId();
        this.orgId = user.getOrgId();
        this.email = user.getEmail();
        this.password = user.getPasswordHash();
        this.enabled = user.getStatus() == com.investigation.platform.user.enums.UserStatus.ACTIVE;

        Set<GrantedAuthority> auths = new HashSet<>();
        if (user.getRole() != null) {
            auths.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().getName()));
            if (user.getRole().getPermissions() != null) {
                user.getRole().getPermissions().forEach(perm ->
                        auths.add(new SimpleGrantedAuthority(perm.getName()))
                );
            }
        }
        this.authorities = auths;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return enabled;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}

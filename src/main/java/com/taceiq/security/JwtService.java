package com.taceiq.security;

import com.taceiq.entity.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMs;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.expirationMs = expirationMs;
    }

    // Platform Admin
    public String generateToken(String email) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
                .subject(email)
                .claim("authorities", List.of("PLATFORM_ADMIN"))
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    // Organisation User - carries full context: userId, username, organisationId, role, permissions, mustChangePassword
    public String generateToken(User user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        Long orgId = user.getOrganisation() != null ? user.getOrganisation().getOrgId() : null;
        String roleName = user.getRole() != null ? user.getRole().getName() : null;
        Long roleId = user.getRole() != null ? user.getRole().getId() : null;

        List<String> permissions = List.of();
        if (user.getRole() != null && user.getRole().getPermissions() != null) {
            permissions = user.getRole().getPermissions().stream()
                    .map(p -> p.getName())
                    .collect(Collectors.toList());
        }

        List<String> authorities = new java.util.ArrayList<>(permissions);
        if (roleName != null) {
            authorities.add("ROLE_" + roleName);
        }
        if (authorities.isEmpty() && roleName != null) {
            authorities = List.of("ROLE_" + roleName);
        }

        var builder = Jwts.builder()
                .subject(user.getUsername())
                .claim("username", user.getUsername())
                .claim("userId", user.getId())
                .claim("organisationId", orgId)
                .claim("orgId", orgId) // keep for backward compat
                .claim("role", roleName)
                .claim("roleId", roleId)
                .claim("permissions", permissions)
                .claim("authorities", authorities)
                .claim("mustChangePassword", user.getMustChangePassword())
                .issuedAt(now)
                .expiration(expiry);

        return builder.signWith(key).compact();
    }

    public String extractEmail(String token) {
        return parseClaims(token).getPayload().getSubject();
    }

    public String extractUsername(String token) {
        return extractEmail(token);
    }

    @SuppressWarnings("unchecked")
    public List<String> extractAuthorities(String token) {
        Claims claims = parseClaims(token).getPayload();
        Object auth = claims.get("authorities");
        if (auth instanceof List) {
            return (List<String>) auth;
        }
        return List.of();
    }

    public boolean isTokenValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    private Jws<Claims> parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token);
    }
}

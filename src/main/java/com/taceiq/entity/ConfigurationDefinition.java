package com.taceiq.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "configuration_definitions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfigurationDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "config_key", nullable = false, unique = true)
    private String key;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "config_type", nullable = false)
    @Builder.Default
    private String type = "STRING";

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "config_definition_allowed_values", joinColumns = @JoinColumn(name = "definition_id"))
    @Column(name = "allowed_value", nullable = false)
    @Builder.Default
    private Set<String> allowedValues = new HashSet<>();

    @Column(name = "default_value")
    private String defaultValue;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}

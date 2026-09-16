package com.taceiq.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "organisations")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Organisation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "org_id")
    private Long orgId;

    @Column(nullable = false)
    private String name;

    @Column
    private String domain;

    @Column
    private String status;

    @Column(columnDefinition = "TEXT")
    private String description;

    // Inverse side: Organisation 1:N User (owning side is User.organisation)
    @OneToMany(mappedBy = "organisation", cascade = CascadeType.ALL, orphanRemoval = false)
    @JsonIgnore
    @Builder.Default
    private List<User> users = new ArrayList<>();

    // Inverse side: Organisation 1:N Role (owning side is Role.organisation)
    @OneToMany(mappedBy = "organisation", cascade = CascadeType.ALL, orphanRemoval = false)
    @JsonIgnore
    @Builder.Default
    private List<Role> roles = new ArrayList<>();

    // Inverse side: Organisation 1:N Configuration (owning side is Configuration.organisation)
    @OneToMany(mappedBy = "organisation", cascade = CascadeType.ALL, orphanRemoval = false)
    @JsonIgnore
    @Builder.Default
    private List<Configuration> configurations = new ArrayList<>();

    // Inverse side: Organisation 1:N Integration (owning side is Integration.organisation)
    @OneToMany(mappedBy = "organisation", cascade = CascadeType.ALL, orphanRemoval = false)
    @JsonIgnore
    @Builder.Default
    private List<Integration> integrations = new ArrayList<>();

    // Inverse side: Organisation 1:N File (owning side is File.organisation) - supports MANUAL_UPLOAD and INTEGRATION files
    @OneToMany(mappedBy = "organisation", cascade = CascadeType.ALL, orphanRemoval = false)
    @JsonIgnore
    @Builder.Default
    private List<File> files = new ArrayList<>();
}

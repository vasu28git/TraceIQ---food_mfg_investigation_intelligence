package com.investigation.platform.organization.entity;

import com.investigation.platform.common.entity.BaseEntity;
import com.investigation.platform.organization.enums.OrganizationStatus;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "organizations")
public class Organization extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "org_id", nullable = false, updatable = false)
    private UUID orgId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "domain", unique = true)
    private String domain;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private OrganizationStatus status;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
}

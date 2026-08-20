package com.investigation.platform.integration.entity;

import com.investigation.platform.common.entity.BaseEntity;
import com.investigation.platform.integration.enums.IntegrationStatus;
import com.investigation.platform.organization.entity.Organization;
import com.investigation.platform.tenant.TenantAware;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
    name = "integrations",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_integrations_org_name", columnNames = {"org_id", "name"})
    }
)
public class Integration extends BaseEntity implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "int_id", nullable = false, updatable = false)
    private UUID intId;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "org_id", insertable = false, updatable = false)
    private Organization organization;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "provider", nullable = false, length = 100)
    private String provider;

    @Column(name = "api_key", length = 500)
    private String apiKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private IntegrationStatus status;

    @Column(name = "last_sync_at")
    private Instant lastSyncAt;
}

package com.investigation.platform.configuration.entity;

import com.investigation.platform.common.entity.BaseEntity;
import com.investigation.platform.organization.entity.Organization;
import com.investigation.platform.tenant.TenantAware;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
    name = "configurations",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_configurations_org_key", columnNames = {"org_id", "config_key"})
    }
)
public class Configuration extends BaseEntity implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "config_id", nullable = false, updatable = false)
    private UUID configId;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "org_id", insertable = false, updatable = false)
    private Organization organization;

    @Column(name = "config_key", nullable = false, length = 100)
    private String configKey;

    @Column(name = "config_value", nullable = false, columnDefinition = "TEXT")
    private String configValue;
}

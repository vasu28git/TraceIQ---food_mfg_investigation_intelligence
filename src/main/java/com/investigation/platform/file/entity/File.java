package com.investigation.platform.file.entity;

import com.investigation.platform.common.entity.BaseEntity;
import com.investigation.platform.integration.entity.Integration;
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
@Table(name = "files")
public class File extends BaseEntity implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "file_id", nullable = false, updatable = false)
    private UUID fileId;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "org_id", insertable = false, updatable = false)
    private Organization organization;

    @Column(name = "integration_id")
    private UUID integrationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "integration_id", insertable = false, updatable = false)
    private Integration integration;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_type", nullable = false, length = 100)
    private String fileType;

    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    @Column(name = "file_size")
    private Long fileSize;
}

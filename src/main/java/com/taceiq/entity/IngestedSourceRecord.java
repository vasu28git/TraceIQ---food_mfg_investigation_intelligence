package com.taceiq.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

@Entity
@Table(name = "ingested_source_record",
       uniqueConstraints = @UniqueConstraint(name = "uq_source_org_type_record",
               columnNames = {"org_id","source_type","source_record_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IngestedSourceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "org_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
    private Organisation organisation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
    private File sourceFile;

    @Column(name = "source_type", nullable = false)
    private String sourceType;

    @Column(name = "source_record_id", nullable = false, columnDefinition = "TEXT")
    private String sourceRecordId;

    @Column(name = "batch_reference", columnDefinition = "TEXT")
    private String batchReference;

    @Column(name = "machine_reference", columnDefinition = "TEXT")
    private String machineReference;

    @Column(name = "supplier_reference", columnDefinition = "TEXT")
    private String supplierReference;

    @Column(name = "product_reference", columnDefinition = "TEXT")
    private String productReference;

    @Column(name = "order_reference", columnDefinition = "TEXT")
    private String orderReference;

    @Column(name = "external_reference", columnDefinition = "TEXT")
    private String externalReference;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private String payload = "{}";

    @Column(name = "ingested_at", nullable = false)
    private Instant ingestedAt;

    @PrePersist
    void onCreate() {
        if (this.ingestedAt == null) this.ingestedAt = Instant.now();
        if (this.payload == null) this.payload = "{}";
    }
}

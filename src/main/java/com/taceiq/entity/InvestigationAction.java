package com.taceiq.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "investigation_action")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InvestigationAction {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "org_id", nullable = false) private Organisation organisation;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "investigation_id", nullable = false) private Investigation investigation;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "finding_id") private InvestigationFinding finding;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "conclusion_id") private InvestigationConclusion conclusion;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "owner_user_id") private User owner;
    @Column(nullable = false, columnDefinition = "TEXT") private String title;
    @Column(columnDefinition = "TEXT") private String description;
    @Column(name = "action_type", columnDefinition = "TEXT") private String actionType;
    @Column(nullable = false) @Builder.Default private String priority = "MEDIUM";
    @Column(nullable = false) @Builder.Default private String status = "OPEN";
    @Column(name = "due_date") private LocalDate dueDate;
    @Column(columnDefinition = "TEXT") private String notes;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "created_by_user_id") private User createdBy;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "updated_by_user_id") private User updatedBy;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    @PrePersist void onCreate() { Instant now = Instant.now(); createdAt = now; updatedAt = now; if (priority == null) priority = "MEDIUM"; if (status == null) status = "OPEN"; }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }
}

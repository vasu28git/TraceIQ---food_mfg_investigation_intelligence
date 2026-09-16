package com.taceiq.dto;

import lombok.*;

import java.time.Instant;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvestigationNoteResponse {
    private Long id;
    private String content;
    private Long authorUserId;
    private Instant createdAt;
    private Instant updatedAt;

    public static InvestigationNoteResponse fromEntity(com.taceiq.entity.InvestigationNote n) {
        return InvestigationNoteResponse.builder()
                .id(n.getId())
                .content(n.getContent())
                .authorUserId(n.getAuthor() != null ? n.getAuthor().getId() : null)
                .createdAt(n.getCreatedAt())
                .updatedAt(n.getUpdatedAt())
                .build();
    }
}

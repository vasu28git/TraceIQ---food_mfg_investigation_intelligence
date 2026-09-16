package com.taceiq.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvestigationTimelineResponse {
    private Long investigationId;
    private String investigationKey;
    private List<InvestigationTimelineEventResponse> events;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
}

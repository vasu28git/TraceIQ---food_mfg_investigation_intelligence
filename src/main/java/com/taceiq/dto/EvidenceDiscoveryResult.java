package com.taceiq.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record EvidenceDiscoveryResult(
        String evidenceStableId,
        String title,
        String sourceType,
        int distance,
        List<String> nodePath,
        List<String> labelPath,
        List<String> relationshipPath,
        String semanticRoute,
        String intermediateEntityId,
        String intermediateEntityType
) {}

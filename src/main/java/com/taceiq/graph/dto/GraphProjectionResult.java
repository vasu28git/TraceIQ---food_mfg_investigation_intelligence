package com.taceiq.graph.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphProjectionResult {
    private Long organisationId;
    private int evidenceProjected;
    private int casesProjected;
    private int actorsProjected;
    private int incidentsProjected;
    private int relationshipsProjected;
    private int skipped;
}

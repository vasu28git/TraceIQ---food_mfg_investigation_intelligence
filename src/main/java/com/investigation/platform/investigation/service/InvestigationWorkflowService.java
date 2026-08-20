package com.investigation.platform.investigation.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * FUTURE MODULE: Investigation Workflow Service placeholder.
 * Represents core case lifecycle steps:
 * 1. Create Case
 * 2. Investigation Plan
 * 3. Collect Evidence
 * 4. Build Graph
 * 5. Timeline
 * 6. AI Investigation
 * 7. Findings
 * 8. Decisions
 * 9. Actions
 * 10. Notes
 * 11. Reports
 * 12. Review & Approve
 * 13. Close Case
 */
public interface InvestigationWorkflowService {

    UUID createCase(String title, String description);

    void updatePlan(UUID caseId, Map<String, Object> planDetails);

    void attachEvidence(UUID caseId, List<UUID> fileIds);

    Map<String, Object> buildKnowledgeGraph(UUID caseId);

    void recordDecision(UUID caseId, String decision, String rationale);

    void closeCase(UUID caseId, String summary);
}

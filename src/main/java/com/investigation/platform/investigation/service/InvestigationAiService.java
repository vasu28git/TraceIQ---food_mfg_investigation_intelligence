package com.investigation.platform.investigation.service;

import java.util.Map;
import java.util.UUID;

/**
 * FUTURE MODULE: Investigation AI Service placeholder.
 * Will integrate OpenAI, LangChain, embeddings, or local LLM pipelines in subsequent phases.
 */
public interface InvestigationAiService {

    Map<String, Object> analyzeCaseEvidence(UUID caseId);

    Map<String, Object> generateInvestigationTimeline(UUID caseId);

    Map<String, Object> generateFindingsReport(UUID caseId);
}

package com.taceiq.service;

import com.taceiq.dto.InvestigationTimelineEventResponse;
import com.taceiq.dto.InvestigationTimelineResponse;
import com.taceiq.entity.CanonicalEvidence;
import com.taceiq.entity.Investigation;
import com.taceiq.entity.InvestigationEvidence;
import com.taceiq.graph.service.GraphReadinessService;
import com.taceiq.repository.CanonicalEvidenceRepository;
import com.taceiq.repository.ComplaintRepository;
import com.taceiq.repository.InvestigationEvidenceRepository;
import com.taceiq.repository.InvestigationRepository;
import com.taceiq.security.AuthorizationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;

/**
 * Evidence Chronology – deterministic reconstruction of evidence occurrences.
 * <p>
 * Current evidence ingestion does not provide structured domain event/action history.
 * Therefore the timeline currently represents deterministic evidence creation/update chronology.
 * Richer actions such as LOGIN, RECORD_ACCESSED, or RECORD_EXPORTED require the upstream
 * evidence source to provide structured event/action data or history.
 * See docs/sample-source-response.json – only sourceCreatedAt/sourceUpdatedAt are available.
 * <p>
 * NOT an audit log, NOT an application activity timeline. No timeline table – read-time projection.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InvestigationTimelineService {

    private final InvestigationRepository investigationRepository;
    private final ComplaintRepository complaintRepository;
    private final InvestigationEvidenceRepository linkRepository;
    private final CanonicalEvidenceRepository canonicalRepo;
    private final AuthorizationService authorizationService;
    private final GraphReadinessService graphReadinessService;
    private final ObjectMapper objectMapper;

    private static final int DEFAULT_SIZE = 50;
    private static final int MAX_SIZE = 100;

    public InvestigationTimelineResponse getTimeline(Long investigationId, Integer page, Integer size) {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireEvidenceGraphAccess();
        if (!graphReadinessService.isOrgGraphReady(orgId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Graph not ready for organisation " + orgId);
        }

        Investigation inv = investigationRepository.findByIdAndOrganisationOrgId(investigationId, orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Investigation not found with id: " + investigationId));

        int p = page != null ? page : 0;
        int s = size != null ? size : DEFAULT_SIZE;
        if (p < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be >=0");
        if (s <= 0 || s > MAX_SIZE) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "size must be 1-100");

        List<InvestigationTimelineEventResponse> events = new ArrayList<>();

        // Phase 5: Incident-scoped timeline — union of:
        // 1) explicitly linked evidence (investigation_evidence)
        // 2) direct canonical evidence where incident_id = incidentId (Phase 1)
        // Do not leak cross-tenant, do not duplicate, preserve legacy behavior.

        // Collect linked evidence
        List<InvestigationEvidence> links = linkRepository.findByOrganisationOrgIdAndInvestigationId(orgId, investigationId, PageRequest.of(0, 1000, Sort.by("createdAt"))).getContent();
        // Collect direct incident evidence
        List<CanonicalEvidence> direct = Collections.emptyList();
        try {
            direct = canonicalRepo.findByIncidentIdAndOrganisationOrgId(investigationId, orgId).stream()
                    .filter(ce -> !Boolean.TRUE.equals(ce.getIsDeleted()))
                    .toList();
        } catch (Exception ignored) {}

        // Merge by externalId (stableId), deduplicate — preserves both ev_001 and ev_002 even if test reuses id
        Map<String, CanonicalEvidence> canonicalByStableId = new LinkedHashMap<>();
        for (InvestigationEvidence link : links) {
            CanonicalEvidence ce = link.getCanonicalEvidence();
            if (ce == null) continue;
            if (Boolean.TRUE.equals(ce.getIsDeleted())) continue;
            if (ce.getOrganisation() != null && !ce.getOrganisation().getOrgId().equals(orgId)) continue;
            String key = ce.getExternalId() != null ? ce.getExternalId() : String.valueOf(ce.getId());
            canonicalByStableId.put(key, ce);
        }
        for (CanonicalEvidence ce : direct) {
            String key = ce.getExternalId() != null ? ce.getExternalId() : String.valueOf(ce.getId());
            if (canonicalByStableId.containsKey(key)) continue;
            if (ce.getOrganisation() != null && !ce.getOrganisation().getOrgId().equals(orgId)) continue;
            canonicalByStableId.put(key, ce);
        }

        for (CanonicalEvidence ce : canonicalByStableId.values()) {

            // Parse safe attributes from normalizedPayload (size, contentType, tags) – reuse GraphWorkspaceService pattern
            Long sizeVal = null;
            String contentTypeVal = null;
            List<String> tagsVal = null;
            try {
                String payload = ce.getNormalizedPayload();
                if (payload != null && !payload.isBlank()) {
                    JsonNode node = objectMapper.readTree(payload);
                    if (node.has("size") && node.get("size").isNumber()) sizeVal = node.get("size").asLong();
                    if (node.has("contentType") && node.get("contentType").isTextual()) contentTypeVal = node.get("contentType").asText(null);
                    if (node.has("tags") && node.get("tags").isArray()) {
                        tagsVal = new ArrayList<>();
                        for (JsonNode t : node.get("tags")) {
                            if (t.isTextual()) tagsVal.add(t.asText());
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to parse normalizedPayload for {}: {}", ce.getExternalId(), e.getMessage());
            }

            // EVIDENCE_CREATED – sourceCreatedAt only
            if (ce.getSourceCreatedAt() != null) {
                events.add(evidenceEvent("EVIDENCE_CREATED", ce.getSourceCreatedAt(), ce, sizeVal, contentTypeVal, tagsVal));
            }
            // EVIDENCE_UPDATED – only when non-null and different from sourceCreatedAt
            // Preserves truthful null-sourceCreatedAt behavior: emit only update if created is null
            if (ce.getSourceUpdatedAt() != null && !ce.getSourceUpdatedAt().equals(ce.getSourceCreatedAt())) {
                events.add(evidenceEvent("EVIDENCE_UPDATED", ce.getSourceUpdatedAt(), ce, sizeVal, contentTypeVal, tagsVal));
            }
            // Intentionally NOT emitting: EVIDENCE_LINKED (link.createdAt), FIRST_SEEN, LAST_SEEN,
            // COMPLAINT_*, INVESTIGATION_CREATED, check/decision/note timestamps.
            // Do not infer action from title/filename/sourceType/status.
        }

        // Deterministic ordering: eventTime ASC, eventType ASC, stableId ASC, then caseId/actorId/title as tie-breaker
        events.sort(Comparator.comparing((InvestigationTimelineEventResponse e) -> e.getEventTime())
                .thenComparing(InvestigationTimelineEventResponse::getEventType)
                .thenComparing(e -> e.getStableId() != null ? e.getStableId() : "")
                .thenComparing(e -> e.getCaseId() != null ? e.getCaseId() : "")
                .thenComparing(e -> e.getActorId() != null ? e.getActorId() : "")
                .thenComparing(e -> e.getTitle() != null ? e.getTitle() : ""));

        long total = events.size();
        int totalPages = (int) Math.ceil((double) total / s);
        int from = Math.min(p * s, events.size());
        int to = Math.min(from + s, events.size());
        List<InvestigationTimelineEventResponse> pageContent = events.subList(from, to);

        return InvestigationTimelineResponse.builder()
                .investigationId(inv.getId())
                .investigationKey(inv.getInvestigationKey())
                .events(pageContent)
                .page(p).size(s)
                .totalElements(total).totalPages(totalPages)
                .build();
    }

    private InvestigationTimelineEventResponse evidenceEvent(String type, Instant time, CanonicalEvidence ce, Long size, String contentType, List<String> tags) {
        return InvestigationTimelineEventResponse.builder()
                .eventType(type)
                .eventTime(time != null ? time.toString() : null)
                .title(ce.getTitle())
                .description(ce.getTitle())
                .sourceType(ce.getSourceType())
                .sourceId(ce.getExternalId())
                .stableId(ce.getExternalId())
                .caseId(ce.getCaseId())
                .actorId(ce.getActorId())
                .parentId(ce.getParentId())
                .status(ce.getStatus())
                .size(size)
                .contentType(contentType)
                .tags(tags)
                .metadata(null)
                .build();
    }
}

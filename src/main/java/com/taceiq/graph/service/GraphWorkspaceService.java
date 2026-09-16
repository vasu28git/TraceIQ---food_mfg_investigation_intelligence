package com.taceiq.graph.service;

import com.taceiq.entity.CanonicalEvidence;
import com.taceiq.entity.IngestedSourceRecord;
import com.taceiq.graph.GraphQueryRepository;
import com.taceiq.graph.dto.CaseFileResponse;
import com.taceiq.graph.dto.GraphEvidenceDetailResponse;
import com.taceiq.repository.CanonicalEvidenceRepository;
import com.taceiq.repository.IngestedSourceRecordRepository;
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

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class GraphWorkspaceService {

    private final GraphQueryRepository graphQueryRepository;
    private final CanonicalEvidenceRepository canonicalRepo;
    private final IngestedSourceRecordRepository sourceRecordRepository;
    private final GraphReadinessService readinessService;
    private final AuthorizationService authorizationService;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired
    public GraphWorkspaceService(GraphQueryRepository graphQueryRepository,
                                 CanonicalEvidenceRepository canonicalRepo,
                                 IngestedSourceRecordRepository sourceRecordRepository,
                                 GraphReadinessService readinessService,
                                 AuthorizationService authorizationService,
                                 ObjectMapper objectMapper) {
        this.graphQueryRepository = graphQueryRepository;
        this.canonicalRepo = canonicalRepo;
        this.sourceRecordRepository = sourceRecordRepository;
        this.readinessService = readinessService;
        this.authorizationService = authorizationService;
        this.objectMapper = objectMapper;
    }

    // Backward compat for tests
    public GraphWorkspaceService(GraphQueryRepository graphQueryRepository,
                                 CanonicalEvidenceRepository canonicalRepo,
                                 GraphReadinessService readinessService,
                                 AuthorizationService authorizationService,
                                 ObjectMapper objectMapper) {
        this(graphQueryRepository, canonicalRepo, null, readinessService, authorizationService, objectMapper);
    }

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    public GraphEvidenceDetailResponse getEvidenceDetail(String stableId) {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireEvidenceGraphAccess();
        // Evidence detail is PostgreSQL-authoritative – do NOT require Neo4j graphReady
        // Graph relationships are fetched separately via trace
        if (stableId == null || stableId.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "stableId is required");
        String sid = stableId.trim();
        // PostgreSQL authoritative, tenant-scoped, not deleted
        CanonicalEvidence ev = null;
        try {
            Optional<CanonicalEvidence> opt = canonicalRepo.findByExternalIdAndOrganisationOrgId(sid, orgId);
            if (opt.isPresent()) ev = opt.get();
        } catch (Exception e) {
            log.debug("Non-unique or failed externalId lookup for {}: {}", sid, e.getMessage());
        }
        if (ev == null) {
            List<CanonicalEvidence> list = canonicalRepo.findByExternalIdInAndOrganisationOrgId(List.of(sid), orgId);
            ev = list.stream().filter(c -> !Boolean.TRUE.equals(c.getIsDeleted())).findFirst().orElse(null);
        }
        if (ev == null || Boolean.TRUE.equals(ev.getIsDeleted())) {
            Optional<IngestedSourceRecord> srcOpt = Optional.empty();
            if (sourceRecordRepository != null) {
                if (sid.startsWith("SRC_")) {
                    int secondUnderscore = sid.indexOf('_', 4);
                    if (secondUnderscore > 4) {
                        String srcType = sid.substring(4, secondUnderscore);
                        String recId = sid.substring(secondUnderscore + 1);
                        srcOpt = sourceRecordRepository.findByOrganisationOrgIdAndSourceTypeAndSourceRecordId(orgId, srcType, recId);
                    }
                }
                if (srcOpt.isEmpty()) {
                    srcOpt = sourceRecordRepository.findFirstByOrganisationOrgIdAndSourceRecordId(orgId, sid);
                }
            }
            if (srcOpt.isPresent()) {
                IngestedSourceRecord src = srcOpt.get();
                String timeStr = src.getIngestedAt() != null ? src.getIngestedAt().toString() : null;
                GraphEvidenceDetailResponse.Attributes attrs = null;
                if (src.getPayload() != null) {
                    try {
                        JsonNode node = objectMapper.readTree(src.getPayload());
                        Long size = node.has("size") ? node.get("size").asLong() : null;
                        String ct = node.has("contentType") ? node.get("contentType").asText(null) : null;
                        attrs = GraphEvidenceDetailResponse.Attributes.builder().size(size).contentType(ct).build();
                    } catch (Exception ignored) {}
                }
                return GraphEvidenceDetailResponse.builder()
                        .stableId(sid)
                        .title(src.getSourceRecordId())
                        .sourceType(src.getSourceType())
                        .status("READY")
                        .sourceCreatedAt(timeStr)
                        .sourceUpdatedAt(timeStr)
                        .firstSeenAt(timeStr)
                        .lastSeenAt(timeStr)
                        .attributes(attrs)
                        .build();
            }
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Evidence not found with stableId: " + sid);
        }
        // Verify exists in graph as well (consistency)
        // We do not query Neo4j for each detail to avoid extra call, but we have already ensured graphReady

        GraphEvidenceDetailResponse.Attributes attrs = null;
        try {
            String payload = ev.getNormalizedPayload();
            if (payload != null) {
                JsonNode node = objectMapper.readTree(payload);
                Long size = node.has("size") ? node.get("size").asLong() : null;
                String ct = node.has("contentType") ? node.get("contentType").asText(null) : null;
                List<String> tags = null;
                if (node.has("tags") && node.get("tags").isArray()) {
                    tags = new ArrayList<>();
                    for (JsonNode t : node.get("tags")) tags.add(t.asText());
                }
                attrs = GraphEvidenceDetailResponse.Attributes.builder().size(size).contentType(ct).tags(tags).build();
            }
        } catch (Exception e) {
            log.warn("Failed to parse normalizedPayload for {}: {}", sid, e.getMessage());
        }

        return GraphEvidenceDetailResponse.builder()
                .stableId(ev.getExternalId())
                .title(ev.getTitle())
                .sourceType(ev.getSourceType())
                .status(ev.getStatus())
                .sourceCreatedAt(ev.getSourceCreatedAt() != null ? ev.getSourceCreatedAt().toString() : null)
                .sourceUpdatedAt(ev.getSourceUpdatedAt() != null ? ev.getSourceUpdatedAt().toString() : null)
                .caseId(ev.getCaseId())
                .actorId(ev.getActorId())
                .parentId(ev.getParentId())
                .firstSeenAt(ev.getFirstSeenAt() != null ? ev.getFirstSeenAt().toString() : null)
                .lastSeenAt(ev.getLastSeenAt() != null ? ev.getLastSeenAt().toString() : null)
                .attributes(attrs)
                .build();
    }

    public CaseFileResponse getCaseFile(String caseId, Integer page, Integer size) {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireEvidenceGraphAccess();
        // Case evidence is PostgreSQL-authoritative – allow even when graph not ready
        // Only trace relationships require graph; they are fetched best-effort below
        if (caseId == null || caseId.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "caseId is required");
        String cId = caseId.trim();
        // PostgreSQL authoritative existence (allow even when Neo4j unavailable)
        if (canonicalRepo.countByCaseIdAndOrganisationOrgId(cId, orgId) == 0) {
            // Fallback to Neo4j if PostgreSQL has no record but graph might (for legacy)
            try {
                if (!graphQueryRepository.caseExists(orgId, cId)) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found with stableId: " + cId);
                }
            } catch (ResponseStatusException re) { throw re; }
            catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found with stableId: " + cId);
            }
        }
        int p = page != null ? page : DEFAULT_PAGE;
        int s = size != null ? size : DEFAULT_SIZE;
        if (p < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be >=0");
        if (s <= 0 || s > MAX_SIZE) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "size must be 1-100");

        // Evidence for case via PostgreSQL authoritative (bounded pagination)
        long total = canonicalRepo.countByCaseIdAndOrganisationOrgId(cId, orgId);
        var pageable = PageRequest.of(p, s, Sort.by("externalId").ascending());
        var pageResult = canonicalRepo.findByCaseIdAndOrganisationOrgId(cId, orgId, pageable);
        List<GraphEvidenceDetailResponse> evidence = pageResult.getContent().stream().map(ev -> {
            GraphEvidenceDetailResponse.Attributes attrs = null;
            try {
                JsonNode node = objectMapper.readTree(ev.getNormalizedPayload());
                Long sz = node.has("size") ? node.get("size").asLong() : null;
                String ct = node.has("contentType") ? node.get("contentType").asText(null) : null;
                List<String> tags = null;
                if (node.has("tags") && node.get("tags").isArray()) {
                    tags = new ArrayList<>();
                    for (JsonNode t : node.get("tags")) tags.add(t.asText());
                }
                attrs = GraphEvidenceDetailResponse.Attributes.builder().size(sz).contentType(ct).tags(tags).build();
            } catch (Exception ignored) {}
            return GraphEvidenceDetailResponse.builder()
                    .stableId(ev.getExternalId()).title(ev.getTitle()).sourceType(ev.getSourceType()).status(ev.getStatus())
                    .sourceCreatedAt(ev.getSourceCreatedAt()!=null?ev.getSourceCreatedAt().toString():null)
                    .sourceUpdatedAt(ev.getSourceUpdatedAt()!=null?ev.getSourceUpdatedAt().toString():null)
                    .caseId(ev.getCaseId()).actorId(ev.getActorId()).parentId(ev.getParentId())
                    .firstSeenAt(ev.getFirstSeenAt()!=null?ev.getFirstSeenAt().toString():null)
                    .lastSeenAt(ev.getLastSeenAt()!=null?ev.getLastSeenAt().toString():null)
                    .attributes(attrs)
                    .build();
        }).collect(Collectors.toList());

        // Actors for case – via graph: find distinct actors for case evidence
        // Use graphQueryRepository to get actors: we can derive from canonical actorIds for this case
        Set<String> actorIds = pageResult.getContent().stream().map(CanonicalEvidence::getActorId).filter(Objects::nonNull).collect(Collectors.toSet());
        // Also include all actors for case (not just page) for counts – query all canonical for case
        List<CanonicalEvidence> allForCase = canonicalRepo.findByCaseIdAndOrganisationOrgId(cId, orgId);
        Set<String> allActorIds = allForCase.stream().map(CanonicalEvidence::getActorId).filter(Objects::nonNull).collect(Collectors.toSet());
        List<CaseFileResponse.ActorSummary> actors = allActorIds.stream().map(aid ->
                CaseFileResponse.ActorSummary.builder().stableId(aid).label("Actor").build()
        ).collect(Collectors.toList());

        // Relationships for case: via GraphQueryRepository trace with depth 1 to get immediate neighbors
        // Reuse traceCase with depth 1 to get relationships
        List<CaseFileResponse.RelationshipSummary> relationships = new ArrayList<>();
        try {
            var trace = graphQueryRepository.traceCase(orgId, cId, 1);
            if (trace != null && trace.getRelationships() != null) {
                relationships = trace.getRelationships().stream().map(r ->
                        CaseFileResponse.RelationshipSummary.builder()
                                .fromStableId(r.getFromStableId()).fromLabel(r.getFromLabel())
                                .type(r.getType())
                                .toStableId(r.getToStableId()).toLabel(r.getToLabel())
                                .build()
                ).collect(Collectors.toList());
            }
            if (trace != null && trace.getRelationships() != null) {
                relationships = relationships.stream().filter(r -> Set.of("BELONGS_TO","CREATED_BY","DERIVED_FROM").contains(r.getType())).collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.warn("Failed to fetch relationships for case {}: {}", cId, e.getMessage());
        }

        long actorCount = allActorIds.size();
        long evidenceCount = total;

        return CaseFileResponse.builder()
                .caseSummary(CaseFileResponse.CaseSummary.builder().stableId(cId).label("Case").evidenceCount(evidenceCount).actorCount(actorCount).build())
                .evidence(evidence.stream().map(ed ->
                        com.taceiq.graph.dto.GraphEvidenceResponse.builder()
                                .stableId(ed.getStableId()).title(ed.getTitle()).sourceType(ed.getSourceType()).status(ed.getStatus())
                                .sourceCreatedAt(ed.getSourceCreatedAt()).sourceUpdatedAt(ed.getSourceUpdatedAt()).build()
                ).collect(Collectors.toList()))
                .actors(actors)
                .relationships(relationships)
                .page(p).size(s).totalElements(total).totalPages((int) Math.ceil((double) total / s))
                .build();
    }

    private void ensureGraphReady(Long orgId) {
        if (!readinessService.isOrgGraphReady(orgId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Graph not ready for organisation " + orgId);
        }
    }
}

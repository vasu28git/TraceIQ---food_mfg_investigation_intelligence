package com.taceiq.service;

import com.taceiq.dto.*;
import com.taceiq.entity.Complaint;
import com.taceiq.entity.Investigation;
import com.taceiq.graph.service.GraphReadinessService;
import com.taceiq.repository.*;
import com.taceiq.security.AuthorizationService;
import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class InvestigationReportService {

    private final InvestigationRepository investigationRepository;
    private final ComplaintRepository complaintRepository;
    private final InvestigationEvidenceRepository evidenceLinkRepository;
    private final CanonicalEvidenceRepository canonicalRepo;
    private final InvestigationTimelineService timelineService;
    private final GraphReadinessService graphReadinessService;
    private final AuthorizationService authorizationService;
    private final ConfigurationRepository configurationRepository;
    private final ConfigurationDefinitionRepository definitionRepository;

    @org.springframework.beans.factory.annotation.Autowired
    public InvestigationReportService(InvestigationRepository investigationRepository, ComplaintRepository complaintRepository,
                                      InvestigationEvidenceRepository evidenceLinkRepository, CanonicalEvidenceRepository canonicalRepo,
                                      InvestigationTimelineService timelineService,
                                      GraphReadinessService graphReadinessService,
                                      AuthorizationService authorizationService,
                                      ConfigurationRepository configurationRepository,
                                      ConfigurationDefinitionRepository definitionRepository) {
        this.investigationRepository = investigationRepository;
        this.complaintRepository = complaintRepository;
        this.evidenceLinkRepository = evidenceLinkRepository;
        this.canonicalRepo = canonicalRepo;
        this.timelineService = timelineService;
        this.graphReadinessService = graphReadinessService;
        this.authorizationService = authorizationService;
        this.configurationRepository = configurationRepository;
        this.definitionRepository = definitionRepository;
    }

    private static final Set<String> VALID_FORMATS = Set.of("PDF","CSV","JSON");
    private static final int MAX_TIMELINE = 1000;
    private static final int MAX_EVIDENCE = 1000;

    public InvestigationReportResponse generateReport(Long investigationId, String formatParam) {
        Long orgId = authorizationService.getCurrentOrgId();
        authorizationService.requireEvidenceGraphAccess();
        if (!graphReadinessService.isOrgGraphReady(orgId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Graph not ready for organisation " + orgId);
        }

        Investigation inv = investigationRepository.findByIdAndOrganisationOrgId(investigationId, orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Investigation not found with id: " + investigationId));

        String format = resolveFormat(formatParam, orgId);
        if (!VALID_FORMATS.contains(format)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid format: " + format + ". Allowed: " + VALID_FORMATS);
        }

        Optional<Complaint> complaintOpt = complaintRepository.findByInvestigationIdAndOrganisationOrgId(investigationId, orgId);
        ComplaintResponse complaintDto = complaintOpt.map(ComplaintResponse::fromEntity).orElse(null);

        InvestigationResponse invDto = InvestigationResponse.fromEntity(inv);

        InvestigationTimelineResponse timeline = timelineService.getTimeline(investigationId, 0, 50);
        if (timeline.getTotalElements() > MAX_TIMELINE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Timeline exceeds maximum report limit " + MAX_TIMELINE);
        }
        if (timeline.getTotalElements() > timeline.getEvents().size()) {
            int fullSize = (int) Math.min(timeline.getTotalElements(), MAX_TIMELINE);
            timeline = timelineService.getTimeline(investigationId, 0, fullSize);
        }

        List<InvestigationEvidenceResponse> evidence = new ArrayList<>();
        Map<String, InvestigationEvidenceResponse> evidenceMap = new LinkedHashMap<>();
        var linkedList = evidenceLinkRepository.findByOrganisationOrgIdAndInvestigationId(orgId, investigationId,
                org.springframework.data.domain.PageRequest.of(0, MAX_EVIDENCE)).getContent();
        for (var link : linkedList) {
            var ev = link.getCanonicalEvidence();
            if (ev == null || Boolean.TRUE.equals(ev.getIsDeleted())) continue;
            if (ev.getOrganisation() != null && !ev.getOrganisation().getOrgId().equals(orgId)) continue;
            evidenceMap.put(ev.getExternalId(), InvestigationEvidenceResponse.builder()
                    .stableId(ev.getExternalId()).title(ev.getTitle()).sourceType(ev.getSourceType()).status(ev.getStatus())
                    .linkedAt(link.getCreatedAt()).build());
        }
        try {
            var directList = canonicalRepo.findByIncidentIdAndOrganisationOrgId(investigationId, orgId);
            for (var ce : directList) {
                if (Boolean.TRUE.equals(ce.getIsDeleted())) continue;
                if (ce.getOrganisation() != null && !ce.getOrganisation().getOrgId().equals(orgId)) continue;
                if (evidenceMap.containsKey(ce.getExternalId())) continue;
                evidenceMap.put(ce.getExternalId(), InvestigationEvidenceResponse.builder()
                        .stableId(ce.getExternalId()).title(ce.getTitle()).sourceType(ce.getSourceType()).status(ce.getStatus())
                        .linkedAt(ce.getLastSeenAt() != null ? ce.getLastSeenAt() : ce.getFirstSeenAt()).build());
            }
        } catch (Exception ignored) {}
        evidence.addAll(evidenceMap.values());
        evidence.sort(Comparator.comparing(InvestigationEvidenceResponse::getStableId, Comparator.nullsLast(String::compareTo)));
        if (evidence.size() > MAX_EVIDENCE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Evidence exceeds maximum report limit " + MAX_EVIDENCE);
        }

        InvestigationReportResponse.ReportMetadata metadata = InvestigationReportResponse.ReportMetadata.builder()
                .investigationId(inv.getId())
                .investigationKey(inv.getInvestigationKey())
                .title(inv.getTitle())
                .generatedAt(Instant.now())
                .format(format)
                .status(inv.getStatus())
                .build();

        return InvestigationReportResponse.builder()
                .reportMetadata(metadata)
                .complaint(complaintDto)
                .investigation(invDto)
                .timeline(timeline)
                .evidence(evidence)
                .build();
    }

    public String renderCsv(InvestigationReportResponse report) {
        StringBuilder sb = new StringBuilder();
        sb.append("SECTION,FIELD,VALUE\n");
        appendCsv(sb, "REPORT", "investigationKey", report.getReportMetadata().getInvestigationKey());
        appendCsv(sb, "REPORT", "title", report.getReportMetadata().getTitle());
        appendCsv(sb, "REPORT", "generatedAt", String.valueOf(report.getReportMetadata().getGeneratedAt()));
        appendCsv(sb, "REPORT", "format", report.getReportMetadata().getFormat());
        if (report.getComplaint() != null) {
            appendCsv(sb, "COMPLAINT", "complaintKey", report.getComplaint().getComplaintKey());
            appendCsv(sb, "COMPLAINT", "title", report.getComplaint().getTitle());
        }
        if (report.getInvestigation() != null) {
            appendCsv(sb, "INVESTIGATION", "investigationKey", report.getInvestigation().getInvestigationKey());
            appendCsv(sb, "INVESTIGATION", "status", report.getInvestigation().getStatus());
        }
        if (report.getTimeline() != null) {
            for (var e : report.getTimeline().getEvents()) {
                appendCsv(sb, "TIMELINE", e.getEventType(), e.getTitle() + " | " + e.getEventTime());
            }
        }
        if (report.getEvidence() != null) {
            for (var ev : report.getEvidence()) {
                appendCsv(sb, "EVIDENCE", ev.getStableId(), ev.getTitle());
            }
        }
        return sb.toString();
    }

    public byte[] renderPdf(InvestigationReportResponse report) {
        try {
            Document document = new Document();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfWriter.getInstance(document, baos);
            document.open();
            document.add(new Paragraph("Investigation Report: " + report.getReportMetadata().getTitle()));
            document.add(new Paragraph("Investigation Key: " + report.getReportMetadata().getInvestigationKey()));
            document.add(new Paragraph("Generated At: " + report.getReportMetadata().getGeneratedAt()));
            document.add(new Paragraph("Format: " + report.getReportMetadata().getFormat()));
            document.add(new Paragraph("Status: " + report.getReportMetadata().getStatus()));
            document.add(new Paragraph(" "));
            if (report.getComplaint() != null) {
                document.add(new Paragraph("Complaint: " + report.getComplaint().getComplaintKey() + " - " + report.getComplaint().getTitle()));
            }
            document.add(new Paragraph("Timeline events: " + (report.getTimeline()!=null?report.getTimeline().getTotalElements():0)));
            if (report.getTimeline() != null) {
                for (var e : report.getTimeline().getEvents()) {
                    document.add(new Paragraph(e.getEventType() + " | " + e.getEventTime() + " | " + e.getTitle()));
                }
            }
            document.add(new Paragraph("Evidence: " + (report.getEvidence()!=null?report.getEvidence().size():0)));
            if (report.getEvidence()!=null) for (var ev: report.getEvidence()) document.add(new Paragraph(ev.getStableId() + " - " + ev.getTitle()));
            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to generate PDF", e);
        }
    }

    private String resolveFormat(String formatParam, Long orgId) {
        String fmt = formatParam;
        if (fmt == null || fmt.isBlank()) {
            try {
                var defOpt = definitionRepository.findByKey("REPORT_DEFAULT_FORMAT");
                String defVal = defOpt.map(d -> d.getDefaultValue()).orElse("PDF");
                var cfgOpt = configurationRepository.findByDefinitionKeyAndOrganisationOrgId("REPORT_DEFAULT_FORMAT", orgId);
                if (cfgOpt.isPresent() && cfgOpt.get().getValue() != null && !cfgOpt.get().getValue().isBlank()) {
                    fmt = cfgOpt.get().getValue().trim().toUpperCase();
                } else {
                    fmt = defVal.trim().toUpperCase();
                }
            } catch (Exception e) {
                fmt = "PDF";
            }
        }
        fmt = fmt.trim().toUpperCase();
        if (!VALID_FORMATS.contains(fmt)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid format: " + fmt);
        }
        return fmt;
    }

    private void appendCsv(StringBuilder sb, String section, String field, String value) {
        String v = value != null ? value : "";
        if (v.contains("\"") || v.contains(",") || v.contains("\n")) {
            v = "\"" + v.replace("\"", "\"\"") + "\"";
        }
        sb.append(section).append(",").append(field).append(",").append(v).append("\n");
    }
}


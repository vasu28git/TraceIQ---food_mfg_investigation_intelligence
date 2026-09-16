package com.taceiq.controller;

import com.taceiq.dto.InvestigationReportResponse;
import com.taceiq.service.InvestigationReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/investigations/{investigationId}/report")
@RequiredArgsConstructor
public class InvestigationReportController {

    private final InvestigationReportService reportService;

    @GetMapping
    public ResponseEntity<?> getReport(@PathVariable Long investigationId,
                                       @RequestParam(required = false) String format) {
        InvestigationReportResponse report = reportService.generateReport(investigationId, format);
        String fmt = report.getReportMetadata().getFormat();
        if ("CSV".equals(fmt)) {
            String csv = reportService.renderCsv(report);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"report-" + investigationId + ".csv\"")
                    .contentType(MediaType.valueOf("text/csv"))
                    .body(csv);
        } else if ("PDF".equals(fmt)) {
            byte[] pdf = reportService.renderPdf(report);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"report-" + investigationId + ".pdf\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdf);
        } else {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(report);
        }
    }
}

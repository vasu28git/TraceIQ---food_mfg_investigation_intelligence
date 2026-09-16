package com.taceiq.event;

import com.taceiq.graph.service.GraphProjectionService;
import com.taceiq.ingestion.SourceRecordIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class ManualFileIngestionListener {

    private final SourceRecordIngestionService ingestionService;
    private final GraphProjectionService graphProjectionService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onManualEvidenceCommitted(ManualEvidenceCommittedEvent event) {
        if (event.getFileId() == null) return;
        try {
            var result = ingestionService.ingestFile(event.getOrganisationId(), event.getFileId(), null);
            log.info("Structured ingestion for file {} org {}: created {} reused {} failed {}",
                    event.getFileId(), event.getOrganisationId(), result.created, result.reused, result.failed);
                graphProjectionService.projectForOrganisation(event.getOrganisationId());
                log.info("Post-ingestion graph projection succeeded for file {} org {}",
                    event.getFileId(), event.getOrganisationId());
        } catch (Exception e) {
            log.warn("Structured ingestion failed for file {} org {}: {}",
                    event.getFileId(), event.getOrganisationId(), e.getMessage());
        }
    }
}
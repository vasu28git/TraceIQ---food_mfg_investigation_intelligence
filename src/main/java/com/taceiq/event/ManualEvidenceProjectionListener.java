package com.taceiq.event;

import com.taceiq.graph.service.GraphProjectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class ManualEvidenceProjectionListener {

    private final GraphProjectionService graphProjectionService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onManualEvidenceCommitted(ManualEvidenceCommittedEvent event) {
        Long orgId = event.getOrganisationId();
        try {
            graphProjectionService.projectForOrganisation(orgId);
            log.info("Post-commit graph projection succeeded for manual evidence org {}", orgId);
        } catch (Exception e) {
            // Do not roll back PostgreSQL commit, just log safe error
            log.warn("Post-commit graph projection failed for manual evidence org {}: {}", orgId, e.getMessage());
        }
    }
}

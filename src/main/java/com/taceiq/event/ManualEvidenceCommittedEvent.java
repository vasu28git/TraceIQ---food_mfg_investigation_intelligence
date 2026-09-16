package com.taceiq.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class ManualEvidenceCommittedEvent extends ApplicationEvent {

    private final Long organisationId;
    private final Long fileId;

    public ManualEvidenceCommittedEvent(Object source, Long organisationId) {
        this(source, organisationId, null);
    }

    public ManualEvidenceCommittedEvent(Object source, Long organisationId, Long fileId) {
        super(source);
        this.organisationId = organisationId;
        this.fileId = fileId;
    }
}

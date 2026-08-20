package com.investigation.platform.file.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileResponse {

    private UUID fileId;
    private UUID orgId;
    private UUID integrationId;
    private String fileName;
    private String fileType;
    private String storageKey;
    private Long fileSize;
    private Instant createdAt;
    private Instant updatedAt;
}

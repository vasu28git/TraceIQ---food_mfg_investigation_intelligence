package com.investigation.platform.file.service.impl;

import com.investigation.platform.file.service.FileStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.UUID;

@Slf4j
@Service
public class LocalMockFileStorageServiceImpl implements FileStorageService {

    @Override
    public String uploadFile(String fileName, String contentType, InputStream data, long size) {
        String storageKey = "storage/tenant/" + UUID.randomUUID() + "/" + fileName;
        log.info("File stored to mock object storage with key: {} (size: {} bytes)", storageKey, size);
        return storageKey;
    }

    @Override
    public InputStream downloadFile(String storageKey) {
        log.info("Mock downloading file with storageKey: {}", storageKey);
        return new ByteArrayInputStream(new byte[0]);
    }

    @Override
    public void deleteFile(String storageKey) {
        log.info("Mock deleted file with storageKey: {}", storageKey);
    }
}

package com.investigation.platform.file.service;

import java.io.InputStream;

public interface FileStorageService {

    String uploadFile(String fileName, String contentType, InputStream data, long size);

    InputStream downloadFile(String storageKey);

    void deleteFile(String storageKey);
}

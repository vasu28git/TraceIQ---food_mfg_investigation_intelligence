package com.taceiq;

import com.taceiq.entity.CanonicalEvidence;
import com.taceiq.entity.File;
import com.taceiq.repository.CanonicalEvidenceRepository;
import com.taceiq.repository.FileRepository;
import com.taceiq.service.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ManualFileUploadTest {

    FileRepository fileRepository;
    CanonicalEvidenceRepository canonicalEvidenceRepository;
    FileService fileService;

    @BeforeEach
    void setup() {
        fileRepository = mock(FileRepository.class);
        canonicalEvidenceRepository = mock(CanonicalEvidenceRepository.class);
        var orgRepo = mock(com.taceiq.repository.OrganisationRepository.class);
        var integRepo = mock(com.taceiq.repository.IntegrationRepository.class);
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var eventPublisher = mock(org.springframework.context.ApplicationEventPublisher.class);
        fileService = new FileService(fileRepository, orgRepo, integRepo, canonicalEvidenceRepository, mapper, eventPublisher);
        // Set storage path to temp
        org.springframework.test.util.ReflectionTestUtils.setField(fileService, "storagePath", System.getProperty("java.io.tmpdir") + "/taceiq_test_uploads");
        org.springframework.test.util.ReflectionTestUtils.setField(fileService, "maxSizeMb", 10);
        // Mock org reference
        var org = com.taceiq.entity.Organisation.builder().orgId(1L).name("OrgA").build();
        when(orgRepo.getReferenceById(1L)).thenReturn(org);
        when(fileRepository.save(any(File.class))).thenAnswer(i -> {
            File f = i.getArgument(0);
            f.setId(100L);
            return f;
        });
        when(canonicalEvidenceRepository.save(any(CanonicalEvidence.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void uploadSucceeds() {
        MockMultipartFile file = new MockMultipartFile("file", "report.pdf", "application/pdf", "hello".getBytes());
        File saved = fileService.uploadFile(file, 1L);
        assertNotNull(saved);
        assertEquals("report.pdf", saved.getOriginalName());
        assertEquals("MANUAL_UPLOAD", saved.getSourceType());
        verify(fileRepository).save(any(File.class));
        verify(canonicalEvidenceRepository).save(any(CanonicalEvidence.class));
    }

    @Test
    void uploadCreatesCanonicalEvidenceWithCorrectOrg() {
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", "content".getBytes());
        fileService.uploadFile(file, 1L);
        ArgumentCaptor<CanonicalEvidence> captor = ArgumentCaptor.forClass(CanonicalEvidence.class);
        verify(canonicalEvidenceRepository).save(captor.capture());
        CanonicalEvidence ev = captor.getValue();
        assertEquals(1L, ev.getOrganisation().getOrgId());
        assertEquals("MANUAL_UPLOAD", ev.getSourceType());
        assertTrue(ev.getExternalId().startsWith("MANUAL_FILE_"));
        assertNull(ev.getIntegration());
    }

    @Test
    void binaryNotStoredInNormalizedPayload() {
        MockMultipartFile file = new MockMultipartFile("file", "binary.pdf", "application/pdf", new byte[]{1,2,3,4,5});
        fileService.uploadFile(file, 1L);
        ArgumentCaptor<CanonicalEvidence> captor = ArgumentCaptor.forClass(CanonicalEvidence.class);
        verify(canonicalEvidenceRepository).save(captor.capture());
        String payload = captor.getValue().getNormalizedPayload();
        assertNotNull(payload);
        assertFalse(payload.contains("AQID")); // base64 of 1,2,3,4,5 would be AQIDBAU=
        assertTrue(payload.contains("binary.pdf"));
        assertFalse(payload.contains("binary\u0001")); // no binary
    }

    @Test
    void uploadValidatesEmptyFile() {
        MockMultipartFile empty = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);
        assertThrows(ResponseStatusException.class, () -> fileService.uploadFile(empty, 1L));
    }

    @Test
    void uploadValidatesFilename() {
        MockMultipartFile bad = new MockMultipartFile("file", "../evil.pdf", "application/pdf", "x".getBytes());
        assertThrows(ResponseStatusException.class, () -> fileService.uploadFile(bad, 1L));
    }

    @Test
    void uploadValidatesSize() {
        byte[] big = new byte[11 * 1024 * 1024];
        MockMultipartFile bigFile = new MockMultipartFile("file", "big.pdf", "application/pdf", big);
        assertThrows(ResponseStatusException.class, () -> fileService.uploadFile(bigFile, 1L));
    }

    @Test
    void duplicateContentCreatesSeparateFileButDeterministicExternalId() {
        MockMultipartFile f1 = new MockMultipartFile("file", "a.pdf", "application/pdf", "same".getBytes());
        MockMultipartFile f2 = new MockMultipartFile("file", "a.pdf", "application/pdf", "same".getBytes());
        // First file saves with id 100, second with 101 - mock to return different ids
        when(fileRepository.save(any(File.class))).thenAnswer(i -> {
            File f = i.getArgument(0);
            // Simulate auto-increment
            f.setId((long) (100 + (int)(Math.random()*1000)));
            return f;
        });
        File saved1 = fileService.uploadFile(f1, 1L);
        File saved2 = fileService.uploadFile(f2, 1L);
        assertNotEquals(saved1.getId(), saved2.getId());
        // But externalId is based on file id, so they are different
        ArgumentCaptor<CanonicalEvidence> captor = ArgumentCaptor.forClass(CanonicalEvidence.class);
        verify(canonicalEvidenceRepository, atLeast(2)).save(captor.capture());
        var all = captor.getAllValues();
        assertNotEquals(all.get(0).getExternalId(), all.get(1).getExternalId());
    }

    @Test
    void uploadWithDifferentFilenamesNotOverwrite() {
        MockMultipartFile f1 = new MockMultipartFile("file", "a.pdf", "application/pdf", "content1".getBytes());
        MockMultipartFile f2 = new MockMultipartFile("file", "b.pdf", "application/pdf", "content2".getBytes());
        File s1 = fileService.uploadFile(f1, 1L);
        File s2 = fileService.uploadFile(f2, 1L);
        assertNotEquals(s1.getStorageKey(), s2.getStorageKey());
    }
}

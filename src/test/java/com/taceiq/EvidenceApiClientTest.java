package com.taceiq;

import com.taceiq.integration.EvidenceApiClient;
import com.taceiq.integration.EvidenceApiConfig;
import com.taceiq.integration.EvidenceApiException;
import com.taceiq.integration.dto.EvidenceApiResponse;
import com.taceiq.integration.dto.ExternalEvidenceRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

public class EvidenceApiClientTest {

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private EvidenceApiClient client;
    private EvidenceApiConfig config;
    private ObjectMapper mapper = new ObjectMapper();

    private static final String BASE = "https://evidence.example.internal";
    private static final String TOKEN = "test-token-***";

    @BeforeEach
    void setup() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        client = new EvidenceApiClient(restTemplate);
        config = EvidenceApiConfig.builder().baseUrl(BASE).bearerToken(TOKEN).build();
    }

    String samplePage1() {
        return """
        {
          "data": [
            {
              "evidenceId": "ev_001",
              "caseId": "CASE-1001",
              "title": "ANONYMIZED \u2013 Network log batch 1",
              "sourceType": "FILE",
              "status": "READY",
              "createdAt": "2026-09-01T08:12:33.000Z",
              "updatedAt": "2026-09-03T10:15:00.000Z",
              "actor": {"actorId": "actor_42", "role": "SYSTEM"},
              "relationships": {"caseId_ref": "CASE-1001", "actorId_ref": "actor_42", "parentEvidenceId": null},
              "attributes": {"size": 2048, "contentType": "application/json", "storageRef": "REDACTED", "tags": ["ANONYMIZED"]}
            }
          ],
          "pagination": {"nextCursor": "cursor-2", "hasMore": true, "totalCount": null}
        }""";
    }

    String samplePage2() {
        return """
        {
          "data": [
            {
              "evidenceId": "ev_002",
              "caseId": "CASE-1001",
              "title": "ANONYMIZED \u2013 Endpoint snapshot",
              "sourceType": "API",
              "status": "READY",
              "createdAt": "2026-09-02T09:00:00.000Z",
              "updatedAt": "2026-09-03T10:16:12.000Z",
              "actor": {"actorId": "actor_07", "role": "ANALYST"},
              "relationships": {"caseId_ref": "CASE-1001", "actorId_ref": "actor_07", "parentEvidenceId": "ev_001"},
              "attributes": {"size": 5120, "contentType": "application/json", "storageRef": "REDACTED", "tags": ["ANONYMIZED"]}
            }
          ],
          "pagination": {"nextCursor": null, "hasMore": false, "totalCount": null}
        }""";
    }

    @Test
    void successfulFirstPage() {
        server.expect(requestTo(BASE + "/api/v1/evidence?limit=2"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer " + TOKEN))
                .andRespond(withSuccess(samplePage1(), MediaType.APPLICATION_JSON));
        EvidenceApiResponse resp = client.fetchEvidence(config, 2, null, null);
        assertEquals(1, resp.getData().size());
        assertEquals("ev_001", resp.getData().get(0).getEvidenceId());
        assertEquals("cursor-2", resp.getPagination().getNextCursor());
        assertTrue(resp.getPagination().getHasMore());
        server.verify();
    }

    @Test
    void successfulMultiPageExtraction() {
        server.expect(requestTo(BASE + "/api/v1/evidence?limit=2"))
                .andRespond(withSuccess(samplePage1(), MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/api/v1/evidence?limit=2&cursor=cursor-2"))
                .andRespond(withSuccess(samplePage2(), MediaType.APPLICATION_JSON));
        List<ExternalEvidenceRecord> all = client.fetchAll(config, 2, null);
        assertEquals(2, all.size());
        assertEquals("ev_001", all.get(0).getEvidenceId());
        assertEquals("ev_002", all.get(1).getEvidenceId());
        server.verify();
    }

    @Test
    void cursorPropagation() {
        server.expect(requestTo(BASE + "/api/v1/evidence?limit=1&cursor=abc123"))
                .andExpect(header("Authorization", "Bearer " + TOKEN))
                .andRespond(withSuccess(samplePage2(), MediaType.APPLICATION_JSON));
        EvidenceApiResponse resp = client.fetchEvidence(config, 1, "abc123", null);
        assertEquals(1, resp.getData().size());
        server.verify();
    }

    @Test
    void hasMoreFalseTermination() {
        String single = """
        {"data":[{"evidenceId":"ev_001","caseId":"CASE-1","title":"t","sourceType":"FILE","status":"READY","createdAt":"2026-09-01T00:00:00Z","updatedAt":"2026-09-01T00:00:00Z"}],"pagination":{"nextCursor":null,"hasMore":false}}""";
        server.expect(requestTo(BASE + "/api/v1/evidence?limit=10"))
                .andRespond(withSuccess(single, MediaType.APPLICATION_JSON));
        List<ExternalEvidenceRecord> all = client.fetchAll(config, 10, null);
        assertEquals(1, all.size());
        server.verify();
    }

    @Test
    void updatedSinceParameter() {
        Instant since = Instant.parse("2026-09-03T00:00:00Z");
        server.expect(requestTo(BASE + "/api/v1/evidence?limit=5&updatedSince=2026-09-03T00:00:00Z"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(samplePage2().replace("hasMore\": false","hasMore\": false"), MediaType.APPLICATION_JSON));
        // fetch with updatedSince
        client.fetchEvidence(config, 5, null, since);
        server.verify();
    }

    @Test
    void updatedSinceFetchAll() {
        Instant since = Instant.parse("2026-09-03T00:00:00Z");
        server.expect(requestTo(BASE + "/api/v1/evidence?limit=5&updatedSince=2026-09-03T00:00:00Z"))
                .andRespond(withSuccess("{\"data\":[],\"pagination\":{\"nextCursor\":null,\"hasMore\":false}}", MediaType.APPLICATION_JSON));
        List<ExternalEvidenceRecord> all = client.fetchAll(config, 5, since);
        assertEquals(0, all.size());
        server.verify();
    }

    @Test
    void authorizationHeaderIsSent() {
        server.expect(requestTo(BASE + "/api/v1/evidence?limit=1"))
                .andExpect(header("Authorization", "Bearer " + TOKEN))
                .andExpect(headerDoesNotExist("X-Api-Key"))
                .andRespond(withSuccess(samplePage2(), MediaType.APPLICATION_JSON));
        client.fetchEvidence(config, 1, null, null);
        server.verify();
    }

    @Test
    void missingNextCursorWhenHasMoreTrue() {
        String bad = "{\"data\":[{\"evidenceId\":\"ev_001\"}],\"pagination\":{\"nextCursor\":null,\"hasMore\":true}}";
        server.expect(requestTo(BASE + "/api/v1/evidence?limit=1"))
                .andRespond(withSuccess(bad, MediaType.APPLICATION_JSON));
        EvidenceApiException ex = assertThrows(EvidenceApiException.class, () -> client.fetchEvidence(config, 1, null, null));
        assertFalse(ex.isRetryable());
        assertTrue(ex.getMessage().contains("nextCursor"));
    }

    @Test
    void missingNextCursorFetchAllFails() {
        String bad = "{\"data\":[{\"evidenceId\":\"ev_001\"}],\"pagination\":{\"nextCursor\":null,\"hasMore\":true}}";
        server.expect(requestTo(BASE + "/api/v1/evidence?limit=1"))
                .andRespond(withSuccess(bad, MediaType.APPLICATION_JSON));
        assertThrows(EvidenceApiException.class, () -> client.fetchAll(config, 1, null));
    }

    @Test
    void repeatedCursorProtection() {
        String pageWithLoop = """
        {"data":[{"evidenceId":"ev_001"}],"pagination":{"nextCursor":"loop","hasMore":true}}""";
        server.expect(requestTo(BASE + "/api/v1/evidence?limit=1"))
                .andRespond(withSuccess(pageWithLoop, MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/api/v1/evidence?limit=1&cursor=loop"))
                .andRespond(withSuccess(pageWithLoop, MediaType.APPLICATION_JSON));
        EvidenceApiException ex = assertThrows(EvidenceApiException.class, () -> client.fetchAll(config, 1, null));
        assertTrue(ex.getMessage().contains("Repeated cursor"));
        assertFalse(ex.isRetryable());
    }

    @Test
    void handling401() {
        server.expect(requestTo(BASE + "/api/v1/evidence?limit=1"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.UNAUTHORIZED).body("{\"error\":\"Unauthorized\"}").contentType(MediaType.APPLICATION_JSON));
        EvidenceApiException ex = assertThrows(EvidenceApiException.class, () -> client.fetchEvidence(config, 1, null, null));
        assertEquals(401, ex.getStatusCode());
        assertFalse(ex.isRetryable());
    }

    @Test
    void handling403() {
        server.expect(requestTo(BASE + "/api/v1/evidence?limit=1"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.FORBIDDEN).body("{\"error\":\"Forbidden\"}").contentType(MediaType.APPLICATION_JSON));
        EvidenceApiException ex = assertThrows(EvidenceApiException.class, () -> client.fetchEvidence(config, 1, null, null));
        assertEquals(403, ex.getStatusCode());
        assertFalse(ex.isRetryable());
    }

    @Test
    void handling429() {
        server.expect(requestTo(BASE + "/api/v1/evidence?limit=1"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS).body("{\"error\":\"Too Many Requests\"}").contentType(MediaType.APPLICATION_JSON));
        EvidenceApiException ex = assertThrows(EvidenceApiException.class, () -> client.fetchEvidence(config, 1, null, null));
        assertEquals(429, ex.getStatusCode());
        assertTrue(ex.isRetryable());
    }

    @Test
    void handling5xx() {
        server.expect(requestTo(BASE + "/api/v1/evidence?limit=1"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR).body("{\"error\":\"Server\"}").contentType(MediaType.APPLICATION_JSON));
        EvidenceApiException ex = assertThrows(EvidenceApiException.class, () -> client.fetchEvidence(config, 1, null, null));
        assertEquals(500, ex.getStatusCode());
        assertTrue(ex.isRetryable());
    }

    @Test
    void timeoutNetworkFailureRetryable() {
        // simulate timeout via ResourceAccessException: server will not match, we simulate by using a different mock that throws
        RestTemplate failing = new RestTemplate() {
            @Override
            public <T> org.springframework.http.ResponseEntity<T> exchange(String url, HttpMethod method, org.springframework.http.HttpEntity<?> requestEntity, Class<T> responseType, Object... uriVariables) {
                throw new org.springframework.web.client.ResourceAccessException("I/O error on GET request", new java.net.SocketTimeoutException("Read timed out"));
            }
        };
        EvidenceApiClient failingClient = new EvidenceApiClient(failing);
        EvidenceApiException ex = assertThrows(EvidenceApiException.class, () -> failingClient.fetchEvidence(config, 1, null, null));
        assertTrue(ex.isRetryable());
        assertTrue(ex.getMessage().toLowerCase().contains("timeout") || ex.getMessage().toLowerCase().contains("network"));
    }

    @Test
    void malformedResponse() {
        server.expect(requestTo(BASE + "/api/v1/evidence?limit=1"))
                .andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));
        EvidenceApiException ex = assertThrows(EvidenceApiException.class, () -> client.fetchEvidence(config, 1, null, null));
        // verify no token leak
        assertFalse(ex.getMessage().contains(TOKEN));
        assertNotNull(ex.getMessage());
    }

    @Test
    void noDatabaseWrites() {
        // client has no repository dependencies – verify via reflection
        assertEquals(0, countFieldsOfType(client, com.taceiq.repository.CanonicalEvidenceRepository.class));
        assertEquals(0, countFieldsOfType(client, com.taceiq.repository.IntegrationSyncRepository.class));
    }

    @Test
    void noNeo4jCalls() {
        // ensure no Neo4j related fields
        String src = client.getClass().getName();
        assertFalse(src.toLowerCase().contains("neo4j"));
        for (java.lang.reflect.Field f : client.getClass().getDeclaredFields()) {
            assertFalse(f.getType().getName().toLowerCase().contains("neo4j"), "Should not contain Neo4j");
        }
    }

    private int countFieldsOfType(Object obj, Class<?> type) {
        int c=0;
        for (java.lang.reflect.Field f : obj.getClass().getDeclaredFields()) if (type.isAssignableFrom(f.getType())) c++;
        return c;
    }
}

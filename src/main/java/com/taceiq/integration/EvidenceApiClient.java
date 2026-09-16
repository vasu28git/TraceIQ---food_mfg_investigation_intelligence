package com.taceiq.integration;

import com.taceiq.integration.dto.EvidenceApiResponse;
import com.taceiq.integration.dto.ExternalEvidenceRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
@Slf4j
public class EvidenceApiClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public EvidenceApiClient(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    // For tests / simple construction without Spring bean
    public EvidenceApiClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        this.objectMapper = new ObjectMapper();
    }

    public EvidenceApiResponse fetchEvidence(EvidenceApiConfig config, int limit, String cursor, Instant updatedSince) {
        if (config == null) throw new IllegalArgumentException("EvidenceApiConfig is required");
        if (config.getBaseUrl() == null || config.getBaseUrl().isBlank()) throw new IllegalArgumentException("baseUrl is required");
        if (config.getBearerToken() == null || config.getBearerToken().isBlank()) throw new IllegalArgumentException("bearerToken is required");
        if (limit <= 0) throw new IllegalArgumentException("limit must be > 0");

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(config.getEvidenceEndpoint())
                .queryParam("limit", limit);
        if (cursor != null && !cursor.isBlank()) {
            builder.queryParam("cursor", cursor);
        }
        if (updatedSince != null) {
            String iso = DateTimeFormatter.ISO_INSTANT.format(updatedSince);
            builder.queryParam("updatedSince", iso);
        }
        String url = builder.toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + config.getBearerToken());
        headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<EvidenceApiResponse> response = restTemplate.exchange(url, HttpMethod.GET, entity, EvidenceApiResponse.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new EvidenceApiException(response.getStatusCode().value(), EvidenceApiException.isRetryableStatus(response.getStatusCode().value()),
                        "Unexpected response status: " + response.getStatusCode(), null);
            }
            EvidenceApiResponse body = response.getBody();
            validatePagination(body.getPagination());
            return body;
        } catch (HttpClientErrorException e) {
            int code = e.getStatusCode().value();
            boolean retryable = EvidenceApiException.isRetryableStatus(code);
            // Do not include token in message
            String msg = "Evidence API client error " + code;
            throw new EvidenceApiException(code, retryable, msg, e.getResponseBodyAsString(), e);
        } catch (HttpServerErrorException e) {
            int code = e.getStatusCode().value();
            throw new EvidenceApiException(code, true, "Evidence API server error " + code, e.getResponseBodyAsString(), e);
        } catch (ResourceAccessException e) {
            // timeout / connection failure – retryable
            Throwable cause = e.getCause();
            String causeMsg = cause != null ? cause.getMessage() : e.getMessage();
            throw new EvidenceApiException(0, true, "Network/timeout failure: " + sanitize(causeMsg), null, e);
        } catch (RestClientException e) {
            // Includes malformed JSON via HttpMessageNotReadableException wrapped
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("json")) {
                throw new EvidenceApiException(0, false, "Malformed JSON response", null, e);
            }
            throw new EvidenceApiException(0, false, "Rest client error: " + sanitize(e.getMessage()), null, e);
        }
    }

    public List<ExternalEvidenceRecord> fetchAll(EvidenceApiConfig config, int limit, Instant updatedSince) {
        List<ExternalEvidenceRecord> all = new ArrayList<>();
        String cursor = null;
        Set<String> seenCursors = new HashSet<>();
        int maxPages = 200;
        for (int page = 0; page < maxPages; page++) {
            if (cursor != null && !seenCursors.add(cursor)) {
                throw new EvidenceApiException(0, false, "Repeated cursor detected: " + cursor, null);
            }
            EvidenceApiResponse resp = fetchEvidence(config, limit, cursor, updatedSince);
            if (resp.getData() != null) {
                all.addAll(resp.getData());
            }
            EvidenceApiResponse.Pagination pagination = resp.getPagination();
            if (pagination == null) {
                // No pagination envelope – assume single page
                break;
            }
            Boolean hasMore = pagination.getHasMore();
            if (hasMore == null || !hasMore) {
                break;
            }
            String next = pagination.getNextCursor();
            if (next == null || next.isBlank()) {
                throw new EvidenceApiException(0, false, "hasMore=true but nextCursor is missing", null);
            }
            cursor = next;
        }
        return Collections.unmodifiableList(all);
    }

    private void validatePagination(EvidenceApiResponse.Pagination pagination) {
        if (pagination == null) return;
        Boolean hasMore = pagination.getHasMore();
        String next = pagination.getNextCursor();
        if (Boolean.TRUE.equals(hasMore) && (next == null || next.isBlank())) {
            throw new EvidenceApiException(0, false, "hasMore=true but nextCursor is missing", null);
        }
    }

    private String sanitize(String msg) {
        if (msg == null) return null;
        // Never leak Bearer token – strip Authorization header values if present
        return msg.replaceAll("Bearer\\s+\\S+", "Bearer ***");
    }
}

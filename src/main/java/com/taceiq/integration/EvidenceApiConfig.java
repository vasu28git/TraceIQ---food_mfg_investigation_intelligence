package com.taceiq.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Getter;

import java.time.Duration;

@Getter
@Builder
public class EvidenceApiConfig {
    private String baseUrl;
    private String bearerToken;
    private Duration connectTimeout;
    private Duration readTimeout;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static EvidenceApiConfig fromIntegrationConfiguration(String configuration) {
        if (configuration == null || configuration.isBlank()) {
            throw new IllegalArgumentException("Integration configuration is empty");
        }
        try {
            JsonNode node = MAPPER.readTree(configuration);
            String baseUrl = null;
            if (node.has("baseUrl")) baseUrl = node.get("baseUrl").asText();
            else if (node.has("url")) baseUrl = node.get("url").asText();
            else if (node.has("base_url")) baseUrl = node.get("base_url").asText();

            String token = null;
            if (node.has("token")) token = node.get("token").asText();
            else if (node.has("bearerToken")) token = node.get("bearerToken").asText();
            else if (node.has("apiKey")) token = node.get("apiKey").asText();
            else if (node.has("api_key")) token = node.get("api_key").asText();

            if (baseUrl == null || baseUrl.isBlank()) {
                throw new IllegalArgumentException("Missing baseUrl/url in Integration.configuration");
            }
            if (token == null || token.isBlank()) {
                throw new IllegalArgumentException("Missing token/apiKey in Integration.configuration");
            }
            // Normalize trailing slash
            baseUrl = baseUrl.trim().replaceAll("/+$", "");
            return EvidenceApiConfig.builder()
                    .baseUrl(baseUrl)
                    .bearerToken(token)
                    .connectTimeout(Duration.ofSeconds(10))
                    .readTimeout(Duration.ofSeconds(30))
                    .build();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid Integration.configuration JSON: " + e.getMessage());
        }
    }

    public String getEvidenceEndpoint() {
        return baseUrl + "/api/v1/evidence";
    }
}

package com.taceiq.graph.config;

import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@Slf4j
public class Neo4jConfig {

    @Value("${app.neo4j.uri:}")
    private String uri;

    @Value("${app.neo4j.username:}")
    private String username;

    @Value("${app.neo4j.password:}")
    private String password;

    @Value("${app.neo4j.database:neo4j}")
    private String database;

    @Bean(destroyMethod = "close")
    public Driver neo4jDriver() {
        if (uri == null || uri.isBlank()) {
            log.warn("Neo4j URI not configured (app.neo4j.uri empty) – using dummy driver, projection will fail until configured. Configure NEO4J_URI/NEO4J_USERNAME/NEO4J_PASSWORD to enable.");
            try {
                return GraphDatabase.driver("bolt://localhost:7687", AuthTokens.none(), Config.builder().withMaxConnectionPoolSize(1).build());
            } catch (Exception e) {
                log.warn("Failed to create dummy Neo4j driver: {}", e.getMessage());
                // Fallback: return a driver that throws on use via anonymous class will be handled by services checking uri
                // For now, return null but ensure services handle null via ObjectProvider – instead return dummy that will be non-null
                // As last resort, return null and let services handle via try/catch (they already check for null)
                return null;
            }
        }
        try {
            Config config = Config.builder()
                    .withMaxConnectionLifetime(30, TimeUnit.MINUTES)
                    .withMaxConnectionPoolSize(20)
                    .withConnectionAcquisitionTimeout(10, TimeUnit.SECONDS)
                    .build();
            Driver driver;
            if (username != null && !username.isBlank()) {
                driver = GraphDatabase.driver(uri, AuthTokens.basic(username, password), config);
            } else {
                driver = GraphDatabase.driver(uri, config);
            }
            // Verify connectivity only if URI present, but don't fail startup if unreachable
            try {
                driver.verifyConnectivity();
                log.info("Neo4j driver connected to {}", uri);
            } catch (Exception e) {
                log.warn("Neo4j connectivity check failed for {}: {} – app will start, projection will fail until Neo4j is reachable.", uri, e.getMessage());
            }
            return driver;
        } catch (Exception e) {
            log.warn("Failed to create Neo4j driver for {}: {} – projection disabled.", uri, e.getMessage());
            try {
                return GraphDatabase.driver("bolt://localhost:7687", AuthTokens.none(), Config.builder().withMaxConnectionPoolSize(1).build());
            } catch (Exception ex) {
                return null;
            }
        }
    }

    public String getDatabase() {
        return database;
    }

    public boolean isConfigured() {
        return uri != null && !uri.isBlank();
    }
}

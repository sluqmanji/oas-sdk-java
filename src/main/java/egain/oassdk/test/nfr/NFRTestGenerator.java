package egain.oassdk.test.nfr;

import egain.oassdk.core.exceptions.GenerationException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

/**
 * Generates NFR testing scripts using SLA specification.
 *
 * @deprecated Use {@link egain.oassdk.testgenerators.nfr.NFRTestGenerator} instead.
 *             This class is retained for backward compatibility with {@link egain.oassdk.test.TestSDK}.
 */
@Deprecated
public class NFRTestGenerator {

    /**
     * Generate NFR tests
     *
     * @param slaSpec   SLA specification
     * @param outputDir Output directory
     * @throws GenerationException if generation fails
     */
    public void generateNFRTests(Map<String, Object> slaSpec, String outputDir) throws GenerationException {
        try {
            Files.createDirectories(Paths.get(outputDir));

            // Generate performance tests
            generatePerformanceTests(slaSpec, outputDir);

            // Generate scalability tests
            generateScalabilityTests(slaSpec, outputDir);

            // Generate reliability tests
            generateReliabilityTests(slaSpec, outputDir);

            // Generate availability tests
            generateAvailabilityTests(slaSpec, outputDir);

            // Generate security tests
            generateSecurityTests(slaSpec, outputDir);

            // Generate load tests
            generateLoadTests(slaSpec, outputDir);

            // Generate stress tests
            generateStressTests(slaSpec, outputDir);

            // Generate configuration
            generateNFRTestConfig(slaSpec, outputDir);

        } catch (Exception e) {
            throw new GenerationException("Failed to generate NFR tests: " + e.getMessage(), e);
        }
    }

    /**
     * Generate performance tests
     */
    private void generatePerformanceTests(Map<String, Object> slaSpec, String outputDir) throws IOException {
        String performanceTest = """
                package com.example.nfr;
                
                import org.junit.jupiter.api.Test;
                import org.junit.jupiter.api.BeforeEach;
                import jakarta.ws.rs.client.Client;
                import jakarta.ws.rs.client.ClientBuilder;
                import jakarta.ws.rs.client.WebTarget;
                import jakarta.ws.rs.client.Entity;
                import jakarta.ws.rs.core.MediaType;
                import jakarta.ws.rs.core.Response;
                import static org.junit.jupiter.api.Assertions.*;
                
                // Test class (using plain JUnit + JAX-RS Client)
                public class PerformanceTests {
                    public void testResponseTimeP95() {
                        long startTime = System.currentTimeMillis();
                        Response response = target.path("health").request().get();
                        long responseTime = System.currentTimeMillis() - startTime;
                
                        assertTrue(responseTime < P95_RESPONSE_TIME, "P95 response time should be less than " + P95_RESPONSE_TIME + "ms");
                        assertEquals(200, response.getStatus());
                        response.close();
                    }
                
                    @Test
                    public void testResponseTimeP99() {
                        long startTime = System.currentTimeMillis();
                        Response response = target.path("health").request().get();
                        long responseTime = System.currentTimeMillis() - startTime;
                
                        assertTrue(responseTime < P99_RESPONSE_TIME, "P99 response time should be less than " + P99_RESPONSE_TIME + "ms");
                        assertEquals(200, response.getStatus());
                        response.close();
                    }
                
                    @Test
                    public void testThroughput() {
                        long startTime = System.currentTimeMillis();
                        long endTime = startTime + 1000; // 1 second
                
                        int requestCount = 0;
                        while (System.currentTimeMillis() < endTime) {
                            Response response = target.path("health").request().get();
                            if (response.getStatus() == 200) {
                                requestCount++;
                            }
                            response.close();
                        }
                
                        assertTrue(requestCount >= THROUGHPUT,
                            "Throughput should be at least " + THROUGHPUT + " requests per second");
                    }
                }
                """;

        Files.write(Paths.get(outputDir, "PerformanceTests.java"), performanceTest.getBytes());
    }

    /**
     * Generate scalability tests
     */
    private void generateScalabilityTests(Map<String, Object> slaSpec, String outputDir) throws IOException {
        String scalabilityTest = """
                package com.example.nfr;
                
                import org.junit.jupiter.api.Test;
                import org.junit.jupiter.api.BeforeEach;
                import jakarta.ws.rs.client.Client;
                import jakarta.ws.rs.client.ClientBuilder;
                import jakarta.ws.rs.client.WebTarget;
                import jakarta.ws.rs.client.Entity;
                import jakarta.ws.rs.core.MediaType;
                import jakarta.ws.rs.core.Response;
                import static org.junit.jupiter.api.Assertions.*;
                
                // Test class (using plain JUnit + JAX-RS Client)
                @TestPropertySource(properties = {
                    "nfr.scalability.concurrent-users=10000",
                    "nfr.scalability.data-volume=1TB"
                })
                public class ScalabilityTests {
                
                    private Client client;
                    private WebTarget target;
                
                    @BeforeEach
                    public void setUp() {
                        client = ClientBuilder.newClient();
                        target = client.target("http://localhost:8080/api");
                    }
                
                    @Test
                    public void testConcurrentUsers() {
                        int concurrentUsers = 10000;
                        int successfulRequests = 0;
                
                        // Simulate concurrent users
                        for (int i = 0; i < concurrentUsers; i++) {
                            try {
                                Response response = target.path("health").request().get();
                                if (response.getStatus() == 200) {
                                    successfulRequests++;
                                }
                            } catch (Exception e) {
                                // Handle exceptions
                            }
                        }
                
                        double successRate = (double) successfulRequests / concurrentUsers;
                        assertTrue(successRate >= 0.95,
                            "Success rate should be at least 95% with " + concurrentUsers + " concurrent users");
                    }
                
                    @Test
                    public void testDataVolume() {
                        // Test with large data volume
                        String largeData = "x".repeat(1000000); // 1MB of data
                
                        Response response = target.path("data").request()
                            .post(Entity.entity(largeData, MediaType.TEXT_PLAIN));
                
                        assertEquals(200, response.getStatus());
                    }
                
                    @Test
                    public void testMemoryUsage() {
                        Runtime runtime = Runtime.getRuntime();
                        long initialMemory = runtime.totalMemory() - runtime.freeMemory();
                
                        // Perform operations
                        for (int i = 0; i < 1000; i++) {
                            restTemplate.getForEntity("/api/health", String.class);
                        }
                
                        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
                        long memoryIncrease = finalMemory - initialMemory;
                
                        // Memory increase should be reasonable
                        assertTrue(memoryIncrease < 100 * 1024 * 1024, // 100MB
                            "Memory increase should be less than 100MB");
                    }
                }
                """;

        Files.write(Paths.get(outputDir, "ScalabilityTests.java"), scalabilityTest.getBytes());
    }

    /**
     * Generate reliability tests
     */
    private void generateReliabilityTests(Map<String, Object> slaSpec, String outputDir) throws IOException {
        String reliabilityTest = """
                package com.example.nfr;
                
                import org.junit.jupiter.api.Test;
                import org.junit.jupiter.api.BeforeEach;
                import jakarta.ws.rs.client.Client;
                import jakarta.ws.rs.client.ClientBuilder;
                import jakarta.ws.rs.client.WebTarget;
                import jakarta.ws.rs.client.Entity;
                import jakarta.ws.rs.core.MediaType;
                import jakarta.ws.rs.core.Response;
                import static org.junit.jupiter.api.Assertions.*;
                
                // Test class (using plain JUnit + JAX-RS Client)
                @TestPropertySource(properties = {
                    "nfr.reliability.error-rate=0.01",
                    "nfr.reliability.uptime=99.9"
                })
                public class ReliabilityTests {
                
                    private Client client;
                    private WebTarget target;
                
                    @BeforeEach
                    public void setUp() {
                        client = ClientBuilder.newClient();
                        target = client.target("http://localhost:8080/api");
                    }
                
                    @Test
                    public void testErrorRate() {
                        int totalRequests = 1000;
                        int errorCount = 0;
                
                        for (int i = 0; i < totalRequests; i++) {
                            try {
                                Response response = target.path("health").request().get();
                                if (response.getStatus() >= 400) {
                                    errorCount++;
                                }
                            } catch (Exception e) {
                                errorCount++;
                            }
                        }
                
                        double errorRate = (double) errorCount / totalRequests;
                        assertTrue(errorRate <= 0.01,
                            "Error rate should be less than 1%");
                    }
                
                    @Test
                    public void testUptime() {
                        long testDuration = 60000; // 1 minute
                        long startTime = System.currentTimeMillis();
                        long endTime = startTime + testDuration;
                
                        int successfulRequests = 0;
                        int totalRequests = 0;
                
                        while (System.currentTimeMillis() < endTime) {
                            try {
                                Response response = target.path("health").request().get();
                                totalRequests++;
                                if (response.getStatus() == 200) {
                                    successfulRequests++;
                                }
                            } catch (Exception e) {
                                totalRequests++;
                            }
                        }
                
                        double uptime = (double) successfulRequests / totalRequests;
                        assertTrue(uptime >= 0.999,
                            "Uptime should be at least 99.9%");
                    }
                
                    @Test
                    public void testFaultTolerance() {
                        // Test system behavior under fault conditions
                        int faultCount = 0;
                        int totalTests = 100;
                
                        for (int i = 0; i < totalTests; i++) {
                            try {
                                // Simulate fault condition
                                ResponseEntity<String> response = restTemplate.getForEntity("/api/fault", String.class);
                                if (response.getStatus() >= 500) {
                                    faultCount++;
                                }
                            } catch (Exception e) {
                                faultCount++;
                            }
                        }
                
                        // System should handle faults gracefully
                        assertTrue(faultCount < totalTests * 0.1,
                            "System should handle faults gracefully");
                    }
                }
                """;

        Files.write(Paths.get(outputDir, "ReliabilityTests.java"), reliabilityTest.getBytes());
    }

    /**
     * Generate availability tests
     */
    private void generateAvailabilityTests(Map<String, Object> slaSpec, String outputDir) throws IOException {
        String availabilityTest = """
                package com.example.nfr;
                
                import org.junit.jupiter.api.Test;
                import org.junit.jupiter.api.BeforeEach;
                import jakarta.ws.rs.client.Client;
                import jakarta.ws.rs.client.ClientBuilder;
                import jakarta.ws.rs.client.WebTarget;
                import jakarta.ws.rs.client.Entity;
                import jakarta.ws.rs.core.MediaType;
                import jakarta.ws.rs.core.Response;
                import static org.junit.jupiter.api.Assertions.*;
                
                // Test class (using plain JUnit + JAX-RS Client)
                @TestPropertySource(properties = {
                    "nfr.availability.target=99.9"
                })
                public class AvailabilityTests {
                
                    private Client client;
                    private WebTarget target;
                
                    @BeforeEach
                    public void setUp() {
                        client = ClientBuilder.newClient();
                        target = client.target("http://localhost:8080/api");
                    }
                
                    @Test
                    public void testAvailability() {
                        long testDuration = 300000; // 5 minutes
                        long startTime = System.currentTimeMillis();
                        long endTime = startTime + testDuration;
                
                        int successfulRequests = 0;
                        int totalRequests = 0;
                
                        while (System.currentTimeMillis() < endTime) {
                            try {
                                Response response = target.path("health").request().get();
                                totalRequests++;
                                if (response.getStatus() == 200) {
                                    successfulRequests++;
                                }
                            } catch (Exception e) {
                                totalRequests++;
                            }
                        }
                
                        double availability = (double) successfulRequests / totalRequests * 100;
                        assertTrue(availability >= 99.9,
                            "Availability should be at least 99.9%");
                    }
                
                    @Test
                    public void testRecoveryTime() {
                        long startTime = System.currentTimeMillis();
                
                        // Simulate system recovery
                        Response response = target.path("health").request().get();
                
                        long recoveryTime = System.currentTimeMillis() - startTime;
                        assertTrue(recoveryTime < 5000,
                            "Recovery time should be less than 5 seconds");
                    }
                
                    @Test
                    public void testHealthCheck() {
                        Response response = target.path("health").request().get();
                
                        assertEquals(200, response.getStatus());
                        assertNotNull(response.getBody());
                    }
                }
                """;

        Files.write(Paths.get(outputDir, "AvailabilityTests.java"), availabilityTest.getBytes());
    }

    /**
     * Generate security tests
     */
    private void generateSecurityTests(Map<String, Object> slaSpec, String outputDir) throws IOException {
        String securityTest = """
                package com.example.nfr;
                
                import org.junit.jupiter.api.Test;
                import org.junit.jupiter.api.BeforeEach;
                import jakarta.ws.rs.client.Client;
                import jakarta.ws.rs.client.ClientBuilder;
                import jakarta.ws.rs.client.WebTarget;
                import jakarta.ws.rs.client.Entity;
                import jakarta.ws.rs.core.MediaType;
                import jakarta.ws.rs.core.Response;
                import static org.junit.jupiter.api.Assertions.*;
                
                // Test class (using plain JUnit + JAX-RS Client)
                @TestPropertySource(properties = {
                    "nfr.security.authentication=true",
                    "nfr.security.authorization=true"
                })
                public class SecurityTests {
                
                    private Client client;
                    private WebTarget target;
                
                    @BeforeEach
                    public void setUp() {
                        client = ClientBuilder.newClient();
                        target = client.target("http://localhost:8080/api");
                    }
                
                    @Test
                    public void testAuthentication() {
                        // Test without authentication
                        Response response = target.path("protected").request().get();
                        assertEquals(401, response.getStatus());
                    }
                
                    @Test
                    public void testAuthorization() {
                        // Test with invalid authorization
                        Response response = target.path("admin").request().get();
                        assertEquals(403, response.getStatus());
                    }
                
                    @Test
                    public void testInputValidation() {
                        // Test with malicious input
                        String maliciousInput = "<script>alert('xss')</script>";
                        ResponseEntity<String> response = restTemplate.postForEntity(
                            "/api/data", maliciousInput, String.class);
                
                        // Should not execute script
                        assertFalse(response.readEntity(String.class).contains("<script>"));
                    }
                
                    @Test
                    public void testSQLInjection() {
                        // Test with SQL injection attempt
                        String sqlInjection = "'; DROP TABLE users; --";
                        Response response = target.path("search").request()
                            .post(Entity.entity(sqlInjection, MediaType.TEXT_PLAIN));
                
                        // Should handle safely
                        assertNotEquals(500, response.getStatus());
                    }
                }
                """;

        Files.write(Paths.get(outputDir, "SecurityTests.java"), securityTest.getBytes());
    }

    /**
     * Generate load tests
     */
    private void generateLoadTests(Map<String, Object> slaSpec, String outputDir) throws IOException {
        String loadTest = """
                package com.example.nfr;
                
                import org.junit.jupiter.api.Test;
                import org.junit.jupiter.api.BeforeEach;
                import jakarta.ws.rs.client.Client;
                import jakarta.ws.rs.client.ClientBuilder;
                import jakarta.ws.rs.client.WebTarget;
                import jakarta.ws.rs.client.Entity;
                import jakarta.ws.rs.core.MediaType;
                import jakarta.ws.rs.core.Response;
                import static org.junit.jupiter.api.Assertions.*;
                
                // Test class (using plain JUnit + JAX-RS Client)
                @TestPropertySource(properties = {
                    "nfr.load.max-users=1000",
                    "nfr.load.duration=300"
                })
                public class LoadTests {
                
                    private Client client;
                    private WebTarget target;
                
                    @BeforeEach
                    public void setUp() {
                        client = ClientBuilder.newClient();
                        target = client.target("http://localhost:8080/api");
                    }
                
                    @Test
                    public void testLoadUnderNormalConditions() {
                        int maxUsers = 1000;
                        int duration = 300; // 5 minutes
                        long startTime = System.currentTimeMillis();
                        long endTime = startTime + duration * 1000;
                
                        int successfulRequests = 0;
                        int totalRequests = 0;
                
                        while (System.currentTimeMillis() < endTime) {
                            for (int i = 0; i < maxUsers; i++) {
                                try {
                                    Response response = target.path("health").request().get();
                                    totalRequests++;
                                    if (response.getStatus() == 200) {
                                        successfulRequests++;
                                    }
                                } catch (Exception e) {
                                    totalRequests++;
                                }
                            }
                        }
                
                        double successRate = (double) successfulRequests / totalRequests;
                        assertTrue(successRate >= 0.95,
                            "Success rate should be at least 95% under load");
                    }
                
                    @Test
                    public void testResponseTimeUnderLoad() {
                        int maxUsers = 1000;
                        long startTime = System.currentTimeMillis();
                
                        for (int i = 0; i < maxUsers; i++) {
                            Response response = target.path("health").request().get();
                            long responseTime = System.currentTimeMillis() - startTime;
                
                            assertTrue(responseTime < 2000,
                                "Response time should be less than 2 seconds under load");
                        }
                    }
                }
                """;

        Files.write(Paths.get(outputDir, "LoadTests.java"), loadTest.getBytes());
    }

    /**
     * Generate stress tests
     */
    private void generateStressTests(Map<String, Object> slaSpec, String outputDir) throws IOException {
        String stressTest = """
                package com.example.nfr;
                
                import org.junit.jupiter.api.Test;
                import org.junit.jupiter.api.BeforeEach;
                import jakarta.ws.rs.client.Client;
                import jakarta.ws.rs.client.ClientBuilder;
                import jakarta.ws.rs.client.WebTarget;
                import jakarta.ws.rs.client.Entity;
                import jakarta.ws.rs.core.MediaType;
                import jakarta.ws.rs.core.Response;
                import static org.junit.jupiter.api.Assertions.*;
                
                // Test class (using plain JUnit + JAX-RS Client)
                @TestPropertySource(properties = {
                    "nfr.stress.max-users=5000",
                    "nfr.stress.duration=600"
                })
                public class StressTests {
                
                    private Client client;
                    private WebTarget target;
                
                    @BeforeEach
                    public void setUp() {
                        client = ClientBuilder.newClient();
                        target = client.target("http://localhost:8080/api");
                    }
                
                    @Test
                    public void testStressUnderHighLoad() {
                        int maxUsers = 5000;
                        int duration = 600; // 10 minutes
                        long startTime = System.currentTimeMillis();
                        long endTime = startTime + duration * 1000;
                
                        int successfulRequests = 0;
                        int totalRequests = 0;
                
                        while (System.currentTimeMillis() < endTime) {
                            for (int i = 0; i < maxUsers; i++) {
                                try {
                                    Response response = target.path("health").request().get();
                                    totalRequests++;
                                    if (response.getStatus() == 200) {
                                        successfulRequests++;
                                    }
                                } catch (Exception e) {
                                    totalRequests++;
                                }
                            }
                        }
                
                        double successRate = (double) successfulRequests / totalRequests;
                        assertTrue(successRate >= 0.90,
                            "Success rate should be at least 90% under stress");
                    }
                
                    @Test
                    public void testSystemRecoveryAfterStress() {
                        // Apply stress
                        for (int i = 0; i < 1000; i++) {
                            restTemplate.getForEntity("/api/health", String.class);
                        }
                
                        // Wait for recovery
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                
                        // Test recovery
                        Response response = target.path("health").request().get();
                        assertEquals(200, response.getStatus());
                    }
                }
                """;

        Files.write(Paths.get(outputDir, "StressTests.java"), stressTest.getBytes());
    }

    /**
     * Generate NFR test configuration
     */
    private void generateNFRTestConfig(Map<String, Object> slaSpec, String outputDir) throws IOException {
        String config = """
                package com.example.nfr;
                
                import jakarta.ws.rs.client.Client;
                import jakarta.ws.rs.client.ClientBuilder;
                import jakarta.inject.Singleton;
                
                @Singleton
                public class NFRTestConfig {
                
                    private final Client client;
                    private final String baseUrl;
                
                    public NFRTestConfig() {
                        this.client = ClientBuilder.newClient();
                        this.baseUrl = "http://localhost:8080/api";
                    }
                
                    public Client getClient() {
                        return client;
                    }
                
                    public String getBaseUrl() {
                        return baseUrl;
                    }
                }
                """;

        Files.write(Paths.get(outputDir, "NFRTestConfig.java"), config.getBytes());
    }
}

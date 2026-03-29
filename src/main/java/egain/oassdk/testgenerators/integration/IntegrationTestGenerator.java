package egain.oassdk.testgenerators.integration;

import egain.oassdk.Util;
import egain.oassdk.config.TestConfig;
import egain.oassdk.core.Constants;
import egain.oassdk.core.exceptions.GenerationException;
import egain.oassdk.testgenerators.ConfigurableTestGenerator;
import egain.oassdk.testgenerators.TestGenerator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Integration test generator
 * Generates JUnit 5 integration tests for API endpoints based on OpenAPI specification
 * These tests make real HTTP calls to a running server
 */
public class IntegrationTestGenerator implements TestGenerator, ConfigurableTestGenerator {

    private TestConfig config;

    @Override
    public void generate(Map<String, Object> spec, String outputDir, TestConfig config, String testFramework) throws GenerationException {
        this.config = config;

        try {
            // Create output directory structure
            Path outputPath = Paths.get(outputDir, "integration");
            Files.createDirectories(outputPath);

            // Extract API information
            String apiTitle = getAPITitle(spec);

            // Get package name from additional properties or use default
            String basePackage = "com.example.api";
            if (config != null && config.getAdditionalProperties() != null) {
                Object packageNameObj = config.getAdditionalProperties().get("packageName");
                if (packageNameObj != null) {
                    basePackage = packageNameObj.toString();
                }
            }

            // Get base URL from servers
            String baseUrl = getBaseUrl(spec);

            // Generate test classes for each endpoint
            generateTestClasses(spec, outputPath.toString(), basePackage, apiTitle, baseUrl);

            // Generate test configuration
            generateTestConfiguration(outputPath.toString(), basePackage, baseUrl);

            // Generate test utilities
            generateTestUtilities(outputPath.toString(), basePackage);

        } catch (Exception e) {
            throw new GenerationException("Failed to generate integration tests: " + e.getMessage(), e);
        }
    }

    /**
     * Generate test classes for all endpoints
     */
    private void generateTestClasses(Map<String, Object> spec, String outputDir, String basePackage, String apiTitle, String baseUrl) throws IOException {
        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        if (paths == null || paths.isEmpty()) {
            return;
        }

        // Group operations by tag for better organization
        Map<String, List<OperationInfo>> operationsByTag = new HashMap<>();

        for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
            String path = pathEntry.getKey();
            Map<String, Object> pathItem = Util.asStringObjectMap(pathEntry.getValue());

            if (pathItem == null) continue;

            for (String method : Constants.HTTP_METHODS) {
                if (pathItem.containsKey(method)) {
                    Map<String, Object> operation = Util.asStringObjectMap(pathItem.get(method));
                    if (operation == null) continue;

                    OperationInfo opInfo = new OperationInfo();
                    opInfo.path = path;
                    opInfo.method = method;
                    opInfo.operation = operation;

                    // Get tag for grouping
                    String tag = getOperationTag(operation);
                    operationsByTag.computeIfAbsent(tag, k -> new ArrayList<>()).add(opInfo);
                }
            }
        }

        // Generate test class for each tag
        for (Map.Entry<String, List<OperationInfo>> tagEntry : operationsByTag.entrySet()) {
            String tag = tagEntry.getKey();
            List<OperationInfo> operations = tagEntry.getValue();

            String className = toClassName(tag) + "IntegrationTest";
            String packageDir = outputDir + "/" + basePackage.replace(".", "/");
            Files.createDirectories(Paths.get(packageDir));

            String testClassContent = generateTestClass(basePackage, className, tag, operations, spec, baseUrl);
            Files.write(Paths.get(packageDir, className + ".java"), testClassContent.getBytes());
        }
    }

    /**
     * Generate a test class for a group of operations
     */
    private String generateTestClass(String basePackage, String className, String tag, List<OperationInfo> operations, Map<String, Object> spec, String baseUrl) {
        StringBuilder sb = new StringBuilder();

        // Package declaration
        sb.append("package ").append(basePackage).append(";\n\n");

        // Imports
        sb.append("import org.junit.jupiter.api.*;\n");
        sb.append("import org.junit.jupiter.api.DisplayName;\n");
        sb.append("import org.junit.jupiter.api.Order;\n");
        sb.append("import org.junit.jupiter.params.ParameterizedTest;\n");
        sb.append("import org.junit.jupiter.params.provider.ValueSource;\n");
        sb.append("import static org.junit.jupiter.api.Assertions.*;\n\n");
        sb.append("import java.net.http.*;\n");
        sb.append("import java.net.URI;\n");
        sb.append("import java.time.Duration;\n");
        sb.append("import java.util.*;\n");
        sb.append("import java.util.concurrent.CompletableFuture;\n\n");

        // Class declaration
        sb.append("/**\n");
        sb.append(" * Integration tests for ").append(tag).append(" API endpoints\n");
        sb.append(" * Generated from OpenAPI specification\n");
        sb.append(" * \n");
        sb.append(" * These tests make real HTTP calls to a running server.\n");
        sb.append(" * Ensure the API server is running before executing these tests.\n");
        sb.append(" */\n");
        sb.append("@DisplayName(\"").append(tag).append(" Integration Tests\")\n");
        sb.append("@TestMethodOrder(MethodOrderer.OrderAnnotation.class)\n");
        sb.append("public class ").append(className).append(" {\n\n");

        // Constants
        sb.append("    private static final String BASE_URL = \"").append(baseUrl).append("\";\n");
        sb.append("    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);\n");
        sb.append("    private static HttpClient httpClient;\n\n");

        // Class-level setup
        sb.append("    @BeforeAll\n");
        sb.append("    static void setUpAll() {\n");
        sb.append("        httpClient = HttpClient.newBuilder()\n");
        sb.append("            .connectTimeout(REQUEST_TIMEOUT)\n");
        sb.append("            .build();\n");
        sb.append("        \n");
        sb.append("        // Verify server is reachable\n");
        sb.append("        try {\n");
        sb.append("            HttpRequest healthCheck = HttpRequest.newBuilder()\n");
        sb.append("                .uri(URI.create(BASE_URL))\n");
        sb.append("                .timeout(REQUEST_TIMEOUT)\n");
        sb.append("                .GET()\n");
        sb.append("                .build();\n");
        sb.append("            \n");
        sb.append("            HttpResponse<String> response = httpClient.send(healthCheck, HttpResponse.BodyHandlers.ofString());\n");
        sb.append("            System.out.println(\"Server health check: \" + response.statusCode());\n");
        sb.append("        } catch (Exception e) {\n");
        sb.append("            System.err.println(\"Warning: Could not reach server at \" + BASE_URL);\n");
        sb.append("            System.err.println(\"Some tests may fail if server is not running.\");\n");
        sb.append("        }\n");
        sb.append("    }\n\n");

        // Test setup
        sb.append("    @BeforeEach\n");
        sb.append("    void setUp() {\n");
        sb.append("        // Setup test data if needed\n");
        sb.append("    }\n\n");

        sb.append("    @AfterEach\n");
        sb.append("    void tearDown() {\n");
        sb.append("        // Cleanup test data if needed\n");
        sb.append("    }\n\n");

        // Generate test methods for each operation
        int order = 1;
        for (OperationInfo opInfo : operations) {
            generateTestMethods(sb, opInfo, spec, order);
            order += 10; // Increment by 10 to allow insertion between tests
        }

        // Helper methods
        sb.append("    /**\n");
        sb.append("     * Helper method to build request URI\n");
        sb.append("     */\n");
        sb.append("    private URI buildUri(String path, Map<String, String> queryParams) {\n");
        sb.append("        StringBuilder uriBuilder = new StringBuilder(BASE_URL).append(path);\n");
        sb.append("        if (queryParams != null && !queryParams.isEmpty()) {\n");
        sb.append("            uriBuilder.append(\"?\");\n");
        sb.append("            boolean first = true;\n");
        sb.append("            for (Map.Entry<String, String> entry : queryParams.entrySet()) {\n");
        sb.append("                if (!first) uriBuilder.append(\"&\");\n");
        sb.append("                uriBuilder.append(entry.getKey()).append(\"=\").append(entry.getValue());\n");
        sb.append("                first = false;\n");
        sb.append("            }\n");
        sb.append("        }\n");
        sb.append("        return URI.create(uriBuilder.toString());\n");
        sb.append("    }\n\n");

        sb.append("    /**\n");
        sb.append("     * Helper method to replace path parameters\n");
        sb.append("     */\n");
        sb.append("    private String replacePathParameters(String path, Map<String, String> pathParams) {\n");
        sb.append("        String result = path;\n");
        sb.append("        if (pathParams != null) {\n");
        sb.append("            for (Map.Entry<String, String> entry : pathParams.entrySet()) {\n");
        sb.append("                result = result.replace(\"{\" + entry.getKey() + \"}\", entry.getValue());\n");
        sb.append("            }\n");
        sb.append("        }\n");
        sb.append("        return result;\n");
        sb.append("    }\n\n");

        sb.append("    /**\n");
        sb.append("     * Helper method to send HTTP request and get response\n");
        sb.append("     */\n");
        sb.append("    private HttpResponse<String> sendRequest(HttpRequest request) throws Exception {\n");
        sb.append("        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());\n");
        sb.append("    }\n\n");

        generateAuthSetupFromSecuritySchemes(sb, spec);


        sb.append("}\n");

        return sb.toString();
    }

    /**
     * Generate test methods for an operation
     */
    private void generateTestMethods(StringBuilder sb, OperationInfo opInfo, Map<String, Object> spec, int order) {
        Map<String, Object> operation = opInfo.operation;
        String operationId = (String) operation.get("operationId");
        String summary = (String) operation.get("summary");
        String method = opInfo.method.toUpperCase();
        String path = opInfo.path;

        // Get operation name for test method
        String testMethodName = operationId != null
                ? toMethodName(operationId)
                : toMethodName(method + "_" + sanitizePath(path));

        // Extract parameters
        List<Map<String, Object>> parameters = operation.containsKey("parameters")
                ? Util.asStringObjectMapList(operation.get("parameters"))
                : new ArrayList<>();

        // Extract responses
        Map<String, Object> responses = operation.containsKey("responses")
                ? Util.asStringObjectMap(operation.get("responses"))
                : new HashMap<>();

        // Check for security requirements
        boolean requiresAuth = operation.containsKey("security") &&
                ((List<?>) operation.get("security")).size() > 0;

        // Test: Successful request
        sb.append("    @Test\n");
        sb.append("    @Order(").append(order).append(")\n");
        sb.append("    @DisplayName(\"").append(summary != null ? summary : method + " " + path).append(" - Successful Request\")\n");
        sb.append("    void test").append(capitalize(testMethodName)).append("_Success() throws Exception {\n");
        sb.append("        // Arrange\n");

        // Build path parameters
        Map<String, String> pathParams = new HashMap<>();
        Map<String, String> queryParams = new HashMap<>();
        for (Map<String, Object> param : parameters) {
            String paramName = (String) param.get("name");
            String paramIn = (String) param.get("in");
            if ("path".equals(paramIn)) {
                String example = getParameterExample(param);
                pathParams.put(paramName, example);
            } else if ("query".equals(paramIn)) {
                String example = getParameterExample(param);
                queryParams.put(paramName, example);
            }
        }

        if (!pathParams.isEmpty()) {
            sb.append("        Map<String, String> pathParams = new HashMap<>();\n");
            for (Map.Entry<String, String> entry : pathParams.entrySet()) {
                sb.append("        pathParams.put(\"").append(entry.getKey()).append("\", \"").append(entry.getValue()).append("\");\n");
            }
            sb.append("        String path = replacePathParameters(\"").append(path).append("\", pathParams);\n");
        } else {
            sb.append("        String path = \"").append(path).append("\";\n");
        }

        if (!queryParams.isEmpty()) {
            sb.append("        Map<String, String> queryParams = new HashMap<>();\n");
            for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                sb.append("        queryParams.put(\"").append(entry.getKey()).append("\", \"").append(entry.getValue()).append("\");\n");
            }
            sb.append("        URI uri = buildUri(path, queryParams);\n");
        } else {
            sb.append("        URI uri = URI.create(BASE_URL + path);\n");
        }

        sb.append("        \n");
        sb.append("        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()\n");
        sb.append("            .uri(uri)\n");
        sb.append("            .timeout(REQUEST_TIMEOUT)\n");
        sb.append("            .header(\"Accept\", \"application/json\");\n");

        if (requiresAuth) {
            sb.append("        \n");
            sb.append("        // Add authentication header\n");
            sb.append("        String token = getAuthToken();\n");
            sb.append("        if (token != null && !token.isEmpty()) {\n");
            sb.append("            requestBuilder.header(\"Authorization\", \"Bearer \" + token);\n");
            sb.append("        }\n");
        }

        if ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method)) {
            sb.append("        \n");
            sb.append("        // Set request body from schema\n");
            String requestBodyJson = generateRequestBodyFromSchema(operation, spec);
            sb.append("        String requestBody = \"").append(escapeJavaString(requestBodyJson)).append("\";\n");
            sb.append("        requestBuilder.header(\"Content-Type\", \"application/json\");\n");
            sb.append("        requestBuilder.method(\"").append(method).append("\", HttpRequest.BodyPublishers.ofString(requestBody));\n");
        } else {
            sb.append("        requestBuilder.GET();\n");
        }

        sb.append("        \n");
        sb.append("        HttpRequest request = requestBuilder.build();\n\n");

        sb.append("        // Act\n");
        sb.append("        HttpResponse<String> response = sendRequest(request);\n\n");

        sb.append("        // Assert\n");
        sb.append("        assertNotNull(response, \"Response should not be null\");\n");
        if (responses.containsKey("200")) {
            sb.append("        assertEquals(200, response.statusCode(), \"Expected 200 OK status\");\n");
        } else if (responses.containsKey("201")) {
            sb.append("        assertEquals(201, response.statusCode(), \"Expected 201 Created status\");\n");
        } else {
            sb.append("        assertTrue(response.statusCode() >= 200 && response.statusCode() < 300, \n");
            sb.append("            \"Expected successful status code, got: \" + response.statusCode());\n");
        }
        sb.append("        assertNotNull(response.body(), \"Response body should not be null\");\n");
        sb.append("        \n");
        sb.append("        // Validate response against schema\n");
        generateResponseSchemaValidation(sb, responses, spec);
        sb.append("    }\n\n");

        // Test: Invalid request (missing required parameters)
        for (Map<String, Object> param : parameters) {
            String paramName = (String) param.get("name");
            Boolean required = param.containsKey("required") ? (Boolean) param.get("required") : false;
            String paramIn = (String) param.get("in");

            if (required && "query".equals(paramIn)) {
                order += 1;
                sb.append("    @Test\n");
                sb.append("    @Order(").append(order).append(")\n");
                sb.append("    @DisplayName(\"").append(summary != null ? summary : method + " " + path)
                        .append(" - Missing Required Parameter: ").append(paramName).append("\")\n");
                sb.append("    void test").append(capitalize(testMethodName)).append("_MissingRequiredParam_").append(capitalize(paramName)).append("() throws Exception {\n");
                sb.append("        // Arrange - Missing required parameter: ").append(paramName).append("\n");

                // Build path without the missing query parameter
                if (!pathParams.isEmpty()) {
                    sb.append("        Map<String, String> pathParams = new HashMap<>();\n");
                    for (Map.Entry<String, String> entry : pathParams.entrySet()) {
                        sb.append("        pathParams.put(\"").append(entry.getKey()).append("\", \"").append(entry.getValue()).append("\");\n");
                    }
                    sb.append("        String path = replacePathParameters(\"").append(path).append("\", pathParams);\n");
                } else {
                    sb.append("        String path = \"").append(path).append("\";\n");
                }

                // Build query params without the required one
                Map<String, String> otherQueryParams = new HashMap<>();
                for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                    if (!entry.getKey().equals(paramName)) {
                        otherQueryParams.put(entry.getKey(), entry.getValue());
                    }
                }

                if (!otherQueryParams.isEmpty()) {
                    sb.append("        Map<String, String> queryParams = new HashMap<>();\n");
                    for (Map.Entry<String, String> entry : otherQueryParams.entrySet()) {
                        sb.append("        queryParams.put(\"").append(entry.getKey()).append("\", \"").append(entry.getValue()).append("\");\n");
                    }
                    sb.append("        URI uri = buildUri(path, queryParams);\n");
                } else {
                    sb.append("        URI uri = URI.create(BASE_URL + path);\n");
                }

                sb.append("        \n");
                sb.append("        HttpRequest request = HttpRequest.newBuilder()\n");
                sb.append("            .uri(uri)\n");
                sb.append("            .timeout(REQUEST_TIMEOUT)\n");
                sb.append("            .GET()\n");
                sb.append("            .header(\"Accept\", \"application/json\")\n");
                sb.append("            .build();\n\n");

                sb.append("        // Act\n");
                sb.append("        HttpResponse<String> response = sendRequest(request);\n\n");

                sb.append("        // Assert\n");
                sb.append("        assertEquals(400, response.statusCode(), \n");
                sb.append("            \"Expected 400 Bad Request for missing required parameter: ").append(paramName).append("\");\n");
                sb.append("    }\n\n");
            }
        }

        // Test: Unauthorized access (if security is required)
        if (requiresAuth) {
            order += 1;
            sb.append("    @Test\n");
            sb.append("    @Order(").append(order).append(")\n");
            sb.append("    @DisplayName(\"").append(summary != null ? summary : method + " " + path).append(" - Unauthorized Access\")\n");
            sb.append("    void test").append(capitalize(testMethodName)).append("_Unauthorized() throws Exception {\n");
            sb.append("        // Arrange - Request without authentication\n");

            if (!pathParams.isEmpty()) {
                sb.append("        Map<String, String> pathParams = new HashMap<>();\n");
                for (Map.Entry<String, String> entry : pathParams.entrySet()) {
                    sb.append("        pathParams.put(\"").append(entry.getKey()).append("\", \"").append(entry.getValue()).append("\");\n");
                }
                sb.append("        String path = replacePathParameters(\"").append(path).append("\", pathParams);\n");
            } else {
                sb.append("        String path = \"").append(path).append("\";\n");
            }

            if (!queryParams.isEmpty()) {
                sb.append("        Map<String, String> queryParams = new HashMap<>();\n");
                for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                    sb.append("        queryParams.put(\"").append(entry.getKey()).append("\", \"").append(entry.getValue()).append("\");\n");
                }
                sb.append("        URI uri = buildUri(path, queryParams);\n");
            } else {
                sb.append("        URI uri = URI.create(BASE_URL + path);\n");
            }

            sb.append("        \n");
            sb.append("        HttpRequest request = HttpRequest.newBuilder()\n");
            sb.append("            .uri(uri)\n");
            sb.append("            .timeout(REQUEST_TIMEOUT)\n");
            sb.append("            .GET()\n");
            sb.append("            .header(\"Accept\", \"application/json\")\n");
            sb.append("            // Intentionally omitting Authorization header\n");
            sb.append("            .build();\n\n");

            sb.append("        // Act\n");
            sb.append("        HttpResponse<String> response = sendRequest(request);\n\n");

            sb.append("        // Assert\n");
            sb.append("        assertEquals(401, response.statusCode(), \n");
            sb.append("            \"Expected 401 Unauthorized for request without authentication\");\n");
            sb.append("    }\n\n");
        }

        // Negative test cases for POST/PUT/PATCH: invalid request bodies
        if ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method)) {
            Map<String, Object> requestBodySchema = extractRequestBodySchema(opInfo.operation, spec);

            // Test: wrong types in request body
            order += 1;
            sb.append("    @Test\n");
            sb.append("    @Order(").append(order).append(")\n");
            sb.append("    @DisplayName(\"").append(summary != null ? summary : method + " " + path)
                    .append(" - Invalid Request Body: Wrong Types\")\n");
            sb.append("    void test").append(capitalize(testMethodName)).append("_InvalidTypes() throws Exception {\n");
            sb.append("        // Arrange - Send request body with wrong field types\n");

            if (!pathParams.isEmpty()) {
                sb.append("        Map<String, String> pathParams = new HashMap<>();\n");
                for (Map.Entry<String, String> entry : pathParams.entrySet()) {
                    sb.append("        pathParams.put(\"").append(entry.getKey()).append("\", \"").append(entry.getValue()).append("\");\n");
                }
                sb.append("        String path = replacePathParameters(\"").append(path).append("\", pathParams);\n");
            } else {
                sb.append("        String path = \"").append(path).append("\";\n");
            }

            if (!queryParams.isEmpty()) {
                sb.append("        Map<String, String> queryParams = new HashMap<>();\n");
                for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                    sb.append("        queryParams.put(\"").append(entry.getKey()).append("\", \"").append(entry.getValue()).append("\");\n");
                }
                sb.append("        URI uri = buildUri(path, queryParams);\n");
            } else {
                sb.append("        URI uri = URI.create(BASE_URL + path);\n");
            }

            String wrongTypesBody = generateWrongTypesRequestBody(requestBodySchema);
            sb.append("        String requestBody = \"").append(escapeJavaString(wrongTypesBody)).append("\";\n");
            sb.append("        \n");
            sb.append("        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()\n");
            sb.append("            .uri(uri)\n");
            sb.append("            .timeout(REQUEST_TIMEOUT)\n");
            sb.append("            .header(\"Accept\", \"application/json\")\n");
            sb.append("            .header(\"Content-Type\", \"application/json\");\n");

            if (requiresAuth) {
                sb.append("        String token = getAuthToken();\n");
                sb.append("        if (token != null && !token.isEmpty()) {\n");
                sb.append("            requestBuilder.header(\"Authorization\", \"Bearer \" + token);\n");
                sb.append("        }\n");
            }

            sb.append("        requestBuilder.method(\"").append(method).append("\", HttpRequest.BodyPublishers.ofString(requestBody));\n");
            sb.append("        HttpRequest request = requestBuilder.build();\n\n");
            sb.append("        // Act\n");
            sb.append("        HttpResponse<String> response = sendRequest(request);\n\n");
            sb.append("        // Assert - Expect 400 or 422 for invalid types\n");
            sb.append("        assertTrue(response.statusCode() == 400 || response.statusCode() == 422, \n");
            sb.append("            \"Expected 400 or 422 for invalid request body types, got: \" + response.statusCode());\n");
            sb.append("    }\n\n");

            // Test: missing required fields
            List<String> requiredFields = getRequiredFieldsFromSchema(requestBodySchema);
            if (!requiredFields.isEmpty()) {
                order += 1;
                sb.append("    @Test\n");
                sb.append("    @Order(").append(order).append(")\n");
                sb.append("    @DisplayName(\"").append(summary != null ? summary : method + " " + path)
                        .append(" - Invalid Request Body: Missing Required Fields\")\n");
                sb.append("    void test").append(capitalize(testMethodName)).append("_MissingRequiredFields() throws Exception {\n");
                sb.append("        // Arrange - Send request body missing required fields: ").append(String.join(", ", requiredFields)).append("\n");

                if (!pathParams.isEmpty()) {
                    sb.append("        Map<String, String> pathParams = new HashMap<>();\n");
                    for (Map.Entry<String, String> entry : pathParams.entrySet()) {
                        sb.append("        pathParams.put(\"").append(entry.getKey()).append("\", \"").append(entry.getValue()).append("\");\n");
                    }
                    sb.append("        String path = replacePathParameters(\"").append(path).append("\", pathParams);\n");
                } else {
                    sb.append("        String path = \"").append(path).append("\";\n");
                }

                if (!queryParams.isEmpty()) {
                    sb.append("        Map<String, String> queryParams = new HashMap<>();\n");
                    for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                        sb.append("        queryParams.put(\"").append(entry.getKey()).append("\", \"").append(entry.getValue()).append("\");\n");
                    }
                    sb.append("        URI uri = buildUri(path, queryParams);\n");
                } else {
                    sb.append("        URI uri = URI.create(BASE_URL + path);\n");
                }

                String missingFieldsBody = generateMissingRequiredFieldsBody(requestBodySchema);
                sb.append("        String requestBody = \"").append(escapeJavaString(missingFieldsBody)).append("\";\n");
                sb.append("        \n");
                sb.append("        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()\n");
                sb.append("            .uri(uri)\n");
                sb.append("            .timeout(REQUEST_TIMEOUT)\n");
                sb.append("            .header(\"Accept\", \"application/json\")\n");
                sb.append("            .header(\"Content-Type\", \"application/json\");\n");

                if (requiresAuth) {
                    sb.append("        String token = getAuthToken();\n");
                    sb.append("        if (token != null && !token.isEmpty()) {\n");
                    sb.append("            requestBuilder.header(\"Authorization\", \"Bearer \" + token);\n");
                    sb.append("        }\n");
                }

                sb.append("        requestBuilder.method(\"").append(method).append("\", HttpRequest.BodyPublishers.ofString(requestBody));\n");
                sb.append("        HttpRequest request = requestBuilder.build();\n\n");
                sb.append("        // Act\n");
                sb.append("        HttpResponse<String> response = sendRequest(request);\n\n");
                sb.append("        // Assert - Expect 400 or 422 for missing required fields\n");
                sb.append("        assertTrue(response.statusCode() == 400 || response.statusCode() == 422, \n");
                sb.append("            \"Expected 400 or 422 for missing required fields, got: \" + response.statusCode());\n");
                sb.append("    }\n\n");
            }
        }
    }

    /**
     * Generate test configuration file
     */
    private void generateTestConfiguration(String outputDir, String basePackage, String baseUrl) throws IOException {
        String configContent = "# Integration Test Configuration\n" +
                "# Generated from OpenAPI specification\n\n" +
                "base.url=" + baseUrl + "\n" +
                "timeout.seconds=30\n" +
                "package.name=" + basePackage + "\n\n" +
                "# Authentication (if required)\n" +
                "# api.token=${API_TOKEN}\n" +
                "# oauth.client.id=${OAUTH_CLIENT_ID}\n" +
                "# oauth.client.secret=${OAUTH_CLIENT_SECRET}\n";

        Files.write(Paths.get(outputDir, "test-config.properties"), configContent.getBytes());
    }

    /**
     * Generate test utilities
     */
    private void generateTestUtilities(String outputDir, String basePackage) throws IOException {
        String packageDir = outputDir + "/" + basePackage.replace(".", "/");
        Files.createDirectories(Paths.get(packageDir));

        // Generate IntegrationTestUtils class
        String utilsContent = generateIntegrationTestUtilsClass(basePackage);
        Files.write(Paths.get(packageDir, "IntegrationTestUtils.java"), utilsContent.getBytes());
    }

    /**
     * Generate IntegrationTestUtils helper class
     */
    private String generateIntegrationTestUtilsClass(String basePackage) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(basePackage).append(";\n\n");
        sb.append("import java.net.http.*;\n");
        sb.append("import java.net.URI;\n");
        sb.append("import java.time.Duration;\n");
        sb.append("import java.util.*;\n\n");
        sb.append("/**\n");
        sb.append(" * Utility class for integration tests\n");
        sb.append(" */\n");
        sb.append("public class IntegrationTestUtils {\n\n");
        sb.append("    /**\n");
        sb.append("     * Wait for server to be ready\n");
        sb.append("     */\n");
        sb.append("    public static boolean waitForServer(String baseUrl, Duration timeout) {\n");
        sb.append("        // TODO: Implement server readiness check\n");
        sb.append("        return true;\n");
        sb.append("    }\n\n");
        sb.append("    /**\n");
        sb.append("     * Generate test data based on schema\n");
        sb.append("     */\n");
        sb.append("    public static Object generateTestData(Map<String, Object> schema) {\n");
        sb.append("        // TODO: Implement test data generation based on schema\n");
        sb.append("        return null;\n");
        sb.append("    }\n\n");
        sb.append("    /**\n");
        sb.append("     * Validate response against schema\n");
        sb.append("     */\n");
        sb.append("    public static boolean validateResponse(Object response, Map<String, Object> schema) {\n");
        sb.append("        // TODO: Implement response validation against schema\n");
        sb.append("        return true;\n");
        sb.append("    }\n\n");
        sb.append("    /**\n");
        sb.append("     * Clean up test data\n");
        sb.append("     */\n");
        sb.append("    public static void cleanupTestData(String resourceId) {\n");
        sb.append("        // TODO: Implement test data cleanup\n");
        sb.append("    }\n\n");
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * Resolve a $ref reference in the OAS spec (e.g. "#/components/schemas/Pet")
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveRef(String ref, Map<String, Object> spec) {
        if (ref == null || !ref.startsWith("#/")) {
            return null;
        }
        String[] parts = ref.substring(2).split("/");
        Object current = spec;
        for (String part : parts) {
            if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(part);
            } else {
                return null;
            }
        }
        return current instanceof Map ? (Map<String, Object>) current : null;
    }

    /**
     * Extract the request body schema from an operation, resolving $ref if present
     */
    private Map<String, Object> extractRequestBodySchema(Map<String, Object> operation, Map<String, Object> spec) {
        Map<String, Object> requestBody = Util.asStringObjectMap(operation.get("requestBody"));
        if (requestBody == null) {
            return new HashMap<>();
        }
        // Resolve $ref on requestBody itself
        if (requestBody.containsKey("$ref")) {
            requestBody = resolveRef((String) requestBody.get("$ref"), spec);
            if (requestBody == null) return new HashMap<>();
        }
        Map<String, Object> content = Util.asStringObjectMap(requestBody.get("content"));
        if (content == null) return new HashMap<>();

        Map<String, Object> mediaType = Util.asStringObjectMap(content.get("application/json"));
        if (mediaType == null) {
            // Try first available media type
            for (Object v : content.values()) {
                mediaType = Util.asStringObjectMap(v);
                if (mediaType != null) break;
            }
        }
        if (mediaType == null) return new HashMap<>();

        Map<String, Object> schema = Util.asStringObjectMap(mediaType.get("schema"));
        if (schema == null) return new HashMap<>();

        // Resolve $ref on the schema
        if (schema.containsKey("$ref")) {
            Map<String, Object> resolved = resolveRef((String) schema.get("$ref"), spec);
            if (resolved != null) {
                schema = resolved;
            }
        }
        return schema;
    }

    /**
     * Generate a mock value for a given schema type
     */
    private String generateMockValue(String fieldName, String type, String format) {
        if (type == null) type = "string";
        switch (type) {
            case "string":
                if ("date-time".equals(format)) return "2024-01-15T10:30:00Z";
                if ("date".equals(format)) return "2024-01-15";
                if ("email".equals(format)) return "test@example.com";
                if ("uri".equals(format) || "url".equals(format)) return "https://example.com";
                if ("uuid".equals(format)) return "550e8400-e29b-41d4-a716-446655440000";
                if (fieldName != null) {
                    String lower = fieldName.toLowerCase();
                    if (lower.contains("name")) return "Test Name";
                    if (lower.contains("email")) return "test@example.com";
                    if (lower.contains("phone")) return "+1-555-0100";
                    if (lower.contains("url") || lower.contains("link")) return "https://example.com";
                    if (lower.contains("description")) return "Test description";
                    if (lower.contains("id")) return "test-id-123";
                    if (lower.contains("status")) return "active";
                }
                return "test-string";
            case "integer":
                if (fieldName != null && fieldName.toLowerCase().contains("age")) return "25";
                if (fieldName != null && fieldName.toLowerCase().contains("count")) return "10";
                return "1";
            case "number":
                return "1.5";
            case "boolean":
                return "true";
            default:
                return "test-value";
        }
    }

    /**
     * Generate a JSON request body string from the operation's request body schema
     */
    private String generateRequestBodyFromSchema(Map<String, Object> operation, Map<String, Object> spec) {
        Map<String, Object> schema = extractRequestBodySchema(operation, spec);
        return generateJsonFromSchema(schema, spec);
    }

    /**
     * Generate a JSON string from a schema definition
     */
    @SuppressWarnings("unchecked")
    private String generateJsonFromSchema(Map<String, Object> schema, Map<String, Object> spec) {
        if (schema == null || schema.isEmpty()) {
            return "{}";
        }

        // Resolve $ref if present
        if (schema.containsKey("$ref")) {
            Map<String, Object> resolved = resolveRef((String) schema.get("$ref"), spec);
            if (resolved != null) {
                schema = resolved;
            } else {
                return "{}";
            }
        }

        String type = (String) schema.get("type");
        if ("array".equals(type)) {
            Map<String, Object> items = Util.asStringObjectMap(schema.get("items"));
            if (items != null) {
                return "[" + generateJsonFromSchema(items, spec) + "]";
            }
            return "[]";
        }

        if (!"object".equals(type) && type != null && !"null".equals(type)) {
            return generateMockValue(null, type, (String) schema.get("format"));
        }

        Map<String, Object> properties = Util.asStringObjectMap(schema.get("properties"));
        if (properties == null || properties.isEmpty()) {
            return "{}";
        }

        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            if (!first) json.append(", ");
            first = false;

            String fieldName = entry.getKey();
            Map<String, Object> propSchema = Util.asStringObjectMap(entry.getValue());
            if (propSchema == null) continue;

            // Resolve $ref on property
            if (propSchema.containsKey("$ref")) {
                Map<String, Object> resolved = resolveRef((String) propSchema.get("$ref"), spec);
                if (resolved != null) propSchema = resolved;
            }

            String propType = (String) propSchema.get("type");
            String propFormat = (String) propSchema.get("format");

            json.append("\\\"").append(fieldName).append("\\\": ");

            if ("object".equals(propType) || propSchema.containsKey("properties")) {
                json.append(generateJsonFromSchema(propSchema, spec));
            } else if ("array".equals(propType)) {
                Map<String, Object> items = Util.asStringObjectMap(propSchema.get("items"));
                if (items != null) {
                    json.append("[").append(generateJsonFromSchema(items, spec)).append("]");
                } else {
                    json.append("[]");
                }
            } else if ("integer".equals(propType) || "number".equals(propType)) {
                json.append(generateMockValue(fieldName, propType, propFormat));
            } else if ("boolean".equals(propType)) {
                json.append(generateMockValue(fieldName, propType, propFormat));
            } else {
                json.append("\\\"").append(generateMockValue(fieldName, propType, propFormat)).append("\\\"");
            }
        }
        json.append("}");
        return json.toString();
    }

    /**
     * Escape a string for use inside a Java string literal
     */
    private String escapeJavaString(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * Generate response schema validation assertions
     */
    @SuppressWarnings("unchecked")
    private void generateResponseSchemaValidation(StringBuilder sb, Map<String, Object> responses, Map<String, Object> spec) {
        // Try 200 first, then 201, then first 2xx
        Map<String, Object> responseObj = null;
        String statusCode = null;
        for (String code : new String[]{"200", "201", "202", "204"}) {
            if (responses.containsKey(code)) {
                responseObj = Util.asStringObjectMap(responses.get(code));
                statusCode = code;
                break;
            }
        }
        if (responseObj == null) return;

        // Resolve $ref on response
        if (responseObj.containsKey("$ref")) {
            responseObj = resolveRef((String) responseObj.get("$ref"), spec);
            if (responseObj == null) return;
        }

        // 204 typically has no body
        if ("204".equals(statusCode)) return;

        Map<String, Object> content = Util.asStringObjectMap(responseObj.get("content"));
        if (content == null) return;

        Map<String, Object> mediaType = Util.asStringObjectMap(content.get("application/json"));
        if (mediaType == null) return;

        Map<String, Object> schema = Util.asStringObjectMap(mediaType.get("schema"));
        if (schema == null) return;

        // Resolve $ref on schema
        if (schema.containsKey("$ref")) {
            Map<String, Object> resolved = resolveRef((String) schema.get("$ref"), spec);
            if (resolved != null) schema = resolved;
        }

        sb.append("        // Validate response is valid JSON\n");
        sb.append("        String responseBody = response.body();\n");
        sb.append("        assertDoesNotThrow(() -> {\n");
        sb.append("            // Simple JSON validity check\n");
        sb.append("            String trimmed = responseBody.trim();\n");
        sb.append("            assertTrue(trimmed.startsWith(\"{\") || trimmed.startsWith(\"[\"),\n");
        sb.append("                \"Response should be valid JSON, got: \" + trimmed.substring(0, Math.min(50, trimmed.length())));\n");
        sb.append("        }, \"Response body should be valid JSON\");\n");

        String type = (String) schema.get("type");
        Map<String, Object> properties = Util.asStringObjectMap(schema.get("properties"));
        List<String> required = schema.containsKey("required") ? Util.asStringList(schema.get("required")) : null;

        if ("object".equals(type) && properties != null && !properties.isEmpty()) {
            // Validate required fields are present
            if (required != null && !required.isEmpty()) {
                sb.append("        // Validate required fields are present in response\n");
                for (String field : required) {
                    sb.append("        assertTrue(responseBody.contains(\"\\\"").append(field).append("\\\"\"),\n");
                    sb.append("            \"Response should contain required field '").append(field).append("'\");\n");
                }
            }

            // Validate field types for non-string fields
            sb.append("        // Validate field types in response\n");
            for (Map.Entry<String, Object> propEntry : properties.entrySet()) {
                Map<String, Object> propSchema = Util.asStringObjectMap(propEntry.getValue());
                if (propSchema == null) continue;
                if (propSchema.containsKey("$ref")) {
                    Map<String, Object> resolved = resolveRef((String) propSchema.get("$ref"), spec);
                    if (resolved != null) propSchema = resolved;
                }
                String propType = (String) propSchema.get("type");
                String fieldName = propEntry.getKey();

                if ("integer".equals(propType) || "number".equals(propType)) {
                    sb.append("        // Field '").append(fieldName).append("' should be a ").append(propType).append(" if present\n");
                    sb.append("        if (responseBody.contains(\"\\\"").append(fieldName).append("\\\"\")) {\n");
                    sb.append("            // Numeric field validation: value after key should not be a quoted string\n");
                    sb.append("            int idx = responseBody.indexOf(\"\\\"").append(fieldName).append("\\\"\");\n");
                    sb.append("            if (idx >= 0) {\n");
                    sb.append("                int colonIdx = responseBody.indexOf(':', idx);\n");
                    sb.append("                if (colonIdx >= 0) {\n");
                    sb.append("                    String afterColon = responseBody.substring(colonIdx + 1).trim();\n");
                    sb.append("                    assertFalse(afterColon.startsWith(\"\\\"\"),\n");
                    sb.append("                        \"Field '").append(fieldName).append("' should be a ").append(propType).append(", not a string\");\n");
                    sb.append("                }\n");
                    sb.append("            }\n");
                    sb.append("        }\n");
                } else if ("boolean".equals(propType)) {
                    sb.append("        // Field '").append(fieldName).append("' should be a boolean if present\n");
                    sb.append("        if (responseBody.contains(\"\\\"").append(fieldName).append("\\\"\")) {\n");
                    sb.append("            int idx = responseBody.indexOf(\"\\\"").append(fieldName).append("\\\"\");\n");
                    sb.append("            if (idx >= 0) {\n");
                    sb.append("                int colonIdx = responseBody.indexOf(':', idx);\n");
                    sb.append("                if (colonIdx >= 0) {\n");
                    sb.append("                    String afterColon = responseBody.substring(colonIdx + 1).trim();\n");
                    sb.append("                    assertTrue(afterColon.startsWith(\"true\") || afterColon.startsWith(\"false\") || afterColon.startsWith(\"null\"),\n");
                    sb.append("                        \"Field '").append(fieldName).append("' should be a boolean\");\n");
                    sb.append("                }\n");
                    sb.append("            }\n");
                    sb.append("        }\n");
                }
            }
        } else if ("array".equals(type)) {
            sb.append("        // Validate response is a JSON array\n");
            sb.append("        assertTrue(responseBody.trim().startsWith(\"[\"),\n");
            sb.append("            \"Response should be a JSON array\");\n");
        }
    }

    /**
     * Generate auth setup methods from OAS securitySchemes
     */
    @SuppressWarnings("unchecked")
    private void generateAuthSetupFromSecuritySchemes(StringBuilder sb, Map<String, Object> spec) {
        Map<String, Object> components = Util.asStringObjectMap(spec.get("components"));
        Map<String, Object> securitySchemes = components != null ? Util.asStringObjectMap(components.get("securitySchemes")) : null;

        if (securitySchemes == null || securitySchemes.isEmpty()) {
            // Fallback: simple bearer token from environment
            sb.append("    /**\n");
            sb.append("     * Helper method to get authentication token\n");
            sb.append("     */\n");
            sb.append("    private String getAuthToken() {\n");
            sb.append("        return System.getenv(\"API_TOKEN\");\n");
            sb.append("    }\n\n");
            return;
        }

        // Find the primary security scheme
        boolean generatedGetAuthToken = false;
        for (Map.Entry<String, Object> entry : securitySchemes.entrySet()) {
            String schemeName = entry.getKey();
            Map<String, Object> scheme = Util.asStringObjectMap(entry.getValue());
            if (scheme == null) continue;

            String schemeType = (String) scheme.get("type");
            String bearerFormat = (String) scheme.get("bearerFormat");
            String schemeStr = (String) scheme.get("scheme");
            String inField = (String) scheme.get("in");
            String paramName = (String) scheme.get("name");

            if ("http".equals(schemeType) && "bearer".equals(schemeStr)) {
                // Bearer token auth
                sb.append("    /**\n");
                sb.append("     * Helper method to get authentication token (Bearer");
                if (bearerFormat != null) sb.append(" - ").append(bearerFormat);
                sb.append(")\n");
                sb.append("     * Security scheme: ").append(schemeName).append("\n");
                sb.append("     */\n");
                sb.append("    private String getAuthToken() {\n");
                sb.append("        String token = System.getenv(\"API_BEARER_TOKEN\");\n");
                sb.append("        if (token == null || token.isEmpty()) {\n");
                sb.append("            token = System.getenv(\"API_TOKEN\");\n");
                sb.append("        }\n");
                sb.append("        return token;\n");
                sb.append("    }\n\n");
                generatedGetAuthToken = true;

            } else if ("apiKey".equals(schemeType)) {
                // API key auth
                String envVarName = "API_KEY";
                sb.append("    /**\n");
                sb.append("     * Helper method to get API key for authentication\n");
                sb.append("     * Security scheme: ").append(schemeName).append(" (apiKey in ").append(inField).append(")\n");
                sb.append("     */\n");
                if ("header".equals(inField)) {
                    sb.append("    private String getApiKeyHeaderName() {\n");
                    sb.append("        return \"").append(paramName != null ? paramName : "X-API-Key").append("\";\n");
                    sb.append("    }\n\n");
                    sb.append("    private String getApiKeyValue() {\n");
                    sb.append("        return System.getenv(\"").append(envVarName).append("\");\n");
                    sb.append("    }\n\n");
                } else if ("query".equals(inField)) {
                    sb.append("    private String getApiKeyParamName() {\n");
                    sb.append("        return \"").append(paramName != null ? paramName : "api_key").append("\";\n");
                    sb.append("    }\n\n");
                    sb.append("    private String getApiKeyValue() {\n");
                    sb.append("        return System.getenv(\"").append(envVarName).append("\");\n");
                    sb.append("    }\n\n");
                }
                if (!generatedGetAuthToken) {
                    sb.append("    private String getAuthToken() {\n");
                    sb.append("        return getApiKeyValue();\n");
                    sb.append("    }\n\n");
                    generatedGetAuthToken = true;
                }

            } else if ("oauth2".equals(schemeType)) {
                // OAuth2 auth
                Map<String, Object> flows = Util.asStringObjectMap(scheme.get("flows"));
                sb.append("    /**\n");
                sb.append("     * Helper method to retrieve OAuth2 token\n");
                sb.append("     * Security scheme: ").append(schemeName).append(" (oauth2)\n");
                sb.append("     */\n");
                sb.append("    private String getAuthToken() {\n");
                sb.append("        // First, try environment variable for pre-configured token\n");
                sb.append("        String token = System.getenv(\"OAUTH2_ACCESS_TOKEN\");\n");
                sb.append("        if (token != null && !token.isEmpty()) {\n");
                sb.append("            return token;\n");
                sb.append("        }\n");
                sb.append("        \n");
                sb.append("        // OAuth2 token retrieval stub\n");
                sb.append("        String clientId = System.getenv(\"OAUTH_CLIENT_ID\");\n");
                sb.append("        String clientSecret = System.getenv(\"OAUTH_CLIENT_SECRET\");\n");

                if (flows != null) {
                    Map<String, Object> clientCredentials = Util.asStringObjectMap(flows.get("clientCredentials"));
                    if (clientCredentials != null) {
                        String tokenUrl = (String) clientCredentials.get("tokenUrl");
                        sb.append("        String tokenUrl = \"").append(tokenUrl != null ? tokenUrl : "").append("\";\n");
                    } else {
                        Map<String, Object> authCode = Util.asStringObjectMap(flows.get("authorizationCode"));
                        if (authCode != null) {
                            String tokenUrl = (String) authCode.get("tokenUrl");
                            sb.append("        String tokenUrl = \"").append(tokenUrl != null ? tokenUrl : "").append("\";\n");
                        } else {
                            sb.append("        String tokenUrl = \"\"; // Configure OAuth2 token URL\n");
                        }
                    }
                } else {
                    sb.append("        String tokenUrl = \"\"; // Configure OAuth2 token URL\n");
                }

                sb.append("        \n");
                sb.append("        if (clientId == null || clientSecret == null || tokenUrl.isEmpty()) {\n");
                sb.append("            System.err.println(\"OAuth2 credentials not configured. Set OAUTH_CLIENT_ID, OAUTH_CLIENT_SECRET env vars.\");\n");
                sb.append("            return null;\n");
                sb.append("        }\n");
                sb.append("        \n");
                sb.append("        try {\n");
                sb.append("            String credentials = java.util.Base64.getEncoder().encodeToString(\n");
                sb.append("                (clientId + \":\" + clientSecret).getBytes());\n");
                sb.append("            HttpRequest tokenRequest = HttpRequest.newBuilder()\n");
                sb.append("                .uri(URI.create(tokenUrl))\n");
                sb.append("                .timeout(REQUEST_TIMEOUT)\n");
                sb.append("                .header(\"Authorization\", \"Basic \" + credentials)\n");
                sb.append("                .header(\"Content-Type\", \"application/x-www-form-urlencoded\")\n");
                sb.append("                .POST(HttpRequest.BodyPublishers.ofString(\"grant_type=client_credentials\"))\n");
                sb.append("                .build();\n");
                sb.append("            HttpResponse<String> tokenResponse = httpClient.send(tokenRequest, HttpResponse.BodyHandlers.ofString());\n");
                sb.append("            if (tokenResponse.statusCode() == 200) {\n");
                sb.append("                // Simple extraction of access_token from JSON response\n");
                sb.append("                String body = tokenResponse.body();\n");
                sb.append("                int start = body.indexOf(\"\\\"access_token\\\":\\\"\") + 16;\n");
                sb.append("                int end = body.indexOf('\"', start);\n");
                sb.append("                if (start > 15 && end > start) {\n");
                sb.append("                    return body.substring(start, end);\n");
                sb.append("                }\n");
                sb.append("            }\n");
                sb.append("            System.err.println(\"Failed to retrieve OAuth2 token: \" + tokenResponse.statusCode());\n");
                sb.append("        } catch (Exception e) {\n");
                sb.append("            System.err.println(\"Error retrieving OAuth2 token: \" + e.getMessage());\n");
                sb.append("        }\n");
                sb.append("        return null;\n");
                sb.append("    }\n\n");
                generatedGetAuthToken = true;
            }
        }

        // Fallback if no recognized scheme generated getAuthToken
        if (!generatedGetAuthToken) {
            sb.append("    /**\n");
            sb.append("     * Helper method to get authentication token\n");
            sb.append("     */\n");
            sb.append("    private String getAuthToken() {\n");
            sb.append("        return System.getenv(\"API_TOKEN\");\n");
            sb.append("    }\n\n");
        }
    }

    /**
     * Generate a request body with wrong field types for negative testing
     */
    @SuppressWarnings("unchecked")
    private String generateWrongTypesRequestBody(Map<String, Object> schema) {
        if (schema == null || schema.isEmpty()) {
            return "{\\\"invalidField\\\": \\\"not-a-number\\\"}";
        }

        Map<String, Object> properties = Util.asStringObjectMap(schema.get("properties"));
        if (properties == null || properties.isEmpty()) {
            return "{\\\"invalidField\\\": \\\"not-a-number\\\"}";
        }

        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            if (!first) json.append(", ");
            first = false;

            String fieldName = entry.getKey();
            Map<String, Object> propSchema = Util.asStringObjectMap(entry.getValue());
            String propType = propSchema != null ? (String) propSchema.get("type") : "string";

            json.append("\\\"").append(fieldName).append("\\\": ");

            // Generate wrong type value
            if ("string".equals(propType)) {
                json.append("12345"); // number instead of string
            } else if ("integer".equals(propType) || "number".equals(propType)) {
                json.append("\\\"not-a-number\\\""); // string instead of number
            } else if ("boolean".equals(propType)) {
                json.append("\\\"not-a-boolean\\\""); // string instead of boolean
            } else if ("array".equals(propType)) {
                json.append("\\\"not-an-array\\\""); // string instead of array
            } else if ("object".equals(propType)) {
                json.append("\\\"not-an-object\\\""); // string instead of object
            } else {
                json.append("null");
            }
        }
        json.append("}");
        return json.toString();
    }

    /**
     * Get the list of required fields from a schema
     */
    private List<String> getRequiredFieldsFromSchema(Map<String, Object> schema) {
        if (schema == null || !schema.containsKey("required")) {
            return new ArrayList<>();
        }
        List<String> required = Util.asStringList(schema.get("required"));
        return required != null ? required : new ArrayList<>();
    }

    /**
     * Generate a request body with required fields omitted for negative testing
     */
    @SuppressWarnings("unchecked")
    private String generateMissingRequiredFieldsBody(Map<String, Object> schema) {
        if (schema == null || schema.isEmpty()) {
            return "{}";
        }

        List<String> requiredFields = getRequiredFieldsFromSchema(schema);
        Map<String, Object> properties = Util.asStringObjectMap(schema.get("properties"));
        if (properties == null || properties.isEmpty()) {
            return "{}";
        }

        // Build a body with only non-required fields
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            String fieldName = entry.getKey();
            if (requiredFields.contains(fieldName)) {
                continue; // Skip required fields
            }
            if (!first) json.append(", ");
            first = false;

            Map<String, Object> propSchema = Util.asStringObjectMap(entry.getValue());
            String propType = propSchema != null ? (String) propSchema.get("type") : "string";
            String propFormat = propSchema != null ? (String) propSchema.get("format") : null;

            json.append("\\\"").append(fieldName).append("\\\": ");
            if ("integer".equals(propType) || "number".equals(propType)) {
                json.append(generateMockValue(fieldName, propType, propFormat));
            } else if ("boolean".equals(propType)) {
                json.append(generateMockValue(fieldName, propType, propFormat));
            } else {
                json.append("\\\"").append(generateMockValue(fieldName, propType, propFormat)).append("\\\"");
            }
        }
        json.append("}");
        return json.toString();
    }

    // Helper methods
    private String getAPITitle(Map<String, Object> spec) {
        Map<String, Object> info = Util.asStringObjectMap(spec.get("info"));
        return info != null ? (String) info.get("title") : "API";
    }

    private String getBaseUrl(Map<String, Object> spec) {
        if (spec.containsKey("servers")) {
            List<Map<String, Object>> servers = Util.asStringObjectMapList(spec.get("servers"));
            if (servers != null && !servers.isEmpty()) {
                String url = (String) servers.get(0).get("url");
                if (url != null) {
                    return url;
                }
            }
        }
        return "http://localhost:8080";
    }

    private String getOperationTag(Map<String, Object> operation) {
        List<String> tags = Util.asStringList(operation.get("tags"));
        return tags != null && !tags.isEmpty() ? tags.get(0) : "Default";
    }

    private String getParameterExample(Map<String, Object> param) {
        if (param.containsKey("example")) {
            return String.valueOf(param.get("example"));
        }
        if (param.containsKey("schema")) {
            Map<String, Object> schema = Util.asStringObjectMap(param.get("schema"));
            if (schema.containsKey("example")) {
                return String.valueOf(schema.get("example"));
            }
            String type = (String) schema.get("type");
            if ("string".equals(type)) {
                return "test-value";
            } else if ("integer".equals(type)) {
                return "123";
            } else if ("boolean".equals(type)) {
                return "true";
            }
        }
        return "example";
    }

    private String toClassName(String name) {
        if (name == null || name.isEmpty()) {
            return "Default";
        }
        String[] parts = name.split("[^a-zA-Z0-9]+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    sb.append(part.substring(1));
                }
            }
        }
        return sb.toString();
    }

    private String toMethodName(String name) {
        if (name == null || name.isEmpty()) {
            return "test";
        }
        String[] parts = name.split("[^a-zA-Z0-9]+");
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String part : parts) {
            if (!part.isEmpty()) {
                if (first) {
                    sb.append(Character.toLowerCase(part.charAt(0)));
                    if (part.length() > 1) {
                        sb.append(part.substring(1));
                    }
                    first = false;
                } else {
                    sb.append(Character.toUpperCase(part.charAt(0)));
                    if (part.length() > 1) {
                        sb.append(part.substring(1));
                    }
                }
            }
        }
        return sb.toString();
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return Character.toUpperCase(str.charAt(0)) + (str.length() > 1 ? str.substring(1) : "");
    }

    private String sanitizePath(String path) {
        return path.replaceAll("[^a-zA-Z0-9]", "_");
    }

    /**
     * Inner class to hold operation information
     */
    private static class OperationInfo {
        String path;
        String method;
        Map<String, Object> operation;
    }

    @Override
    public String getName() {
        return "Integration Test Generator";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getTestType() {
        return "integration";
    }

    @Override
    public void setConfig(TestConfig config) {
        this.config = config;
    }

    @Override
    public TestConfig getConfig() {
        return this.config;
    }
}

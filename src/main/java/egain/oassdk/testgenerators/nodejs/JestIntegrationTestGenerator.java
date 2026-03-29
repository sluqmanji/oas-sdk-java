package egain.oassdk.testgenerators.nodejs;

import egain.oassdk.Util;
import egain.oassdk.core.Constants;
import egain.oassdk.config.TestConfig;
import egain.oassdk.core.exceptions.GenerationException;
import egain.oassdk.testgenerators.ConfigurableTestGenerator;
import egain.oassdk.testgenerators.TestGenerator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * NodeJS Jest integration test generator
 * Generates Jest integration tests for API endpoints based on OpenAPI specification
 * These tests make real HTTP calls to a running server
 */
public class JestIntegrationTestGenerator implements TestGenerator, ConfigurableTestGenerator {

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

            // Get base URL from servers
            String baseUrl = getBaseUrl(spec);

            // Generate test files for each endpoint
            generateTestFiles(spec, outputPath.toString(), apiTitle, baseUrl);

            // Generate Jest configuration
            generateJestConfig(outputPath.toString());

            // Generate test setup
            generateTestSetup(outputPath.toString(), baseUrl);

            // Generate package.json
            generatePackageJson(outputPath.toString());

        } catch (Exception e) {
            throw new GenerationException("Failed to generate Jest integration tests: " + e.getMessage(), e);
        }
    }

    /**
     * Generate test files for all endpoints
     */
    private void generateTestFiles(Map<String, Object> spec, String outputDir, String apiTitle, String baseUrl) throws IOException {
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

            String[] methods = Constants.HTTP_METHODS;
            for (String method : methods) {
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

        // Generate test file for each tag
        for (Map.Entry<String, List<OperationInfo>> tagEntry : operationsByTag.entrySet()) {
            String tag = tagEntry.getKey();
            List<OperationInfo> operations = tagEntry.getValue();

            String fileName = toKebabCase(tag) + ".integration.test.js";

            String testFileContent = generateTestFile(fileName, tag, operations, spec, baseUrl);
            Files.write(Paths.get(outputDir, fileName), testFileContent.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Generate a test file for a group of operations
     */
    private String generateTestFile(String fileName, String tag, List<OperationInfo> operations, Map<String, Object> spec, String baseUrl) {
        StringBuilder sb = new StringBuilder();

        // File header
        sb.append("/**\n");
        sb.append(" * Integration tests for ").append(tag).append(" API endpoints\n");
        sb.append(" * Generated from OpenAPI specification\n");
        sb.append(" * \n");
        sb.append(" * These tests make real HTTP calls to a running server.\n");
        sb.append(" * Ensure the API server is running before executing these tests.\n");
        sb.append(" */\n\n");

        // Imports
        sb.append("const axios = require('axios');\n");
        sb.append("const { setupTestEnvironment } = require('./setup');\n\n");

        // Constants
        sb.append("const BASE_URL = process.env.API_BASE_URL || '").append(baseUrl).append("';\n");
        sb.append("const REQUEST_TIMEOUT = 30000;\n\n");

        // Helper functions
        sb.append("/**\n");
        sb.append(" * Build complete URL with query parameters\n");
        sb.append(" */\n");
        sb.append("function buildUrl(path, queryParams = null) {\n");
        sb.append("  let url = `${BASE_URL}${path}`;\n");
        sb.append("  if (queryParams) {\n");
        sb.append("    const params = Object.entries(queryParams)\n");
        sb.append("      .map(([key, value]) => `${key}=${value}`)\n");
        sb.append("      .join('&');\n");
        sb.append("    url = `${url}?${params}`;\n");
        sb.append("  }\n");
        sb.append("  return url;\n");
        sb.append("}\n\n");

        sb.append("/**\n");
        sb.append(" * Replace path parameters in URL\n");
        sb.append(" */\n");
        sb.append("function replacePathParams(path, pathParams) {\n");
        sb.append("  let result = path;\n");
        sb.append("  Object.entries(pathParams).forEach(([key, value]) => {\n");
        sb.append("    result = result.replace(`{${key}}`, value);\n");
        sb.append("  });\n");
        sb.append("  return result;\n");
        sb.append("}\n\n");

        sb.append("/**\n");
        sb.append(" * Get authentication token from environment\n");
        sb.append(" */\n");
        sb.append("function getAuthToken() {\n");
        sb.append("  return process.env.API_TOKEN;\n");
        sb.append("}\n\n");

        // Test suite
        sb.append("describe('").append(tag).append(" Integration Tests', () => {\n");
        sb.append("  let axiosInstance;\n\n");

        sb.append("  beforeAll(async () => {\n");
        sb.append("    await setupTestEnvironment();\n");
        sb.append("    axiosInstance = axios.create({\n");
        sb.append("      timeout: REQUEST_TIMEOUT,\n");
        sb.append("      headers: {\n");
        sb.append("        'Accept': 'application/json'\n");
        sb.append("      }\n");
        sb.append("    });\n");
        sb.append("  });\n\n");

        // Generate test cases for each operation
        for (OperationInfo opInfo : operations) {
            generateTestCases(sb, opInfo, spec);
        }

        sb.append("});\n");

        return sb.toString();
    }

    /**
     * Generate test cases for an operation
     */
    private void generateTestCases(StringBuilder sb, OperationInfo opInfo, Map<String, Object> spec) {
        Map<String, Object> operation = opInfo.operation;
        String operationId = (String) operation.get("operationId");
        String summary = (String) operation.get("summary");
        String method = opInfo.method.toLowerCase();
        String path = opInfo.path;

        // Get operation name for test
        String testName = operationId != null
                ? operationId
                : method + " " + path;

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

        // Build path and query parameters
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

        // Test: Successful request
        sb.append("  describe('").append(summary != null ? summary : testName).append("', () => {\n");
        sb.append("    test('should return success on valid request', async () => {\n");
        sb.append("      // Arrange\n");

        if (!pathParams.isEmpty()) {
            sb.append("      const pathParams = {\n");
            for (Map.Entry<String, String> entry : pathParams.entrySet()) {
                sb.append("        ").append(entry.getKey()).append(": '").append(entry.getValue()).append("',\n");
            }
            sb.append("      };\n");
            sb.append("      const path = replacePathParams('").append(path).append("', pathParams);\n");
        } else {
            sb.append("      const path = '").append(path).append("';\n");
        }

        if (!queryParams.isEmpty()) {
            sb.append("      const queryParams = {\n");
            for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                sb.append("        ").append(entry.getKey()).append(": '").append(entry.getValue()).append("',\n");
            }
            sb.append("      };\n");
            sb.append("      const url = buildUrl(path, queryParams);\n");
        } else {
            sb.append("      const url = `${BASE_URL}${path}`;\n");
        }

        sb.append("      \n");
        sb.append("      const config = {\n");
        sb.append("        headers: { 'Accept': 'application/json' }\n");
        sb.append("      };\n");

        if (requiresAuth) {
            sb.append("      \n");
            sb.append("      const token = getAuthToken();\n");
            sb.append("      if (token) {\n");
            sb.append("        config.headers['Authorization'] = `Bearer ${token}`;\n");
            sb.append("      }\n");
        }

        sb.append("      \n");
        sb.append("      // Act\n");
        if ("post".equals(method) || "put".equals(method) || "patch".equals(method)) {
            sb.append("      const requestBody = {}; // TODO: Generate request body from schema\n");
            sb.append("      config.headers['Content-Type'] = 'application/json';\n");
            sb.append("      const response = await axiosInstance.").append(method).append("(url, requestBody, config);\n");
        } else {
            sb.append("      const response = await axiosInstance.").append(method).append("(url, config);\n");
        }

        sb.append("      \n");
        sb.append("      // Assert\n");
        sb.append("      expect(response).toBeDefined();\n");
        if (responses.containsKey("200")) {
            sb.append("      expect(response.status).toBe(200);\n");
        } else if (responses.containsKey("201")) {
            sb.append("      expect(response.status).toBe(201);\n");
        } else {
            sb.append("      expect(response.status).toBeGreaterThanOrEqual(200);\n");
            sb.append("      expect(response.status).toBeLessThan(300);\n");
        }
        sb.append("    });\n\n");

        // Test: Missing required parameters
        for (Map<String, Object> param : parameters) {
            String paramName = (String) param.get("name");
            Boolean required = param.containsKey("required") ? (Boolean) param.get("required") : false;
            String paramIn = (String) param.get("in");

            if (required && "query".equals(paramIn)) {
                sb.append("    test('should return 400 when missing required parameter: ").append(paramName).append("', async () => {\n");
                sb.append("      // Arrange - Missing required parameter: ").append(paramName).append("\n");

                if (!pathParams.isEmpty()) {
                    sb.append("      const pathParams = {\n");
                    for (Map.Entry<String, String> entry : pathParams.entrySet()) {
                        sb.append("        ").append(entry.getKey()).append(": '").append(entry.getValue()).append("',\n");
                    }
                    sb.append("      };\n");
                    sb.append("      const path = replacePathParams('").append(path).append("', pathParams);\n");
                } else {
                    sb.append("      const path = '").append(path).append("';\n");
                }

                // Build query params without the required one
                Map<String, String> otherQueryParams = new HashMap<>();
                for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                    if (!entry.getKey().equals(paramName)) {
                        otherQueryParams.put(entry.getKey(), entry.getValue());
                    }
                }

                if (!otherQueryParams.isEmpty()) {
                    sb.append("      const queryParams = {\n");
                    for (Map.Entry<String, String> entry : otherQueryParams.entrySet()) {
                        sb.append("        ").append(entry.getKey()).append(": '").append(entry.getValue()).append("',\n");
                    }
                    sb.append("      };\n");
                    sb.append("      const url = buildUrl(path, queryParams);\n");
                } else {
                    sb.append("      const url = `${BASE_URL}${path}`;\n");
                }

                sb.append("      \n");
                sb.append("      // Act & Assert\n");
                sb.append("      try {\n");
                sb.append("        await axiosInstance.").append(method).append("(url);\n");
                sb.append("        fail('Expected request to fail with 400');\n");
                sb.append("      } catch (error) {\n");
                sb.append("        expect(error.response.status).toBe(400);\n");
                sb.append("      }\n");
                sb.append("    });\n\n");
            }
        }

        // Test: Unauthorized access (if security is required)
        if (requiresAuth) {
            sb.append("    test('should return 401 when missing authentication', async () => {\n");
            sb.append("      // Arrange - Request without authentication\n");

            if (!pathParams.isEmpty()) {
                sb.append("      const pathParams = {\n");
                for (Map.Entry<String, String> entry : pathParams.entrySet()) {
                    sb.append("        ").append(entry.getKey()).append(": '").append(entry.getValue()).append("',\n");
                }
                sb.append("      };\n");
                sb.append("      const path = replacePathParams('").append(path).append("', pathParams);\n");
            } else {
                sb.append("      const path = '").append(path).append("';\n");
            }

            if (!queryParams.isEmpty()) {
                sb.append("      const queryParams = {\n");
                for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                    sb.append("        ").append(entry.getKey()).append(": '").append(entry.getValue()).append("',\n");
                }
                sb.append("      };\n");
                sb.append("      const url = buildUrl(path, queryParams);\n");
            } else {
                sb.append("      const url = `${BASE_URL}${path}`;\n");
            }

            sb.append("      \n");
            sb.append("      // Act & Assert\n");
            sb.append("      try {\n");
            sb.append("        await axiosInstance.").append(method).append("(url);\n");
            sb.append("        fail('Expected request to fail with 401');\n");
            sb.append("      } catch (error) {\n");
            sb.append("        expect(error.response.status).toBe(401);\n");
            sb.append("      }\n");
            sb.append("    });\n\n");
        }

        sb.append("  });\n\n");
    }

    /**
     * Generate Jest configuration
     */
    private void generateJestConfig(String outputDir) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("module.exports = {\n");
        sb.append("  testEnvironment: 'node',\n");
        sb.append("  testMatch: ['**/*.integration.test.js'],\n");
        sb.append("  testTimeout: 30000,\n");
        sb.append("  verbose: true,\n");
        sb.append("  collectCoverage: true,\n");
        sb.append("  coverageDirectory: 'coverage',\n");
        sb.append("  coverageReporters: ['text', 'lcov', 'html'],\n");
        sb.append("};\n");

        Files.write(Paths.get(outputDir, "jest.config.js"), sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generate test setup file
     */
    private void generateTestSetup(String outputDir, String baseUrl) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("/**\n");
        sb.append(" * Test setup and utilities\n");
        sb.append(" */\n\n");
        sb.append("const axios = require('axios');\n\n");

        sb.append("/**\n");
        sb.append(" * Setup test environment and check server health\n");
        sb.append(" */\n");
        sb.append("async function setupTestEnvironment() {\n");
        sb.append("  const baseUrl = process.env.API_BASE_URL || '").append(baseUrl).append("';\n");
        sb.append("  try {\n");
        sb.append("    const response = await axios.get(baseUrl, { timeout: 5000 });\n");
        sb.append("    console.log(`Server health check: ${response.status}`);\n");
        sb.append("  } catch (error) {\n");
        sb.append("    console.warn(`Warning: Could not reach server at ${baseUrl}`);\n");
        sb.append("    console.warn('Some tests may fail if server is not running.');\n");
        sb.append("  }\n");
        sb.append("}\n\n");

        sb.append("module.exports = {\n");
        sb.append("  setupTestEnvironment\n");
        sb.append("};\n");

        Files.write(Paths.get(outputDir, "setup.js"), sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generate package.json
     */
    private void generatePackageJson(String outputDir) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"name\": \"api-integration-tests\",\n");
        sb.append("  \"version\": \"1.0.0\",\n");
        sb.append("  \"description\": \"Integration tests for API\",\n");
        sb.append("  \"scripts\": {\n");
        sb.append("    \"test\": \"jest\",\n");
        sb.append("    \"test:watch\": \"jest --watch\",\n");
        sb.append("    \"test:coverage\": \"jest --coverage\"\n");
        sb.append("  },\n");
        sb.append("  \"devDependencies\": {\n");
        sb.append("    \"jest\": \"^29.0.0\",\n");
        sb.append("    \"axios\": \"^1.4.0\"\n");
        sb.append("  }\n");
        sb.append("}\n");

        Files.write(Paths.get(outputDir, "package.json"), sb.toString().getBytes(StandardCharsets.UTF_8));
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

    private String toKebabCase(String name) {
        if (name == null || name.isEmpty()) {
            return "default";
        }
        // Convert PascalCase/camelCase to kebab-case
        String result = name.replaceAll("([a-z])([A-Z])", "$1-$2");
        // Replace non-alphanumeric characters with hyphens
        result = result.replaceAll("[^a-zA-Z0-9]+", "-");
        return result.toLowerCase(Locale.ROOT);
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
        return "Jest Integration Test Generator";
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


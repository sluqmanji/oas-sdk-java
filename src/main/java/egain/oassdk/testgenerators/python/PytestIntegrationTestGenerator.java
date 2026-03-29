package egain.oassdk.testgenerators.python;

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
 * Python pytest integration test generator
 * Generates pytest integration tests for API endpoints based on OpenAPI specification
 * These tests make real HTTP calls to a running server
 */
public class PytestIntegrationTestGenerator implements TestGenerator, ConfigurableTestGenerator {

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

            // Generate test classes for each endpoint
            generateTestModules(spec, outputPath.toString(), apiTitle, baseUrl);

            // Generate test configuration
            generateTestConfiguration(outputPath.toString(), baseUrl);

            // Generate conftest.py for pytest fixtures
            generateConftest(outputPath.toString(), baseUrl);

            // Generate requirements.txt
            generateRequirements(outputPath.toString());

        } catch (Exception e) {
            throw new GenerationException("Failed to generate pytest integration tests: " + e.getMessage(), e);
        }
    }

    /**
     * Generate test modules for all endpoints
     */
    private void generateTestModules(Map<String, Object> spec, String outputDir, String apiTitle, String baseUrl) throws IOException {
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

        // Create __init__.py to make it a package
        Files.write(Paths.get(outputDir, "__init__.py"), "".getBytes(StandardCharsets.UTF_8));

        // Generate test module for each tag
        for (Map.Entry<String, List<OperationInfo>> tagEntry : operationsByTag.entrySet()) {
            String tag = tagEntry.getKey();
            List<OperationInfo> operations = tagEntry.getValue();

            String moduleName = "test_" + toSnakeCase(tag) + "_integration";

            String testModuleContent = generateTestModule(moduleName, tag, operations, spec, baseUrl);
            Files.write(Paths.get(outputDir, moduleName + ".py"), testModuleContent.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Generate a test module for a group of operations
     */
    private String generateTestModule(String moduleName, String tag, List<OperationInfo> operations, Map<String, Object> spec, String baseUrl) {
        StringBuilder sb = new StringBuilder();

        // Module docstring
        sb.append("\"\"\"Integration tests for ").append(tag).append(" API endpoints\n\n");
        sb.append("Generated from OpenAPI specification\n");
        sb.append("These tests make real HTTP calls to a running server.\n");
        sb.append("Ensure the API server is running before executing these tests.\n");
        sb.append("\"\"\"\n\n");

        // Imports
        sb.append("import pytest\n");
        sb.append("import requests\n");
        sb.append("from typing import Dict, Any, Optional\n");
        sb.append("import os\n\n");

        // Constants
        sb.append("BASE_URL = os.getenv('API_BASE_URL', '").append(baseUrl).append("')\n");
        sb.append("REQUEST_TIMEOUT = 30\n\n");

        // Helper functions
        sb.append("def build_url(path: str, query_params: Optional[Dict[str, Any]] = None) -> str:\n");
        sb.append("    \"\"\"Build complete URL with query parameters\"\"\"\n");
        sb.append("    url = f\"{BASE_URL}{path}\"\n");
        sb.append("    if query_params:\n");
        sb.append("        params = '&'.join(f\"{k}={v}\" for k, v in query_params.items())\n");
        sb.append("        url = f\"{url}?{params}\"\n");
        sb.append("    return url\n\n\n");

        sb.append("def replace_path_params(path: str, path_params: Dict[str, str]) -> str:\n");
        sb.append("    \"\"\"Replace path parameters in URL\"\"\"\n");
        sb.append("    result = path\n");
        sb.append("    for key, value in path_params.items():\n");
        sb.append("        result = result.replace(f\"{{{key}}}\", value)\n");
        sb.append("    return result\n\n\n");

        sb.append("def get_auth_token() -> Optional[str]:\n");
        sb.append("    \"\"\"Get authentication token from environment\"\"\"\n");
        sb.append("    return os.getenv('API_TOKEN')\n\n\n");

        // Test class
        sb.append("class Test").append(toPascalCase(tag)).append("Integration:\n");
        sb.append("    \"\"\"Integration tests for ").append(tag).append(" endpoints\"\"\"\n\n");

        // Generate test methods for each operation
        for (OperationInfo opInfo : operations) {
            generateTestMethods(sb, opInfo, spec);
        }

        return sb.toString();
    }

    /**
     * Generate test methods for an operation
     */
    private void generateTestMethods(StringBuilder sb, OperationInfo opInfo, Map<String, Object> spec) {
        Map<String, Object> operation = opInfo.operation;
        String operationId = (String) operation.get("operationId");
        String summary = (String) operation.get("summary");
        String method = opInfo.method.toLowerCase();
        String path = opInfo.path;

        // Get operation name for test method
        String testMethodName = operationId != null
                ? toSnakeCase(operationId)
                : toSnakeCase(method + "_" + sanitizePath(path));

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
        sb.append("    def test_").append(testMethodName).append("_success(self):\n");
        sb.append("        \"\"\"Test ").append(summary != null ? summary : method.toUpperCase() + " " + path).append(" - Successful Request\"\"\"\n");
        sb.append("        # Arrange\n");

        if (!pathParams.isEmpty()) {
            sb.append("        path_params = {\n");
            for (Map.Entry<String, String> entry : pathParams.entrySet()) {
                sb.append("            '").append(entry.getKey()).append("': '").append(entry.getValue()).append("',\n");
            }
            sb.append("        }\n");
            sb.append("        path = replace_path_params('").append(path).append("', path_params)\n");
        } else {
            sb.append("        path = '").append(path).append("'\n");
        }

        if (!queryParams.isEmpty()) {
            sb.append("        query_params = {\n");
            for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                sb.append("            '").append(entry.getKey()).append("': '").append(entry.getValue()).append("',\n");
            }
            sb.append("        }\n");
            sb.append("        url = build_url(path, query_params)\n");
        } else {
            sb.append("        url = f\"{BASE_URL}{path}\"\n");
        }

        sb.append("        \n");
        sb.append("        headers = {'Accept': 'application/json'}\n");

        if (requiresAuth) {
            sb.append("        token = get_auth_token()\n");
            sb.append("        if token:\n");
            sb.append("            headers['Authorization'] = f'Bearer {token}'\n");
            sb.append("        \n");
        }

        if ("post".equals(method) || "put".equals(method) || "patch".equals(method)) {
            sb.append("        headers['Content-Type'] = 'application/json'\n");
            sb.append("        request_body = {}  # TODO: Generate request body from schema\n");
            sb.append("        \n");
            sb.append("        # Act\n");
            sb.append("        response = requests.").append(method).append("(\n");
            sb.append("            url,\n");
            sb.append("            json=request_body,\n");
            sb.append("            headers=headers,\n");
            sb.append("            timeout=REQUEST_TIMEOUT\n");
            sb.append("        )\n");
        } else {
            sb.append("        \n");
            sb.append("        # Act\n");
            sb.append("        response = requests.").append(method).append("(\n");
            sb.append("            url,\n");
            sb.append("            headers=headers,\n");
            sb.append("            timeout=REQUEST_TIMEOUT\n");
            sb.append("        )\n");
        }

        sb.append("        \n");
        sb.append("        # Assert\n");
        sb.append("        assert response is not None, 'Response should not be None'\n");
        if (responses.containsKey("200")) {
            sb.append("        assert response.status_code == 200, f'Expected 200 OK, got {response.status_code}'\n");
        } else if (responses.containsKey("201")) {
            sb.append("        assert response.status_code == 201, f'Expected 201 Created, got {response.status_code}'\n");
        } else {
            sb.append("        assert 200 <= response.status_code < 300, f'Expected success status, got {response.status_code}'\n");
        }
        sb.append("        \n\n");

        // Test: Missing required parameters
        for (Map<String, Object> param : parameters) {
            String paramName = (String) param.get("name");
            Boolean required = param.containsKey("required") ? (Boolean) param.get("required") : false;
            String paramIn = (String) param.get("in");

            if (required && "query".equals(paramIn)) {
                sb.append("    def test_").append(testMethodName).append("_missing_").append(toSnakeCase(paramName)).append("(self):\n");
                sb.append("        \"\"\"Test ").append(summary != null ? summary : method.toUpperCase() + " " + path)
                        .append(" - Missing Required Parameter: ").append(paramName).append("\"\"\"\n");
                sb.append("        # Arrange - Missing required parameter: ").append(paramName).append("\n");

                if (!pathParams.isEmpty()) {
                    sb.append("        path_params = {\n");
                    for (Map.Entry<String, String> entry : pathParams.entrySet()) {
                        sb.append("            '").append(entry.getKey()).append("': '").append(entry.getValue()).append("',\n");
                    }
                    sb.append("        }\n");
                    sb.append("        path = replace_path_params('").append(path).append("', path_params)\n");
                } else {
                    sb.append("        path = '").append(path).append("'\n");
                }

                // Build query params without the required one
                Map<String, String> otherQueryParams = new HashMap<>();
                for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                    if (!entry.getKey().equals(paramName)) {
                        otherQueryParams.put(entry.getKey(), entry.getValue());
                    }
                }

                if (!otherQueryParams.isEmpty()) {
                    sb.append("        query_params = {\n");
                    for (Map.Entry<String, String> entry : otherQueryParams.entrySet()) {
                        sb.append("            '").append(entry.getKey()).append("': '").append(entry.getValue()).append("',\n");
                    }
                    sb.append("        }\n");
                    sb.append("        url = build_url(path, query_params)\n");
                } else {
                    sb.append("        url = f\"{BASE_URL}{path}\"\n");
                }

                sb.append("        \n");
                sb.append("        headers = {'Accept': 'application/json'}\n");
                sb.append("        \n");
                sb.append("        # Act\n");
                sb.append("        response = requests.").append(method).append("(\n");
                sb.append("            url,\n");
                sb.append("            headers=headers,\n");
                sb.append("            timeout=REQUEST_TIMEOUT\n");
                sb.append("        )\n");
                sb.append("        \n");
                sb.append("        # Assert\n");
                sb.append("        assert response.status_code == 400, f'Expected 400 Bad Request, got {response.status_code}'\n");
                sb.append("        \n\n");
            }
        }

        // Test: Unauthorized access (if security is required)
        if (requiresAuth) {
            sb.append("    def test_").append(testMethodName).append("_unauthorized(self):\n");
            sb.append("        \"\"\"Test ").append(summary != null ? summary : method.toUpperCase() + " " + path).append(" - Unauthorized Access\"\"\"\n");
            sb.append("        # Arrange - Request without authentication\n");

            if (!pathParams.isEmpty()) {
                sb.append("        path_params = {\n");
                for (Map.Entry<String, String> entry : pathParams.entrySet()) {
                    sb.append("            '").append(entry.getKey()).append("': '").append(entry.getValue()).append("',\n");
                }
                sb.append("        }\n");
                sb.append("        path = replace_path_params('").append(path).append("', path_params)\n");
            } else {
                sb.append("        path = '").append(path).append("'\n");
            }

            if (!queryParams.isEmpty()) {
                sb.append("        query_params = {\n");
                for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                    sb.append("            '").append(entry.getKey()).append("': '").append(entry.getValue()).append("',\n");
                }
                sb.append("        }\n");
                sb.append("        url = build_url(path, query_params)\n");
            } else {
                sb.append("        url = f\"{BASE_URL}{path}\"\n");
            }

            sb.append("        \n");
            sb.append("        headers = {'Accept': 'application/json'}\n");
            sb.append("        # Intentionally omitting Authorization header\n");
            sb.append("        \n");
            sb.append("        # Act\n");
            sb.append("        response = requests.").append(method).append("(\n");
            sb.append("            url,\n");
            sb.append("            headers=headers,\n");
            sb.append("            timeout=REQUEST_TIMEOUT\n");
            sb.append("        )\n");
            sb.append("        \n");
            sb.append("        # Assert\n");
            sb.append("        assert response.status_code == 401, f'Expected 401 Unauthorized, got {response.status_code}'\n");
            sb.append("        \n\n");
        }
    }

    /**
     * Generate test configuration file
     */
    private void generateTestConfiguration(String outputDir, String baseUrl) throws IOException {
        String configContent = "# Integration Test Configuration\n" +
                "# Generated from OpenAPI specification\n\n" +
                "[pytest]\n" +
                "addopts = -v --tb=short --strict-markers\n" +
                "testpaths = .\n" +
                "python_files = test_*.py\n" +
                "python_classes = Test*\n" +
                "python_functions = test_*\n" +
                "markers =\n" +
                "    integration: Integration tests\n" +
                "    slow: Slow running tests\n\n" +
                "[env]\n" +
                "API_BASE_URL=" + baseUrl + "\n" +
                "# API_TOKEN=your_token_here\n";

        Files.write(Paths.get(outputDir, "pytest.ini"), configContent.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generate conftest.py for pytest fixtures
     */
    private void generateConftest(String outputDir, String baseUrl) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("\"\"\"Pytest configuration and fixtures for integration tests\"\"\"\n\n");
        sb.append("import pytest\n");
        sb.append("import requests\n");
        sb.append("import time\n");
        sb.append("import os\n\n");

        sb.append("@pytest.fixture(scope='session', autouse=True)\n");
        sb.append("def check_server_health():\n");
        sb.append("    \"\"\"Check if the API server is reachable before running tests\"\"\"\n");
        sb.append("    base_url = os.getenv('API_BASE_URL', '").append(baseUrl).append("')\n");
        sb.append("    try:\n");
        sb.append("        response = requests.get(base_url, timeout=5)\n");
        sb.append("        print(f\"Server health check: {response.status_code}\")\n");
        sb.append("    except Exception as e:\n");
        sb.append("        print(f\"Warning: Could not reach server at {base_url}\")\n");
        sb.append("        print(f\"Some tests may fail if server is not running.\")\n");
        sb.append("        print(f\"Error: {e}\")\n\n");

        sb.append("@pytest.fixture\n");
        sb.append("def api_client():\n");
        sb.append("    \"\"\"Provide a configured requests session\"\"\"\n");
        sb.append("    session = requests.Session()\n");
        sb.append("    session.headers.update({'Accept': 'application/json'})\n");
        sb.append("    return session\n\n");

        sb.append("@pytest.fixture\n");
        sb.append("def auth_headers():\n");
        sb.append("    \"\"\"Provide authentication headers\"\"\"\n");
        sb.append("    token = os.getenv('API_TOKEN')\n");
        sb.append("    if token:\n");
        sb.append("        return {'Authorization': f'Bearer {token}'}\n");
        sb.append("    return {}\n");

        Files.write(Paths.get(outputDir, "conftest.py"), sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generate requirements.txt for pytest and requests
     */
    private void generateRequirements(String outputDir) throws IOException {
        String requirements = "# Test dependencies\n" +
                "pytest>=7.0.0\n" +
                "pytest-cov>=4.0.0\n" +
                "requests>=2.28.0\n" +
                "python-dotenv>=0.19.0\n";

        Files.write(Paths.get(outputDir, "requirements.txt"), requirements.getBytes(StandardCharsets.UTF_8));
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

    private String toSnakeCase(String name) {
        if (name == null || name.isEmpty()) {
            return "default";
        }
        // Convert PascalCase/camelCase to snake_case
        String result = name.replaceAll("([a-z])([A-Z])", "$1_$2");
        // Replace non-alphanumeric characters with underscores
        result = result.replaceAll("[^a-zA-Z0-9]+", "_");
        return result.toLowerCase(Locale.ROOT);
    }

    private String toPascalCase(String name) {
        if (name == null || name.isEmpty()) {
            return "Default";
        }
        String[] parts = name.split("[^a-zA-Z0-9]+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    sb.append(part.substring(1).toLowerCase(Locale.ROOT));
                }
            }
        }
        return sb.toString();
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
        return "Pytest Integration Test Generator";
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


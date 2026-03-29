package egain.oassdk.testgenerators.postman;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import egain.oassdk.Util;
import egain.oassdk.core.Constants;
import egain.oassdk.config.TestConfig;
import egain.oassdk.core.exceptions.GenerationException;
import egain.oassdk.testgenerators.ConfigurableTestGenerator;
import egain.oassdk.testgenerators.TestGenerator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Postman collection generator
 */
public class PostmanTestGenerator implements TestGenerator, ConfigurableTestGenerator {

    private TestConfig config;
    private final ObjectMapper objectMapper;

    public PostmanTestGenerator() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(SerializationFeature.INDENT_OUTPUT, false);
    }

    @Override
    public void generate(Map<String, Object> spec, String outputDir, TestConfig config, String testFramework) throws GenerationException {
        this.config = config;

        if (outputDir == null) {
            throw new IllegalArgumentException("Output directory cannot be null");
        }
        try {
            // Create output directory
            Files.createDirectories(Paths.get(outputDir));

            // Generate Postman collection
            generatePostmanCollection(spec, outputDir);

            // Generate environment file
            generateEnvironmentFile(spec, outputDir);

            // Generate test scripts
            generateTestScripts(spec, outputDir);

        } catch (Exception e) {
            String errorMsg = e.getMessage();
            if (errorMsg == null || errorMsg.isEmpty()) {
                errorMsg = e.getClass().getSimpleName() + " at " + e.getStackTrace()[0].toString();
            }
            throw new GenerationException("Failed to generate Postman tests: " + errorMsg, e);
        }
    }

    @Override
    public String getName() {
        return "Postman Test Generator";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getTestType() {
        return "postman";
    }

    @Override
    public void setConfig(TestConfig config) {
        this.config = config;
    }

    @Override
    public TestConfig getConfig() {
        return this.config;
    }

    /**
     * Generate Postman collection
     */
    private void generatePostmanCollection(Map<String, Object> spec, String outputDir) throws IOException {
        String apiTitle = getAPITitle(spec);
        String apiVersion = getAPIVersion(spec);
        String apiDescription = getAPIDescription(spec);

        // Ensure all values are non-null for Map.of()
        String safeTitle = apiTitle != null ? apiTitle : "API";
        String safeVersion = apiVersion != null ? apiVersion : "1.0.0";
        String safeDescription = apiDescription != null ? apiDescription : "Generated API";

        Map<String, Object> collection = new HashMap<>();
        Map<String, Object> info = new HashMap<>();
        info.put("name", safeTitle + " API Tests");
        info.put("description", safeDescription);
        info.put("version", safeVersion);
        info.put("schema", "https://schema.getpostman.com/json/collection/v2.1.0/collection.json");
        collection.put("info", info);

        // Generate items (requests) from paths
        List<Map<String, Object>> items = generateCollectionItems(spec);
        collection.put("item", items);

        // Generate variables
        List<Map<String, Object>> variables = generateVariables(spec);
        collection.put("variable", variables);

        // Convert to JSON
        String json = convertToJson(collection);

        // Write to file
        String fileName = safeTitle.replaceAll("[^a-zA-Z0-9]", "-") + "-API.postman_collection.json";
        Files.write(Paths.get(outputDir, fileName), json.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generate collection items from API paths
     */
    private List<Map<String, Object>> generateCollectionItems(Map<String, Object> spec) {
        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));

        if (paths == null) return items;

        // First, collect all operations grouped by tag across all paths
        Map<String, List<Map<String, Object>>> allTaggedOperations = new HashMap<>();

        for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
            String path = pathEntry.getKey();
            Map<String, Object> pathItem = Util.asStringObjectMap(pathEntry.getValue());

            if (pathItem == null) continue;

            // Get operations for this path grouped by tag
            Map<String, List<Map<String, Object>>> taggedOperations = groupOperationsByTag(pathItem, path);

            // Merge into the global tag map
            for (Map.Entry<String, List<Map<String, Object>>> tagEntry : taggedOperations.entrySet()) {
                String tag = tagEntry.getKey();
                List<Map<String, Object>> operations = tagEntry.getValue();

                // Add operations to the existing tag list or create a new one
                allTaggedOperations.computeIfAbsent(tag, k -> new ArrayList<>()).addAll(operations);
            }
        }

        // Now create folders for each unique tag
        for (Map.Entry<String, List<Map<String, Object>>> tagEntry : allTaggedOperations.entrySet()) {
            String tag = tagEntry.getKey();
            List<Map<String, Object>> operations = tagEntry.getValue();

            Map<String, Object> folder = new HashMap<>();
            folder.put("name", tag);
            folder.put("item", operations);
            items.add(folder);
        }

        return items;
    }

    /**
     * Group operations by tag
     */
    private Map<String, List<Map<String, Object>>> groupOperationsByTag(Map<String, Object> pathItem, String path) {
        Map<String, List<Map<String, Object>>> taggedOperations = new HashMap<>();

        String[] methods = Constants.HTTP_METHODS;
        for (String method : methods) {
            if (pathItem.containsKey(method)) {
                Map<String, Object> operation = Util.asStringObjectMap(pathItem.get(method));
                if (operation == null) continue;

                String tag = getOperationTag(operation);
                Map<String, Object> request = generatePostmanRequest(method, path, operation);

                taggedOperations.computeIfAbsent(tag, k -> new ArrayList<>()).add(request);
            }
        }

        return taggedOperations;
    }

    /**
     * Generate Postman request
     */
    private Map<String, Object> generatePostmanRequest(String method, String path, Map<String, Object> operation) {
        String operationId = (String) operation.get("operationId");
        String summary = (String) operation.get("summary");
        String description = (String) operation.get("description");

        // Escape newlines in description for Postman JSON format
        String escapedDescription = escapeStringForJson(description != null ? description : "");

        Map<String, Object> request = new HashMap<>();
        request.put("name", summary != null ? summary : operationId != null ? operationId : method.toUpperCase() + " " + path);

        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("method", method.toUpperCase());
        requestMap.put("header", generateHeaders(operation));
        requestMap.put("url", generateUrl(path, operation));
        requestMap.put("body", generateBody(operation));
        requestMap.put("description", escapedDescription);
        request.put("request", requestMap);

        // Add tests
        request.put("event", generateTests(operation));

        return request;
    }

    /**
     * Generate headers
     */
    private List<Map<String, Object>> generateHeaders(Map<String, Object> operation) {
        List<Map<String, Object>> headers = new ArrayList<>();

        // Add common headers
        headers.add(Map.of("key", "Content-Type", "value", "application/json"));
        headers.add(Map.of("key", "Accept", "value", "application/json"));

        // Add operation-specific headers
        if (operation.containsKey("parameters")) {
            List<Map<String, Object>> parameters = Util.asStringObjectMapList(operation.get("parameters"));
            if (parameters != null) {
                for (Map<String, Object> param : parameters) {
                    if (param != null && "header".equals(param.get("in"))) {
                        Object name = param.get("name");
                        if (name != null) {
                            Object description = param.get("description");
                            headers.add(Map.of(
                                    "key", name.toString(),
                                    "value", "{{" + name + "}}",
                                    "description", description != null ? description.toString() : ""
                            ));
                        }
                    }
                }
            }
        }

        return headers;
    }

    /**
     * Generate URL
     */
    private Map<String, Object> generateUrl(String path, Map<String, Object> operation) {
        Map<String, Object> url = new HashMap<>();
        String safePath = path != null ? path : "";
        url.put("raw", "{{base_url}}" + safePath);
        url.put("host", List.of("{{base_url}}"));
        url.put("path", Arrays.asList(safePath.split("/")));

        // Add query parameters
        List<Map<String, Object>> queryParams = new ArrayList<>();
        if (operation.containsKey("parameters")) {
            List<Map<String, Object>> parameters = Util.asStringObjectMapList(operation.get("parameters"));
            if (parameters != null) {
                for (Map<String, Object> param : parameters) {
                    if (param != null && "query".equals(param.get("in"))) {
                        Object name = param.get("name");
                        if (name != null) {
                            Object description = param.get("description");
                            queryParams.add(Map.of(
                                    "key", name.toString(),
                                    "value", "{{" + name + "}}",
                                    "description", description != null ? description.toString() : ""
                            ));
                        }
                    }
                }
            }
        }

        if (!queryParams.isEmpty()) {
            url.put("query", queryParams);
        }

        return url;
    }

    /**
     * Generate request body
     */
    private Map<String, Object> generateBody(Map<String, Object> operation) {
        if (!operation.containsKey("requestBody")) {
            return Map.of("mode", "raw", "raw", "");
        }

        Map<String, Object> requestBody = Util.asStringObjectMap(operation.get("requestBody"));
        Map<String, Object> content = Util.asStringObjectMap(requestBody.get("content"));

        if (content == null || !content.containsKey("application/json")) {
            return Map.of("mode", "raw", "raw", "");
        }

        Map<String, Object> jsonContent = Util.asStringObjectMap(content.get("application/json"));
        String example = extractExampleFromContent(jsonContent);

        // Jackson will properly escape the string when serializing to JSON
        // We keep the example as-is (with actual newlines) and let Jackson handle escaping
        // This ensures the raw field contains properly escaped newlines in the final JSON
        String safeExample = example != null ? example : "";

        Map<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("mode", "raw");
        bodyMap.put("raw", safeExample);

        Map<String, Object> rawOptions = new HashMap<>();
        rawOptions.put("language", "json");
        Map<String, Object> options = new HashMap<>();
        options.put("raw", rawOptions);
        bodyMap.put("options", options);

        return bodyMap;
    }

    /**
     * Extract example from content (checks examples first, then schema example)
     */
    @SuppressWarnings("unchecked")
    private String extractExampleFromContent(Map<String, Object> jsonContent) {
        if (jsonContent == null) {
            return "{}";
        }

        // First, try to get example from examples map
        if (jsonContent.containsKey("examples")) {
            Map<String, Object> examples = (Map<String, Object>) jsonContent.get("examples");
            if (examples != null && !examples.isEmpty()) {
                // Get the first example's value
                Object firstExample = examples.values().iterator().next();
                if (firstExample instanceof Map) {
                    Map<String, Object> exampleMap = (Map<String, Object>) firstExample;
                    if (exampleMap.containsKey("value")) {
                        Object value = exampleMap.get("value");
                        if (value != null) {
                            try {
                                return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
                            } catch (Exception e) {
                                // Fallback to string representation
                                return value.toString();
                            }
                        }
                    }
                }
            }
        }

        // Try to get example from schema
        if (jsonContent.containsKey("schema")) {
            Map<String, Object> schema = (Map<String, Object>) jsonContent.get("schema");
            return generateExampleFromSchema(schema);
        }

        // If no example found, generate a simple placeholder
        return "{\n  \"example\": \"value\"\n}";
    }

    /**
     * Generate tests
     */
    private List<Map<String, Object>> generateTests(Map<String, Object> operation) {
        List<Map<String, Object>> events = new ArrayList<>();

        Map<String, Object> testEvent = new HashMap<>();
        testEvent.put("listen", "test");

        List<String> testScript = generateTestScript(operation);

        Map<String, Object> scriptMap = new HashMap<>();
        scriptMap.put("type", "text/javascript");
        scriptMap.put("exec", testScript);
        testEvent.put("script", scriptMap);

        events.add(testEvent);
        return events;
    }

    /**
     * Generate test script
     */
    private List<String> generateTestScript(Map<String, Object> operation) {
        List<String> script = new ArrayList<>();

        script.add("// Test response status");
        script.add("pm.test(\"Status code is 200\", function () {");
        script.add("    pm.response.to.have.status(200);");
        script.add("});");
        script.add("");

        script.add("// Test response time");
        script.add("pm.test(\"Response time is less than 2000ms\", function () {");
        script.add("    pm.expect(pm.response.responseTime).to.be.below(2000);");
        script.add("});");
        script.add("");

        script.add("// Test response headers");
        script.add("pm.test(\"Content-Type is application/json\", function () {");
        script.add("    pm.expect(pm.response.headers.get(\"Content-Type\")).to.include(\"application/json\");");
        script.add("});");
        script.add("");

        // Add response validation if schema is available
        if (operation.containsKey("responses")) {
            Map<String, Object> responses = Util.asStringObjectMap(operation.get("responses"));
            if (responses.containsKey("200")) {
                Map<String, Object> response200 = Util.asStringObjectMap(responses.get("200"));
                if (response200.containsKey("content")) {
                    script.add("// Test response body structure");
                    script.add("pm.test(\"Response has required fields\", function () {");
                    script.add("    const jsonData = pm.response.json();");
                    script.add("    // Add specific field validations here");
                    script.add("});");
                }
            }
        }

        return script;
    }

    /**
     * Generate variables
     */
    private List<Map<String, Object>> generateVariables(Map<String, Object> spec) {
        List<Map<String, Object>> variables = new ArrayList<>();

        // Add server URLs if available (prefer production server)
        if (spec.containsKey("servers")) {
            List<Map<String, Object>> servers = Util.asStringObjectMapList(spec.get("servers"));
            if (servers != null && !servers.isEmpty()) {
                Map<String, Object> firstServer = servers.get(0);
                if (firstServer != null) {
                    String serverUrl = (String) firstServer.get("url");
                    if (serverUrl != null) {
                        variables.add(Map.of(
                                "key", "base_url",
                                "value", serverUrl,
                                "type", "string"
                        ));
                    }
                }
            }
        }

        // If no server URL was added, use fallback
        if (variables.isEmpty()) {
            // Fallback to localhost if no servers defined
            variables.add(Map.of(
                    "key", "base_url",
                    "value", "http://localhost:8080",
                    "type", "string"
            ));
        }

        return variables;
    }

    /**
     * Generate environment file
     */
    private void generateEnvironmentFile(Map<String, Object> spec, String outputDir) throws IOException {
        String apiTitle = getAPITitle(spec);
        String safeTitle = apiTitle != null ? apiTitle : "API";

        Map<String, Object> environment = new HashMap<>();
        environment.put("id", UUID.randomUUID().toString());
        environment.put("name", safeTitle + " Environment");
        environment.put("values", generateVariables(spec));

        String json = convertToJson(environment);
        String fileName = safeTitle.replaceAll("[^a-zA-Z0-9]", "-") + "-Environment.postman_environment.json";
        Files.write(Paths.get(outputDir, fileName), json.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generate test scripts
     */
    private void generateTestScripts(Map<String, Object> spec, String outputDir) throws IOException {
        String apiTitle = getAPITitle(spec);
        String safeTitle = apiTitle != null ? apiTitle : "API";

        // Generate Newman test script
        String newmanScript = generateNewmanScript(safeTitle);
        Files.write(Paths.get(outputDir, "run-tests.sh"), newmanScript.getBytes(StandardCharsets.UTF_8));

        // Generate curl commands
        String curlScript = generateCurlScript(spec);
        Files.write(Paths.get(outputDir, "curl-commands.sh"), curlScript.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generate Newman test script
     */
    private String generateNewmanScript(String apiTitle) {
        String safeTitle = apiTitle != null ? apiTitle : "API";
        String collectionFile = safeTitle.replaceAll("[^a-zA-Z0-9]", "-") + "-API.postman_collection.json";
        String environmentFile = safeTitle.replaceAll("[^a-zA-Z0-9]", "-") + "-Environment.postman_environment.json";

        return String.format("""
                #!/bin/bash
                
                # Install Newman if not already installed
                if ! command -v newman &> /dev/null; then
                    echo "Installing Newman..."
                    npm install -g newman
                fi
                
                # Run Postman collection tests
                echo "Running API tests with Newman..."
                newman run %s -e %s --reporters cli,json --reporter-json-export test-results.json
                
                # Check test results
                if [ $? -eq 0 ]; then
                    echo "All tests passed!"
                else
                    echo "Some tests failed. Check test-results.json for details."
                    exit 1
                fi
                """, collectionFile, environmentFile);
    }

    /**
     * Generate curl commands script
     */
    private String generateCurlScript(Map<String, Object> spec) {
        StringBuilder script = new StringBuilder();
        script.append("#!/bin/bash\n\n");
        script.append("# API Test Script - Generated from OpenAPI specification\n");
        script.append("BASE_URL=\"http://localhost:8080\"\n\n");

        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        if (paths != null) {
            for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
                String path = pathEntry.getKey();
                Map<String, Object> pathItem = Util.asStringObjectMap(pathEntry.getValue());

                if (pathItem == null) continue;

                String[] methods = {"get", "post", "put", "delete", "patch"};
                for (String method : methods) {
                    if (pathItem.containsKey(method)) {
                        Map<String, Object> operation = Util.asStringObjectMap(pathItem.get(method));
                        String summary = (String) operation.get("summary");

                        script.append("# ").append(summary != null ? summary : method.toUpperCase()).append(" ").append(path).append("\n");
                        script.append("curl -X ").append(method.toUpperCase()).append(" \"$BASE_URL").append(path).append("\" \\\n");
                        script.append("  -H \"Content-Type: application/json\" \\\n");
                        script.append("  -H \"Accept: application/json\"\n\n");
                    }
                }
            }
        }

        return script.toString();
    }

    /**
     * Helper methods
     */
    private String getAPITitle(Map<String, Object> spec) {
        if (spec == null) {
            return "API";
        }
        Map<String, Object> info = Util.asStringObjectMap(spec.get("info"));
        if (info == null) {
            return "API";
        }
        Object title = info.get("title");
        return title != null ? title.toString() : "API";
    }

    private String getAPIVersion(Map<String, Object> spec) {
        if (spec == null) {
            return "1.0.0";
        }
        Map<String, Object> info = Util.asStringObjectMap(spec.get("info"));
        if (info == null) {
            return "1.0.0";
        }
        Object version = info.get("version");
        return version != null ? version.toString() : "1.0.0";
    }

    private String getAPIDescription(Map<String, Object> spec) {
        if (spec == null) {
            return "Generated API";
        }
        Map<String, Object> info = Util.asStringObjectMap(spec.get("info"));
        if (info == null) {
            return "Generated API";
        }
        Object description = info.get("description");
        return description != null ? description.toString() : "Generated API";
    }

    private String getOperationTag(Map<String, Object> operation) {
        if (operation == null) {
            return "Default";
        }
        List<String> tags = Util.asStringList(operation.get("tags"));
        if (tags != null && !tags.isEmpty()) {
            String firstTag = tags.get(0);
            return firstTag != null ? firstTag : "Default";
        }
        return "Default";
    }

    private String generateExampleFromSchema(Map<String, Object> schema) {
        if (schema == null) {
            return "{}";
        }

        // First, try to get example from the schema directly
        if (schema.containsKey("example")) {
            Object example = schema.get("example");
            if (example != null) {
                try {
                    return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(example);
                } catch (Exception e) {
                    // Fallback to string representation
                    return example.toString();
                }
            }
        }

        // Try to get from examples map
        if (schema.containsKey("examples")) {
            Map<String, Object> examples = Util.asStringObjectMap(schema.get("examples"));
            if (examples != null && !examples.isEmpty()) {
                // Get the first example
                Object firstExample = examples.values().iterator().next();
                if (firstExample instanceof Map) {
                    Map<String, Object> exampleMap = Util.asStringObjectMap(firstExample);
                    if (exampleMap.containsKey("value")) {
                        Object value = exampleMap.get("value");
                        if (value != null) {
                            try {
                                return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
                            } catch (Exception e) {
                                return value.toString();
                            }
                        }
                    }
                }
            }
        }

        // If no example found, generate a simple placeholder
        return "{\n  \"example\": \"value\"\n}";
    }

    private String convertToJson(Map<String, Object> data) {
        try {
            // Use Jackson ObjectMapper for proper JSON serialization with escaping
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data);
        } catch (Exception e) {
            // Fallback to manual conversion if Jackson fails
            return convertToJsonManual(data);
        }
    }

    /**
     * Manual JSON conversion fallback (with proper string escaping)
     */
    private String convertToJsonManual(Map<String, Object> data) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");

        boolean first = true;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (!first) json.append(",\n");
            json.append("  \"").append(escapeJsonString(entry.getKey())).append("\": ");

            Object value = entry.getValue();
            switch (value) {
                case String s -> json.append("\"").append(escapeJsonString(s)).append("\"");
                case List<?> list -> json.append(convertListToJson(list));
                case Map<?, ?> map -> json.append(convertMapToJson(map));
                case null, default -> json.append(value);
            }

            first = false;
        }

        json.append("\n}");
        return json.toString();
    }

    private String convertListToJson(List<?> list) {
        StringBuilder json = new StringBuilder();
        json.append("[\n");

        boolean first = true;
        for (Object item : list) {
            if (!first) json.append(",\n");
            json.append("    ");

            if (item instanceof String) {
                json.append("\"").append(escapeJsonString((String) item)).append("\"");
            } else if (item instanceof Map) {
                json.append(convertMapToJson((Map<?, ?>) item));
            } else {
                json.append(item);
            }

            first = false;
        }

        json.append("\n  ]");
        return json.toString();
    }

    private String convertMapToJson(Map<?, ?> map) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");

        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) json.append(",\n");
            String key = entry.getKey() != null ? entry.getKey().toString() : "null";
            json.append("    \"").append(escapeJsonString(key)).append("\": ");

            Object value = entry.getValue();
            switch (value) {
                case String s -> json.append("\"").append(escapeJsonString(s)).append("\"");
                case List<?> list -> json.append(convertListToJson(list));
                case Map<?, ?> map1 -> json.append(convertMapToJson(map1));
                case null, default -> json.append(value);
            }

            first = false;
        }

        json.append("\n  }");
        return json.toString();
    }

    /**
     * Escape string for JSON format (converts newlines to \n, etc.)
     */
    private String escapeStringForJson(String str) {
        if (str == null) {
            return "";
        }
        return escapeJsonString(str);
    }

    /**
     * Escape JSON string properly (handles newlines, quotes, backslashes, etc.)
     */
    private String escapeJsonString(String str) {
        if (str == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (char c : str.toCharArray()) {
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                    break;
            }
        }
        return sb.toString();
    }
}

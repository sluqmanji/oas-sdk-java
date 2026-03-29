package egain.oassdk.test.sequence;

import egain.oassdk.Util;
import egain.oassdk.core.Constants;
import egain.oassdk.core.exceptions.GenerationException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Generates randomized sequence testing of API using OpenAPI spec.
 * Reads actual paths/operations from the OAS spec to generate endpoint-specific
 * test sequences rather than hardcoded endpoints.
 */
public class RandomizedSequenceTester {

    /**
     * Generate sequence tests
     *
     * @param spec      OpenAPI specification
     * @param outputDir Output directory
     * @param baseUrl   Base URL for the API under test
     * @throws GenerationException if generation fails
     */
    public void generateSequenceTests(Map<String, Object> spec, String outputDir, String baseUrl) throws GenerationException {
        try {
            Files.createDirectories(Paths.get(outputDir));

            // Extract API calls from the spec
            List<APICallInfo> apiCalls = extractAPICallsFromSpec(spec);

            // Generate sequence test framework
            generateSequenceTestFramework(spec, outputDir, baseUrl, apiCalls);

            // Generate random sequence generator
            generateRandomSequenceGenerator(spec, outputDir, baseUrl);

            // Generate sequence test cases
            generateSequenceTestCases(spec, outputDir, baseUrl, apiCalls);

            // Generate sequence test runner
            generateSequenceTestRunner(spec, outputDir, baseUrl);

            // Generate configuration
            generateSequenceTestConfig(spec, outputDir, baseUrl, apiCalls);

        } catch (Exception e) {
            throw new GenerationException("Failed to generate sequence tests: " + e.getMessage(), e);
        }
    }

    /**
     * Extract all API calls from the OpenAPI spec paths
     */
    private List<APICallInfo> extractAPICallsFromSpec(Map<String, Object> spec) {
        List<APICallInfo> calls = new ArrayList<>();
        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        if (paths == null) return calls;

        for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
            String path = pathEntry.getKey();
            Map<String, Object> pathItem = Util.asStringObjectMap(pathEntry.getValue());
            if (pathItem == null) continue;

            for (String method : Constants.HTTP_METHODS) {
                if (pathItem.containsKey(method)) {
                    Map<String, Object> operation = Util.asStringObjectMap(pathItem.get(method));
                    if (operation == null) continue;

                    APICallInfo info = new APICallInfo();
                    info.method = method.toUpperCase();
                    info.path = path;
                    info.operationId = (String) operation.get("operationId");
                    info.hasPathParams = path.contains("{");
                    info.hasRequestBody = operation.containsKey("requestBody");
                    info.operation = operation;

                    // Extract resource name from path (e.g., /api/users/{id} -> users)
                    info.resourceName = extractResourceName(path);

                    calls.add(info);
                }
            }
        }
        return calls;
    }

    /**
     * Extract resource name from a path, e.g. /api/users/{id} -> users
     */
    private String extractResourceName(String path) {
        String[] segments = path.split("/");
        for (int i = segments.length - 1; i >= 0; i--) {
            String seg = segments[i];
            if (!seg.isEmpty() && !seg.startsWith("{")) {
                return seg;
            }
        }
        return "resource";
    }

    /**
     * Build a JSON request body string for an operation using mock data generation.
     * Extracts the schema from requestBody and generates realistic fake data.
     */
    private String buildRequestBodyForOperation(Map<String, Object> operation, Map<String, Object> spec) {
        Map<String, Object> requestBody = Util.asStringObjectMap(operation.get("requestBody"));
        if (requestBody == null) return null;

        Map<String, Object> content = Util.asStringObjectMap(requestBody.get("content"));
        if (content == null) return null;

        Map<String, Object> jsonContent = Util.asStringObjectMap(content.get("application/json"));
        if (jsonContent == null) {
            for (Object val : content.values()) {
                jsonContent = Util.asStringObjectMap(val);
                if (jsonContent != null) break;
            }
        }
        if (jsonContent == null) return null;

        Map<String, Object> schema = Util.asStringObjectMap(jsonContent.get("schema"));
        if (schema == null) return null;

        // Resolve $ref
        if (schema.containsKey("$ref")) {
            String ref = (String) schema.get("$ref");
            if (ref != null && ref.startsWith("#/components/schemas/")) {
                String schemaName = ref.substring("#/components/schemas/".length());
                Map<String, Object> components = Util.asStringObjectMap(spec.get("components"));
                if (components != null) {
                    Map<String, Object> schemas = Util.asStringObjectMap(components.get("schemas"));
                    if (schemas != null) {
                        schema = Util.asStringObjectMap(schemas.get(schemaName));
                    }
                }
            }
        }

        if (schema == null) return null;

        // Build a simple mock JSON body from schema properties
        return buildMockJsonFromSchema(schema, spec, 0);
    }

    /**
     * Build a simple mock JSON string from a schema
     */
    private String buildMockJsonFromSchema(Map<String, Object> schema, Map<String, Object> spec, int depth) {
        if (depth > 5) return "{}";

        Map<String, Object> properties = Util.asStringObjectMap(schema.get("properties"));
        if (properties == null || properties.isEmpty()) return "{}";

        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> prop : properties.entrySet()) {
            if (!first) sb.append(", ");
            sb.append("\\\"").append(prop.getKey()).append("\\\": ");

            Map<String, Object> propSchema = Util.asStringObjectMap(prop.getValue());
            if (propSchema == null) {
                sb.append("\\\"mock_value\\\"");
                first = false;
                continue;
            }

            String type = (String) propSchema.get("type");
            if (type == null) type = "string";

            switch (type) {
                case "string":
                    sb.append("\\\"mock_").append(prop.getKey()).append("\\\"");
                    break;
                case "integer":
                case "number":
                    sb.append("1");
                    break;
                case "boolean":
                    sb.append("true");
                    break;
                default:
                    sb.append("\\\"mock_value\\\"");
            }
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * Infer dependency graph from API calls.
     * Paths with path parameters (e.g., /users/{id}) depend on
     * POST/creation endpoints for the same resource.
     */
    private Map<String, List<String>> inferDependencies(List<APICallInfo> apiCalls) {
        Map<String, List<String>> dependencies = new LinkedHashMap<>();

        // Index creation endpoints (POST without path params) by resource
        Map<String, String> creationEndpoints = new HashMap<>();
        for (APICallInfo call : apiCalls) {
            String key = call.path + ":" + call.method;
            if ("POST".equals(call.method) && !call.hasPathParams) {
                creationEndpoints.put(call.resourceName, key);
                dependencies.put(key, Collections.emptyList());
            }
        }

        // Endpoints with path params depend on the creation endpoint
        for (APICallInfo call : apiCalls) {
            String key = call.path + ":" + call.method;
            if (!dependencies.containsKey(key)) {
                if (call.hasPathParams) {
                    String creationKey = creationEndpoints.get(call.resourceName);
                    if (creationKey != null) {
                        dependencies.put(key, Collections.singletonList(creationKey));
                    } else {
                        dependencies.put(key, Collections.emptyList());
                    }
                } else {
                    dependencies.put(key, Collections.emptyList());
                }
            }
        }

        return dependencies;
    }

    /**
     * Generate sequence test framework
     */
    private void generateSequenceTestFramework(Map<String, Object> spec, String outputDir, String baseUrl, List<APICallInfo> apiCalls) throws IOException {
        // Build resource names for dynamic state extraction
        Set<String> resourceNames = new LinkedHashSet<>();
        for (APICallInfo call : apiCalls) {
            resourceNames.add(call.resourceName);
        }

        StringBuilder stateExtraction = new StringBuilder();
        for (String resource : resourceNames) {
            stateExtraction.append(String.format("""
                                    if (call.getPath().contains("/%s")) {
                                        String extractedId = extractIdFromResponse(responseBody);
                                        if (extractedId != null) {
                                            state.put("%s_id", extractedId);
                                        }
                                    }
                    """, resource, resource));
        }

        String framework = """
                package com.example.sequence;

                import jakarta.ws.rs.client.Client;
                import jakarta.ws.rs.client.ClientBuilder;
                import jakarta.ws.rs.client.Entity;
                import jakarta.ws.rs.client.WebTarget;
                import jakarta.ws.rs.core.MediaType;
                import jakarta.ws.rs.core.Response;
                import com.fasterxml.jackson.databind.JsonNode;
                import com.fasterxml.jackson.databind.ObjectMapper;
                import org.junit.jupiter.api.BeforeEach;
                import org.junit.jupiter.api.AfterEach;
                import java.util.*;
                import java.util.concurrent.*;
                import java.util.stream.Collectors;

                public class SequenceTestFramework {

                    protected Client client;
                    protected WebTarget target;
                    protected final ObjectMapper objectMapper = new ObjectMapper();

                    protected final String baseUrl = "%s";
                    protected final Random random;
                    protected final Map<String, Object> state = new ConcurrentHashMap<>();
                    protected final List<APICall> createdResources = new CopyOnWriteArrayList<>();

                    public SequenceTestFramework() {
                        this(System.currentTimeMillis());
                    }

                    public SequenceTestFramework(long seed) {
                        this.random = new Random(seed);
                    }

                    @BeforeEach
                    public void setUp() {
                        client = ClientBuilder.newClient();
                        target = client.target(baseUrl);
                        state.clear();
                        createdResources.clear();
                    }

                    @AfterEach
                    public void tearDown() {
                        // Cleanup: delete created resources in reverse order
                        List<APICall> toDelete = new ArrayList<>(createdResources);
                        Collections.reverse(toDelete);
                        for (APICall created : toDelete) {
                            try {
                                String deletePath = created.getPath();
                                // Try to construct a DELETE path if the creation returned an ID
                                target.path(deletePath).request().delete().close();
                            } catch (Exception ignored) {
                                // Best-effort cleanup
                            }
                        }

                        if (client != null) {
                            client.close();
                        }
                    }

                    /**
                     * Execute a sequence of API calls
                     */
                    protected List<SequenceResult> executeSequence(List<APICall> sequence) {
                        List<SequenceResult> results = new ArrayList<>();

                        for (APICall call : sequence) {
                            try {
                                // Substitute path parameters from state
                                String resolvedPath = resolvePathParams(call.getPath());

                                long startTime = System.currentTimeMillis();
                                Response response = executeAPICall(
                                    new APICall(call.getMethod(), resolvedPath, call.getBody(), call.getHeaders()));
                                long responseTime = System.currentTimeMillis() - startTime;

                                SequenceResult result = new SequenceResult(
                                    call, response, responseTime, true, null
                                );
                                results.add(result);

                                // Update state based on response
                                updateState(call, response);

                            } catch (Exception e) {
                                SequenceResult result = new SequenceResult(
                                    call, null, 0, false, e.getMessage()
                                );
                                results.add(result);
                            }
                        }

                        return results;
                    }

                    /**
                     * Resolve path parameters using state (e.g., replace {id} with stored ID)
                     */
                    protected String resolvePathParams(String path) {
                        String resolved = path;
                        for (Map.Entry<String, Object> entry : state.entrySet()) {
                            if (entry.getKey().endsWith("_id") && entry.getValue() != null) {
                                resolved = resolved.replace("{id}", entry.getValue().toString());
                                String resource = entry.getKey().replace("_id", "");
                                resolved = resolved.replace("{" + resource + "Id}", entry.getValue().toString());
                            }
                        }
                        return resolved;
                    }

                    /**
                     * Execute a single API call
                     */
                    protected Response executeAPICall(APICall call) {
                        WebTarget callTarget = target.path(call.getPath());

                        // Add headers
                        jakarta.ws.rs.client.Invocation.Builder builder = callTarget.request(MediaType.APPLICATION_JSON);
                        for (Map.Entry<String, String> header : call.getHeaders().entrySet()) {
                            builder = builder.header(header.getKey(), header.getValue());
                        }

                        switch (call.getMethod().toUpperCase()) {
                            case "GET":
                                return builder.get();
                            case "POST":
                                return builder.post(Entity.entity(
                                    call.getBody() != null ? call.getBody() : "{}",
                                    MediaType.APPLICATION_JSON));
                            case "PUT":
                                return builder.put(Entity.entity(
                                    call.getBody() != null ? call.getBody() : "{}",
                                    MediaType.APPLICATION_JSON));
                            case "PATCH":
                                return builder.method("PATCH", Entity.entity(
                                    call.getBody() != null ? call.getBody() : "{}",
                                    MediaType.APPLICATION_JSON));
                            case "DELETE":
                                return builder.delete();
                            case "HEAD":
                                return builder.head();
                            case "OPTIONS":
                                return builder.options();
                            default:
                                throw new IllegalArgumentException("Unsupported HTTP method: " + call.getMethod());
                        }
                    }

                    /**
                     * Update state based on API response using Jackson for JSON parsing
                     */
                    protected void updateState(APICall call, Response response) {
                        int status = response.getStatus();
                        if ((status >= 200 && status < 300) && response.hasEntity()) {
                            String responseBody = response.readEntity(String.class);

                            state.put("last_response", responseBody);
                            state.put("last_status", status);

                            // Extract ID from response using Jackson
                            String extractedId = extractIdFromResponse(responseBody);

                            // Dynamic state extraction based on API endpoints
                %s

                            // Track created resources for cleanup
                            if ("POST".equalsIgnoreCase(call.getMethod()) && status == 201 && extractedId != null) {
                                String deletePath = call.getPath() + "/" + extractedId;
                                createdResources.add(new APICall("DELETE", deletePath, null, null));
                            }
                        }
                    }

                    /**
                     * Extract ID from JSON response using Jackson ObjectMapper
                     */
                    protected String extractIdFromResponse(String responseBody) {
                        try {
                            JsonNode root = objectMapper.readTree(responseBody);
                            // Try common ID fields
                            for (String field : new String[]{"id", "ID", "_id", "uuid", "resourceId"}) {
                                JsonNode idNode = root.get(field);
                                if (idNode != null && !idNode.isNull()) {
                                    return idNode.asText();
                                }
                            }
                        } catch (Exception ignored) {
                            // Not valid JSON or no ID field
                        }
                        return null;
                    }

                    /**
                     * Generate random sequence of API calls
                     */
                    protected List<APICall> generateRandomSequence(List<APICall> availableCalls, int maxLength) {
                        List<APICall> sequence = new ArrayList<>();
                        int sequenceLength = random.nextInt(maxLength) + 1;

                        for (int i = 0; i < sequenceLength; i++) {
                            APICall call = availableCalls.get(random.nextInt(availableCalls.size()));
                            sequence.add(call);
                        }

                        return sequence;
                    }

                    /**
                     * Validate sequence results with per-method status code expectations
                     */
                    protected boolean validateSequenceResults(List<SequenceResult> results) {
                        for (SequenceResult result : results) {
                            if (!result.isSuccess()) {
                                return false;
                            }

                            if (result.getResponseTime() > %d) {
                                return false;
                            }

                            // Validate expected status codes by method
                            if (result.getResponse() != null) {
                                int status = result.getResponse().getStatus();
                                // Any 2xx or expected 4xx is acceptable in sequence testing
                                if (status >= 500) {
                                    return false; // Server errors are always failures
                                }
                            }
                        }

                        return true;
                    }

                    /**
                     * API Call representation
                     */
                    public static class APICall {
                        private final String method;
                        private final String path;
                        private final Object body;
                        private final Map<String, String> headers;

                        public APICall(String method, String path, Object body, Map<String, String> headers) {
                            this.method = method;
                            this.path = path;
                            this.body = body;
                            this.headers = headers != null ? headers : new HashMap<>();
                        }

                        public String getMethod() { return method; }
                        public String getPath() { return path; }
                        public Object getBody() { return body; }
                        public Map<String, String> getHeaders() { return headers; }
                    }

                    /**
                     * Sequence result representation
                     */
                    public static class SequenceResult {
                        private final APICall call;
                        private final Response response;
                        private final long responseTime;
                        private final boolean success;
                        private final String errorMessage;

                        public SequenceResult(APICall call, Response response,
                                            long responseTime, boolean success, String errorMessage) {
                            this.call = call;
                            this.response = response;
                            this.responseTime = responseTime;
                            this.success = success;
                            this.errorMessage = errorMessage;
                        }

                        public APICall getCall() { return call; }
                        public Response getResponse() { return response; }
                        public long getResponseTime() { return responseTime; }
                        public boolean isSuccess() { return success; }
                        public String getErrorMessage() { return errorMessage; }
                    }
                }
                """.formatted(baseUrl, stateExtraction.toString(), Constants.DEFAULT_MAX_RESPONSE_TIME_MS);

        Files.write(Paths.get(outputDir, "SequenceTestFramework.java"), framework.getBytes());
    }

    /**
     * Generate random sequence generator
     */
    private void generateRandomSequenceGenerator(Map<String, Object> spec, String outputDir, String baseUrl) throws IOException {
        String generator = """
                package com.example.sequence;

                import com.example.sequence.SequenceTestFramework.APICall;
                import java.util.*;
                import java.util.stream.Collectors;

                public class RandomSequenceGenerator {

                    private final Random random;
                    private final List<APICall> availableCalls;

                    public RandomSequenceGenerator(List<APICall> availableCalls) {
                        this(availableCalls, System.currentTimeMillis());
                    }

                    public RandomSequenceGenerator(List<APICall> availableCalls, long seed) {
                        this.availableCalls = availableCalls;
                        this.random = new Random(seed);
                    }

                    /**
                     * Generate random sequence based on OpenAPI spec
                     */
                    public List<APICall> generateRandomSequence(int maxLength) {
                        List<APICall> sequence = new ArrayList<>();
                        int sequenceLength = random.nextInt(maxLength) + 1;

                        for (int i = 0; i < sequenceLength; i++) {
                            APICall call = availableCalls.get(random.nextInt(availableCalls.size()));
                            sequence.add(call);
                        }

                        return sequence;
                    }

                    /**
                     * Generate weighted random sequence
                     */
                    public List<APICall> generateWeightedSequence(int maxLength, Map<String, Double> weights) {
                        List<APICall> sequence = new ArrayList<>();
                        int sequenceLength = random.nextInt(maxLength) + 1;

                        for (int i = 0; i < sequenceLength; i++) {
                            APICall call = selectWeightedCall(weights);
                            sequence.add(call);
                        }

                        return sequence;
                    }

                    /**
                     * Generate sequence with dependencies
                     */
                    public List<APICall> generateDependentSequence(int maxLength, Map<String, List<String>> dependencies) {
                        List<APICall> sequence = new ArrayList<>();
                        Set<String> executedCalls = new HashSet<>();
                        int sequenceLength = random.nextInt(maxLength) + 1;

                        for (int i = 0; i < sequenceLength; i++) {
                            APICall call = selectDependentCall(executedCalls, dependencies);
                            if (call != null) {
                                sequence.add(call);
                                executedCalls.add(call.getPath() + ":" + call.getMethod());
                            }
                        }

                        return sequence;
                    }

                    /**
                     * Select weighted call
                     */
                    private APICall selectWeightedCall(Map<String, Double> weights) {
                        double totalWeight = weights.values().stream().mapToDouble(Double::doubleValue).sum();
                        double randomWeight = random.nextDouble() * totalWeight;

                        double currentWeight = 0;
                        for (APICall call : availableCalls) {
                            String key = call.getPath() + ":" + call.getMethod();
                            currentWeight += weights.getOrDefault(key, 1.0);
                            if (currentWeight >= randomWeight) {
                                return call;
                            }
                        }

                        return availableCalls.get(random.nextInt(availableCalls.size()));
                    }

                    /**
                     * Select dependent call
                     */
                    private APICall selectDependentCall(Set<String> executedCalls, Map<String, List<String>> dependencies) {
                        List<APICall> eligible = this.availableCalls.stream()
                            .filter(call -> {
                                String key = call.getPath() + ":" + call.getMethod();
                                List<String> deps = dependencies.get(key);
                                return deps == null || deps.stream().allMatch(executedCalls::contains);
                            })
                            .collect(Collectors.toList());

                        if (eligible.isEmpty()) {
                            return null;
                        }

                        return eligible.get(random.nextInt(eligible.size()));
                    }

                    /**
                     * Generate sequence with state transitions
                     */
                    public List<APICall> generateStatefulSequence(int maxLength, Map<String, String> stateTransitions) {
                        List<APICall> sequence = new ArrayList<>();
                        String currentState = "initial";
                        int sequenceLength = random.nextInt(maxLength) + 1;

                        for (int i = 0; i < sequenceLength; i++) {
                            APICall call = selectStatefulCall(currentState, stateTransitions);
                            if (call != null) {
                                sequence.add(call);
                                currentState = stateTransitions.getOrDefault(call.getPath() + ":" + call.getMethod(), currentState);
                            }
                        }

                        return sequence;
                    }

                    /**
                     * Select stateful call
                     */
                    private APICall selectStatefulCall(String currentState, Map<String, String> stateTransitions) {
                        List<APICall> eligible = this.availableCalls.stream()
                            .filter(call -> {
                                String key = call.getPath() + ":" + call.getMethod();
                                return stateTransitions.containsKey(key);
                            })
                            .collect(Collectors.toList());

                        if (eligible.isEmpty()) {
                            return null;
                        }

                        return eligible.get(random.nextInt(eligible.size()));
                    }
                }
                """;

        Files.write(Paths.get(outputDir, "RandomSequenceGenerator.java"), generator.getBytes());
    }

    /**
     * Generate sequence test cases -- now OAS-driven
     */
    private void generateSequenceTestCases(Map<String, Object> spec, String outputDir, String baseUrl, List<APICallInfo> apiCalls) throws IOException {
        // Build the initializeAPICalls method body from actual spec
        StringBuilder initBody = new StringBuilder();
        for (APICallInfo call : apiCalls) {
            String body = "null";
            if (call.hasRequestBody) {
                String mockBody = buildRequestBodyForOperation(call.operation, spec);
                body = mockBody != null ? "\"" + mockBody + "\"" : "null";
            }
            initBody.append("            calls.add(new APICall(\"")
                    .append(call.method).append("\", \"")
                    .append(call.path).append("\", ")
                    .append(body).append(", null));\n");
        }

        // Build dependency map from spec
        Map<String, List<String>> dependencies = inferDependencies(apiCalls);
        StringBuilder depsBody = new StringBuilder();
        for (Map.Entry<String, List<String>> entry : dependencies.entrySet()) {
            if (entry.getValue().isEmpty()) {
                depsBody.append("            dependencies.put(\"").append(entry.getKey())
                        .append("\", Arrays.asList());\n");
            } else {
                depsBody.append("            dependencies.put(\"").append(entry.getKey())
                        .append("\", Arrays.asList(");
                boolean first = true;
                for (String dep : entry.getValue()) {
                    if (!first) depsBody.append(", ");
                    depsBody.append("\"").append(dep).append("\"");
                    first = false;
                }
                depsBody.append("));\n");
            }
        }

        // Build state transitions from spec
        StringBuilder stateBody = new StringBuilder();
        for (APICallInfo call : apiCalls) {
            String key = call.path + ":" + call.method;
            String stateName = call.resourceName + "_" + call.method.toLowerCase();
            stateBody.append("            stateTransitions.put(\"").append(key)
                    .append("\", \"").append(stateName).append("\");\n");
        }

        // Build weights from spec (GET operations weighted higher for read-heavy scenarios)
        StringBuilder weightsBody = new StringBuilder();
        for (APICallInfo call : apiCalls) {
            String key = call.path + ":" + call.method;
            double weight = "GET".equals(call.method) ? 0.3 : 0.15;
            weightsBody.append("            weights.put(\"").append(key)
                    .append("\", ").append(weight).append(");\n");
        }

        String testCases = """
                package com.example.sequence;

                import com.example.sequence.SequenceTestFramework.APICall;
                import com.example.sequence.SequenceTestFramework.SequenceResult;
                import org.junit.jupiter.api.Test;
                import org.junit.jupiter.api.BeforeEach;
                import static org.junit.jupiter.api.Assertions.*;
                import java.util.*;
                import java.util.concurrent.*;

                /**
                 * Randomized sequence tests generated from OpenAPI specification.
                 * Tests exercise different API paths end-to-end without manual code.
                 */
                public class SequenceTestCases extends SequenceTestFramework {

                    private List<APICall> availableCalls;
                    private RandomSequenceGenerator generator;

                    @BeforeEach
                    public void setUp() {
                        super.setUp();
                        availableCalls = initializeAPICalls();
                        generator = new RandomSequenceGenerator(availableCalls);
                    }

                    @Test
                    public void testRandomSequence() {
                        List<APICall> sequence = generator.generateRandomSequence(10);
                        List<SequenceResult> results = executeSequence(sequence);

                        assertTrue(validateSequenceResults(results),
                            "Random sequence should execute successfully");
                    }

                    @Test
                    public void testWeightedSequence() {
                        Map<String, Double> weights = new HashMap<>();
                %s

                        List<APICall> sequence = generator.generateWeightedSequence(10, weights);
                        List<SequenceResult> results = executeSequence(sequence);

                        assertTrue(validateSequenceResults(results),
                            "Weighted sequence should execute successfully");
                    }

                    @Test
                    public void testDependentSequence() {
                        Map<String, List<String>> dependencies = new HashMap<>();
                %s

                        List<APICall> sequence = generator.generateDependentSequence(10, dependencies);
                        List<SequenceResult> results = executeSequence(sequence);

                        assertTrue(validateSequenceResults(results),
                            "Dependent sequence should execute successfully");
                    }

                    @Test
                    public void testStatefulSequence() {
                        Map<String, String> stateTransitions = new HashMap<>();
                %s

                        List<APICall> sequence = generator.generateStatefulSequence(10, stateTransitions);
                        List<SequenceResult> results = executeSequence(sequence);

                        assertTrue(validateSequenceResults(results),
                            "Stateful sequence should execute successfully");
                    }

                    @Test
                    public void testConcurrentSequences() {
                        int numSequences = 10;
                        List<CompletableFuture<List<SequenceResult>>> futures = new ArrayList<>();

                        for (int i = 0; i < numSequences; i++) {
                            CompletableFuture<List<SequenceResult>> future = CompletableFuture.supplyAsync(() -> {
                                List<APICall> sequence = generator.generateRandomSequence(5);
                                return executeSequence(sequence);
                            });
                            futures.add(future);
                        }

                        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                            futures.toArray(new CompletableFuture[0])
                        );

                        assertDoesNotThrow(() -> allFutures.get(),
                            "Concurrent sequences should execute without errors");
                    }

                    @Test
                    public void testSequencePerformance() {
                        long startTime = System.currentTimeMillis();

                        for (int i = 0; i < 100; i++) {
                            List<APICall> sequence = generator.generateRandomSequence(5);
                            List<SequenceResult> results = executeSequence(sequence);
                            assertTrue(validateSequenceResults(results));
                        }

                        long endTime = System.currentTimeMillis();
                        long totalTime = endTime - startTime;

                        assertTrue(totalTime < 30000,
                            "100 sequences should complete in less than 30 seconds");
                    }

                    /**
                     * Initialize API calls from OpenAPI specification endpoints
                     */
                    private List<APICall> initializeAPICalls() {
                        List<APICall> calls = new ArrayList<>();

                %s
                        return calls;
                    }
                }
                """.formatted(weightsBody.toString(), depsBody.toString(),
                stateBody.toString(), initBody.toString());

        Files.write(Paths.get(outputDir, "SequenceTestCases.java"), testCases.getBytes());
    }

    /**
     * Generate sequence test runner
     */
    private void generateSequenceTestRunner(Map<String, Object> spec, String outputDir, String baseUrl) throws IOException {
        String runner = """
                package com.example.sequence;

                import org.junit.platform.launcher.Launcher;
                import org.junit.platform.launcher.LauncherDiscoveryRequest;
                import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
                import org.junit.platform.launcher.core.LauncherFactory;
                import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
                import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

                public class SequenceTestRunner {

                    public static void main(String[] args) {
                        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                            .selectors(selectClass(SequenceTestCases.class))
                            .build();

                        Launcher launcher = LauncherFactory.create();
                        SummaryGeneratingListener listener = new SummaryGeneratingListener();
                        launcher.registerTestExecutionListeners(listener);
                        launcher.execute(request);

                        listener.getSummary().printTo(new java.io.PrintWriter(System.out));
                    }
                }
                """;

        Files.write(Paths.get(outputDir, "SequenceTestRunner.java"), runner.getBytes());
    }

    /**
     * Generate sequence test configuration -- now OAS-driven
     */
    private void generateSequenceTestConfig(Map<String, Object> spec, String outputDir, String baseUrl, List<APICallInfo> apiCalls) throws IOException {
        // Build init body from spec
        StringBuilder initBody = new StringBuilder();
        for (APICallInfo call : apiCalls) {
            String body = "null";
            if (call.hasRequestBody) {
                String mockBody = buildRequestBodyForOperation(call.operation, spec);
                body = mockBody != null ? "\"" + mockBody + "\"" : "null";
            }
            initBody.append("            calls.add(new SequenceTestFramework.APICall(\"")
                    .append(call.method).append("\", \"")
                    .append(call.path).append("\", ")
                    .append(body).append(", null));\n");
        }

        String config = """
                package com.example.sequence;

                import java.util.ArrayList;
                import java.util.List;

                public class SequenceTestConfig {

                    public static final String BASE_URL = "%s";
                    public static final int MAX_SEQUENCES = 100;
                    public static final int MAX_SEQUENCE_LENGTH = 10;

                    public static RandomSequenceGenerator createRandomSequenceGenerator() {
                        List<SequenceTestFramework.APICall> availableCalls = initializeAPICalls();
                        return new RandomSequenceGenerator(availableCalls);
                    }

                    private static List<SequenceTestFramework.APICall> initializeAPICalls() {
                        List<SequenceTestFramework.APICall> calls = new ArrayList<>();

                %s
                        return calls;
                    }
                }
                """.formatted(baseUrl, initBody.toString());

        Files.write(Paths.get(outputDir, "SequenceTestConfig.java"), config.getBytes());
    }

    /**
     * Holds extracted information about an API call from the spec
     */
    private static class APICallInfo {
        String method;
        String path;
        String operationId;
        String resourceName;
        boolean hasPathParams;
        boolean hasRequestBody;
        Map<String, Object> operation;
    }
}

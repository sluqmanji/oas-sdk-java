package egain.oassdk.test.sequence;

import egain.oassdk.Util;
import egain.oassdk.core.Constants;
import egain.oassdk.core.exceptions.GenerationException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.Locale.ROOT;

/**
 * Generates randomized sequence testing of API using OpenAPI spec.
 * Reads actual paths/operations from the OAS spec to generate endpoint-specific
 * test sequences rather than hardcoded endpoints.
 */
public class RandomizedSequenceTester {

    private static final Pattern PATH_PARAM_PATTERN = Pattern.compile("\\{([^}]+)}");

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
                    info.pathParamNames = extractPathParamNames(path);
                    info.defaultQueryParams = buildDefaultQueryParams(operation, spec);

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

    private List<String> extractPathParamNames(String path) {
        List<String> names = new ArrayList<>();
        Matcher m = PATH_PARAM_PATTERN.matcher(path);
        while (m.find()) {
            names.add(m.group(1));
        }
        return names;
    }

    private Map<String, String> buildDefaultQueryParams(Map<String, Object> operation, Map<String, Object> spec) {
        Map<String, String> map = new LinkedHashMap<>();
        for (Map<String, Object> param : listOperationParameters(operation, spec)) {
            if (!"query".equalsIgnoreCase((String) param.get("in"))) {
                continue;
            }
            String name = (String) param.get("name");
            if (name == null) {
                continue;
            }
            Map<String, Object> schema = Util.asStringObjectMap(param.get("schema"));
            if (schema == null) {
                continue;
            }
            String val = pickExampleForQueryParam(schema);
            if (val != null) {
                map.put(name, val);
            } else if (Boolean.TRUE.equals(param.get("required"))) {
                map.put(name, "1");
            }
        }
        return map;
    }

    private List<Map<String, Object>> listOperationParameters(Map<String, Object> operation, Map<String, Object> spec) {
        List<Map<String, Object>> out = new ArrayList<>();
        Object raw = operation.get("parameters");
        if (!(raw instanceof List<?> list)) {
            return out;
        }
        for (Object item : list) {
            Map<String, Object> m = Util.asStringObjectMap(item);
            if (m == null) {
                continue;
            }
            if (m.containsKey("$ref")) {
                Map<String, Object> resolved = resolveParameterRef((String) m.get("$ref"), spec);
                if (resolved != null) {
                    out.add(resolved);
                }
            } else {
                out.add(m);
            }
        }
        return out;
    }

    private Map<String, Object> resolveParameterRef(String ref, Map<String, Object> spec) {
        if (ref == null || !ref.startsWith("#/components/parameters/")) {
            return null;
        }
        String paramName = ref.substring("#/components/parameters/".length());
        Map<String, Object> components = Util.asStringObjectMap(spec.get("components"));
        if (components == null) {
            return null;
        }
        Map<String, Object> parameters = Util.asStringObjectMap(components.get("parameters"));
        if (parameters == null) {
            return null;
        }
        return Util.asStringObjectMap(parameters.get(paramName));
    }

    private String pickExampleForQueryParam(Map<String, Object> schema) {
        if (schema == null) {
            return null;
        }
        Object ex = schema.get("example");
        if (ex instanceof String s) {
            return s;
        }
        if (ex instanceof Number || ex instanceof Boolean) {
            return String.valueOf(ex);
        }
        Object def = schema.get("default");
        if (def instanceof String s) {
            return s;
        }
        if (def instanceof Number || def instanceof Boolean) {
            return String.valueOf(def);
        }
        Object enumRaw = schema.get("enum");
        if (enumRaw instanceof List<?> enums && !enums.isEmpty()) {
            Object first = enums.getFirst();
            return first != null ? String.valueOf(first) : null;
        }
        String type = (String) schema.get("type");
        if ("integer".equals(type) || "number".equals(type)) {
            Object min = schema.get("minimum");
            if (min instanceof Number n) {
                return String.valueOf(n.longValue());
            }
            return "0";
        }
        if ("boolean".equals(type)) {
            return "true";
        }
        if ("string".equals(type)) {
            return "test";
        }
        return null;
    }

    private String escapeJavaStringLiteral(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * Java expression for an immutable map (up to 5 entries); otherwise double-brace LinkedHashMap.
     */
    private String toJavaMapExpression(Map<String, String> map) {
        if (map == null || map.isEmpty()) {
            return "Collections.emptyMap()";
        }
        if (map.size() <= 5) {
            StringBuilder sb = new StringBuilder("Map.of(");
            boolean first = true;
            for (Map.Entry<String, String> e : map.entrySet()) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                sb.append('"').append(escapeJavaStringLiteral(e.getKey())).append("\", \"")
                        .append(escapeJavaStringLiteral(e.getValue())).append('"');
            }
            sb.append(')');
            return sb.toString();
        }
        StringBuilder sb = new StringBuilder("new LinkedHashMap<>() {{\n");
        for (Map.Entry<String, String> e : map.entrySet()) {
            sb.append("                put(\"")
                    .append(escapeJavaStringLiteral(e.getKey())).append("\", \"")
                    .append(escapeJavaStringLiteral(e.getValue())).append("\");\n");
        }
        sb.append("            }}");
        return sb.toString();
    }

    private void appendAPICallConstructor(
            StringBuilder sb,
            String indent,
            String concreteApicallType,
            APICallInfo call,
            Map<String, Object> spec,
            Map<String, String> queryOverride,
            int expectedStatus,
            boolean noAuth) {
        String body = "null";
        if (call.hasRequestBody) {
            String mockBody = buildRequestBodyForOperation(call.operation, spec);
            body = mockBody != null ? "\"" + mockBody + "\"" : "null";
        }
        Map<String, String> query = new LinkedHashMap<>(call.defaultQueryParams);
        if (queryOverride != null) {
            query.putAll(queryOverride);
        }
        sb.append(indent).append("new ").append(concreteApicallType).append("(\"")
                .append(call.method).append("\", \"")
                .append(escapeJavaStringLiteral(call.path)).append("\", ")
                .append(body).append(", null, ")
                .append(toJavaMapExpression(query)).append(", ")
                .append(expectedStatus).append(", ")
                .append(noAuth).append(")");
    }

    private APICallInfo findByOperationId(List<APICallInfo> apiCalls, String operationId) {
        if (operationId == null) {
            return null;
        }
        for (APICallInfo c : apiCalls) {
            if (operationId.equals(c.operationId)) {
                return c;
            }
        }
        return null;
    }

    private APICallInfo findByOperationIdContains(List<APICallInfo> apiCalls, String substring) {
        String low = substring.toLowerCase(ROOT);
        return apiCalls.stream()
                .filter(c -> c.operationId != null && c.operationId.toLowerCase(ROOT).contains(low))
                .findFirst()
                .orElse(null);
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
        Set<String> pathParamNamesUnion = new LinkedHashSet<>();
        for (APICallInfo call : apiCalls) {
            pathParamNamesUnion.addAll(call.pathParamNames);
        }
        StringBuilder bodyIdBootstrap = new StringBuilder();
        StringBuilder locationBootstrap = new StringBuilder();
        for (String n : pathParamNamesUnion) {
            String esc = escapeJavaStringLiteral(n);
            bodyIdBootstrap.append("                            state.putIfAbsent(\"")
                    .append(esc).append("\", extractedId);\n");
            locationBootstrap.append("            state.putIfAbsent(\"")
                    .append(esc).append("\", lastSegment);\n");
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
                import java.net.URI;
                import java.util.*;
                import java.util.Locale;
                import java.util.concurrent.*;

                public class SequenceTestFramework {

                    protected Client client;
                    protected Client clientNoAuth;
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
                        clientNoAuth = ClientBuilder.newClient();
                        target = client.target(baseUrl);
                        state.clear();
                        createdResources.clear();
                    }

                    @AfterEach
                    public void tearDown() {
                        List<APICall> toDelete = new ArrayList<>(createdResources);
                        Collections.reverse(toDelete);
                        for (APICall created : toDelete) {
                            try {
                                String deletePath = resolveTemplateParams(created.getPath());
                                target.path(trimLeadingSlash(deletePath)).request().delete().close();
                            } catch (Exception ignored) {
                            }
                        }
                        if (client != null) {
                            client.close();
                        }
                        if (clientNoAuth != null) {
                            clientNoAuth.close();
                        }
                    }

                    protected String trimLeadingSlash(String p) {
                        if (p != null && p.startsWith("/")) {
                            return p.substring(1);
                        }
                        return p != null ? p : "";
                    }

                    /**
                     * Execute a sequence of API calls
                     */
                    protected List<SequenceResult> executeSequence(List<APICall> sequence) {
                        List<SequenceResult> results = new ArrayList<>();

                        for (APICall call : sequence) {
                            try {
                                String resolvedPath = resolveTemplateParams(call.getPath());

                                long startTime = System.currentTimeMillis();
                                Response response = executeAPICall(call, resolvedPath);
                                long responseTime = System.currentTimeMillis() - startTime;

                                SequenceResult result = new SequenceResult(
                                    call, response, responseTime, true, null
                                );
                                results.add(result);

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

                    protected Object resolveStateForPathParam(String name) {
                        if (name == null) {
                            return null;
                        }
                        if (state.containsKey(name)) {
                            return state.get(name);
                        }
                        if (state.containsKey(name + "_id")) {
                            return state.get(name + "_id");
                        }
                        String legacy = name.toLowerCase(Locale.ROOT) + "_id";
                        for (Map.Entry<String, Object> e : state.entrySet()) {
                            if (e.getKey().equalsIgnoreCase(legacy)) {
                                return e.getValue();
                            }
                        }
                        if (state.containsKey("id")) {
                            return state.get("id");
                        }
                        if (state.containsKey("locationResourceId")) {
                            return state.get("locationResourceId");
                        }
                        for (Map.Entry<String, Object> e : state.entrySet()) {
                            if (e.getKey().endsWith("_id") && e.getValue() != null) {
                                return e.getValue();
                            }
                        }
                        return null;
                    }

                    /**
                     * Replace each {paramName} segment using state (e.g. {folderID} after create).
                     */
                    protected String resolveTemplateParams(String pathTemplate) {
                        if (pathTemplate == null || !pathTemplate.contains("{")) {
                            return pathTemplate;
                        }
                        StringBuilder sb = new StringBuilder();
                        int i = 0;
                        while (i < pathTemplate.length()) {
                            int open = pathTemplate.indexOf('{', i);
                            if (open < 0) {
                                sb.append(pathTemplate.substring(i));
                                break;
                            }
                            sb.append(pathTemplate, i, open);
                            int close = pathTemplate.indexOf('}', open + 1);
                            if (close < 0) {
                                sb.append(pathTemplate.substring(open));
                                break;
                            }
                            String name = pathTemplate.substring(open + 1, close);
                            Object v = resolveStateForPathParam(name);
                            if (v != null) {
                                sb.append(v);
                            } else {
                                sb.append("{").append(name).append("}");
                            }
                            i = close + 1;
                        }
                        return sb.toString();
                    }

                    protected void captureLocationState(Response response) {
                        if (response == null || response.getStatus() < 200 || response.getStatus() >= 300) {
                            return;
                        }
                        URI loc = response.getLocation();
                        if (loc == null) {
                            return;
                        }
                        String pathPart = loc.getPath();
                        if (pathPart == null || pathPart.isEmpty()) {
                            return;
                        }
                        int slash = pathPart.lastIndexOf('/');
                        String lastSegment = slash >= 0 ? pathPart.substring(slash + 1) : pathPart;
                        if (lastSegment.isEmpty()) {
                            return;
                        }
                        state.putIfAbsent("locationResourceId", lastSegment);
                        state.putIfAbsent("id", lastSegment);
                %s
                    }

                    /**
                     * Execute a single API call (resolved path, query params, optional unauthenticated client).
                     */
                    protected Response executeAPICall(APICall call, String resolvedPath) {
                        Client activeClient = call.isNoAuth() ? clientNoAuth : client;
                        WebTarget root = activeClient.target(baseUrl);
                        WebTarget callTarget = root.path(trimLeadingSlash(resolvedPath));
                        for (Map.Entry<String, String> qp : call.getQueryParams().entrySet()) {
                            callTarget = callTarget.queryParam(qp.getKey(), qp.getValue());
                        }

                        jakarta.ws.rs.client.Invocation.Builder builder = callTarget.request(MediaType.APPLICATION_JSON);
                        for (Map.Entry<String, String> header : call.getHeaders().entrySet()) {
                            builder = builder.header(header.getKey(), header.getValue());
                        }

                        switch (call.getMethod().toUpperCase(Locale.ROOT)) {
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
                        if (status >= 200 && status < 300) {
                            captureLocationState(response);
                        }
                        if ((status >= 200 && status < 300) && response.hasEntity()) {
                            String responseBody = response.readEntity(String.class);

                            state.put("last_response", responseBody);
                            state.put("last_status", status);

                            String extractedId = extractIdFromResponse(responseBody);

                            if (extractedId != null) {
                                state.putIfAbsent("id", extractedId);
                %s
                            }

                            if ("POST".equalsIgnoreCase(call.getMethod()) && (status == 201 || status == 200) && extractedId != null) {
                                String deletePath = call.getPath();
                                if (!deletePath.endsWith("/")) {
                                    deletePath = deletePath + "/" + extractedId;
                                } else {
                                    deletePath = deletePath + extractedId;
                                }
                                createdResources.add(new APICall("DELETE", deletePath, null, null, Collections.emptyMap(), -1, false));
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

                            if (result.getResponse() != null) {
                                int status = result.getResponse().getStatus();
                                if (status >= 500) {
                                    return false;
                                }
                                APICall call = result.getCall();
                                if (call.getExpectedStatus() >= 0 && status != call.getExpectedStatus()) {
                                    return false;
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
                        private final Map<String, String> queryParams;
                        /** When non-negative, response status must match; -1 means any non-server-error (not 5xx). */
                        private final int expectedStatus;
                        private final boolean noAuth;

                        public APICall(String method, String path, Object body, Map<String, String> headers,
                                       Map<String, String> queryParams, int expectedStatus, boolean noAuth) {
                            this.method = method;
                            this.path = path;
                            this.body = body;
                            this.headers = headers != null ? headers : new HashMap<>();
                            this.queryParams = queryParams != null ? queryParams : Collections.emptyMap();
                            this.expectedStatus = expectedStatus;
                            this.noAuth = noAuth;
                        }

                        public String getMethod() { return method; }
                        public String getPath() { return path; }
                        public Object getBody() { return body; }
                        public Map<String, String> getHeaders() { return headers; }
                        public Map<String, String> getQueryParams() { return queryParams; }
                        public int getExpectedStatus() { return expectedStatus; }
                        public boolean isNoAuth() { return noAuth; }
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
                """.formatted(baseUrl, locationBootstrap.toString(), bodyIdBootstrap.toString(), Constants.DEFAULT_MAX_RESPONSE_TIME_MS);

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
                    private final List<List<APICall>> scenarioTemplates;

                    public RandomSequenceGenerator(List<APICall> availableCalls) {
                        this(availableCalls, Collections.emptyList(), System.currentTimeMillis());
                    }

                    public RandomSequenceGenerator(List<APICall> availableCalls, long seed) {
                        this(availableCalls, Collections.emptyList(), seed);
                    }

                    public RandomSequenceGenerator(List<APICall> availableCalls, List<List<APICall>> scenarioTemplates) {
                        this(availableCalls, scenarioTemplates, System.currentTimeMillis());
                    }

                    public RandomSequenceGenerator(List<APICall> availableCalls, List<List<APICall>> scenarioTemplates, long seed) {
                        this.availableCalls = availableCalls;
                        this.scenarioTemplates = scenarioTemplates != null ? scenarioTemplates : Collections.emptyList();
                        this.random = new Random(seed);
                    }

                    /**
                     * Random sequence that often injects full Integrated-sheet scenario templates.
                     */
                    public List<APICall> generateScenarioBiasedSequence(int maxLength, double scenarioWeight) {
                        List<APICall> sequence = new ArrayList<>();
                        int targetLen = random.nextInt(Math.max(1, maxLength)) + 1;
                        while (sequence.size() < targetLen) {
                            if (!scenarioTemplates.isEmpty() && random.nextDouble() < scenarioWeight) {
                                List<APICall> template = scenarioTemplates.get(random.nextInt(scenarioTemplates.size()));
                                for (APICall step : template) {
                                    if (sequence.size() >= targetLen) {
                                        break;
                                    }
                                    sequence.add(step);
                                }
                            } else {
                                sequence.add(availableCalls.get(random.nextInt(availableCalls.size())));
                            }
                        }
                        return sequence;
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
            initBody.append("            calls.add(");
            appendAPICallConstructor(initBody, "", "APICall", call, spec, null, -1, false);
            initBody.append(");\n");
        }

        IntegratedScenarioCodegen integrated = buildIntegratedScenarioCodegen(spec, apiCalls);

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

        String biasedBlock = """

                    @Test
                    public void testScenarioBiasedSequence() {
                        List<APICall> sequence = generator.generateScenarioBiasedSequence(10, 0.45);
                        List<SequenceResult> results = executeSequence(sequence);
                        assertTrue(validateSequenceResults(results),
                            "Scenario-biased RST should satisfy validation");
                    }
                """;

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
                %s

                    @BeforeEach
                    public void setUp() {
                        super.setUp();
                        availableCalls = initializeAPICalls();
                        generator = new RandomSequenceGenerator(availableCalls, %s);
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
                %s

                    /**
                     * Initialize API calls from OpenAPI specification endpoints
                     */
                    private List<APICall> initializeAPICalls() {
                        List<APICall> calls = new ArrayList<>();

                %s
                        return calls;
                    }
                }
                """.formatted(
                integrated.helperMethods(),
                integrated.scenarioTemplateListExpr(),
                weightsBody.toString(),
                depsBody.toString(),
                stateBody.toString(),
                integrated.testMethods() + biasedBlock,
                initBody.toString());

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
            initBody.append("            calls.add(");
            appendAPICallConstructor(initBody, "", "SequenceTestFramework.APICall", call, spec, null, -1, false);
            initBody.append(");\n");
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
        List<String> pathParamNames = List.of();
        Map<String, String> defaultQueryParams = Map.of();
    }

    private record IntegratedScenarioCodegen(
            String helperMethods,
            String testMethods,
            String scenarioTemplateListExpr) {
    }

    private IntegratedScenarioCodegen buildIntegratedScenarioCodegen(
            Map<String, Object> spec, List<APICallInfo> apiCalls) {
        StringBuilder helpers = new StringBuilder();
        StringBuilder tests = new StringBuilder();
        List<String> templateRefs = new ArrayList<>();

        APICallInfo createFolder = findByOperationId(apiCalls, "createFolder");
        APICallInfo getFolder = findByOperationId(apiCalls, "getFolder");
        APICallInfo editFolder = findByOperationId(apiCalls, "editFolder");
        APICallInfo deleteFolder = findByOperationId(apiCalls, "deleteFolder");
        APICallInfo copyFolder = findByOperationIdContains(apiCalls, "copy");
        APICallInfo moveFolder = findByOperationIdContains(apiCalls, "move");
        APICallInfo permFolder = findByOperationIdContains(apiCalls, "ermission");

        emitScenarioIfPresent(helpers, tests, templateRefs, spec, "createAndGet",
                "Integrated: create and get", Arrays.asList(createFolder, getFolder),
                Arrays.asList(null, null), Arrays.asList(-1, -1), Arrays.asList(false, false));

        emitScenarioIfPresent(helpers, tests, templateRefs, spec, "editAndGet",
                "Integrated: edit and get", Arrays.asList(createFolder, editFolder, getFolder),
                Arrays.asList(null, null, null), Arrays.asList(-1, -1, -1), Arrays.asList(false, false, false));

        emitScenarioIfPresent(helpers, tests, templateRefs, spec, "deleteAndGet",
                "Integrated: delete folder and get", Arrays.asList(createFolder, deleteFolder, getFolder),
                Arrays.asList(null, null, null), Arrays.asList(-1, -1, -1), Arrays.asList(false, false, false));

        if (permFolder != null && getFolder != null) {
            emitScenarioIfPresent(helpers, tests, templateRefs, spec, "editPermissionsAndGet",
                    "Integrated: edit permissions and get", Arrays.asList(createFolder, permFolder, getFolder),
                    Arrays.asList(null, null, null), Arrays.asList(-1, -1, -1), Arrays.asList(false, false, false));
        } else {
            emitDisabledScenario(tests, "editPermissionsAndGet",
                    "No folder permission-edit operation found in spec (Integrated sheet: edit permissions and get).");
        }

        if (copyFolder != null && getFolder != null && createFolder != null) {
            emitScenarioIfPresent(helpers, tests, templateRefs, spec, "copyFolderAndGet",
                    "Integrated: copy folder and get", Arrays.asList(createFolder, copyFolder, getFolder),
                    Arrays.asList(null, null, null), Arrays.asList(-1, -1, -1), Arrays.asList(false, false, false));
        } else {
            emitDisabledScenario(tests, "copyFolderAndGet",
                    "No copy-folder operation found in spec (Integrated sheet: Copy folder and get).");
        }

        if (moveFolder != null && getFolder != null && createFolder != null) {
            emitScenarioIfPresent(helpers, tests, templateRefs, spec, "moveFolderAndGet",
                    "Integrated: move folder and get", Arrays.asList(createFolder, moveFolder, getFolder),
                    Arrays.asList(null, null, null), Arrays.asList(-1, -1, -1), Arrays.asList(false, false, false));
        } else {
            emitDisabledScenario(tests, "moveFolderAndGet",
                    "No move-folder operation found in spec (Integrated sheet: Move folder and get).");
        }

        emitDisabledScenario(tests, "getUsingDifferentUser",
                "Requires a second authenticated client; configure alternate token in generated framework (Integrated: get using different user).");
        emitDisabledScenario(tests, "editUsingDifferentUserThanCreateAndGet",
                "Requires two distinct user contexts (Integrated: edit using different user than create and get).");

        if (createFolder != null && getFolder != null) {
            Map<String, String> langQuery = pickQueryParamsMatching(getFolder, "lang");
            if (langQuery.isEmpty()) {
                langQuery = Map.of("$lang", "en-US");
            }
            emitScenarioIfPresent(helpers, tests, templateRefs, spec, "queryLangPositive",
                    "Integrated: validate query $lang", Arrays.asList(createFolder, getFolder),
                    Arrays.asList(null, langQuery), Arrays.asList(-1, -1), Arrays.asList(false, false));

            Map<String, String> levelQuery = pickQueryParamsMatching(getFolder, "level");
            if (levelQuery.isEmpty()) {
                levelQuery = Map.of("$level", "0");
            }
            emitScenarioIfPresent(helpers, tests, templateRefs, spec, "queryLevelPositive",
                    "Integrated: validate query $level", Arrays.asList(createFolder, getFolder),
                    Arrays.asList(null, levelQuery), Arrays.asList(-1, -1), Arrays.asList(false, false));

            Map<String, String> sortOrder = new LinkedHashMap<>(pickQueryParamsMatching(getFolder, "sort"));
            sortOrder.putAll(pickQueryParamsMatching(getFolder, "order"));
            if (sortOrder.isEmpty()) {
                sortOrder.put("$sort", "name");
                sortOrder.put("$order", "asc");
            }
            emitScenarioIfPresent(helpers, tests, templateRefs, spec, "querySortOrderPositive",
                    "Integrated: validate $sort and $order", Arrays.asList(createFolder, getFolder),
                    Arrays.asList(null, sortOrder), Arrays.asList(-1, -1), Arrays.asList(false, false));

            Map<String, String> pageQuery = new LinkedHashMap<>();
            pageQuery.putAll(pickQueryParamsMatching(getFolder, "page"));
            if (pageQuery.isEmpty()) {
                pageQuery.put("$pagenum", "1");
                pageQuery.put("$pagesize", "10");
            }
            emitScenarioIfPresent(helpers, tests, templateRefs, spec, "queryPaginationPositive",
                    "Integrated: validate $pagenum and $pagesize", Arrays.asList(createFolder, getFolder),
                    Arrays.asList(null, pageQuery), Arrays.asList(-1, -1), Arrays.asList(false, false));
        }

        if (getFolder != null && createFolder != null) {
            emitScenarioIfPresent(helpers, tests, templateRefs, spec, "expect401",
                    "Integrated: validate 401 without credentials", Arrays.asList(createFolder, getFolder),
                    Arrays.asList(null, null), Arrays.asList(-1, 401), Arrays.asList(false, true));
            emitDisabledScenario(tests, "expect403",
                    "Enable when running with a token missing required scope (Integrated: validate 403). Set system property egain.sequence.run403=true and use an insufficient-scope token.");
        } else {
            emitDisabledScenario(tests, "expect401",
                    "createFolder and getFolder required for unauthenticated GET scenario.");
            emitDisabledScenario(tests, "expect403", "getFolder operation not present in spec.");
        }

        String listExpr;
        if (templateRefs.isEmpty()) {
            listExpr = "Collections.emptyList()";
        } else {
            listExpr = templateRefs.stream()
                    .map(n -> "scenario" + n + "()")
                    .collect(Collectors.joining(", ", "Arrays.asList(", ")"));
        }
        return new IntegratedScenarioCodegen(helpers.toString(), tests.toString(), listExpr);
    }

    private Map<String, String> pickQueryParamsMatching(APICallInfo getOp, String needle) {
        Map<String, String> out = new LinkedHashMap<>();
        if (getOp == null) {
            return out;
        }
        String n = needle.toLowerCase(ROOT);
        for (Map.Entry<String, String> e : getOp.defaultQueryParams.entrySet()) {
            if (e.getKey().toLowerCase(ROOT).contains(n)) {
                out.put(e.getKey(), e.getValue());
            }
        }
        return out;
    }

    private void emitDisabledScenario(StringBuilder tests, String methodSuffix, String reason) {
        tests.append("    @org.junit.jupiter.api.Disabled(\"")
                .append(escapeJavaStringLiteral(reason))
                .append("\")\n    @Test\n    void testScenario_")
                .append(methodSuffix)
                .append("() {\n    }\n\n");
    }

    private void emitScenarioIfPresent(
            StringBuilder helpers,
            StringBuilder tests,
            List<String> templateRefs,
            Map<String, Object> spec,
            String methodSuffix,
            String displayComment,
            List<APICallInfo> calls,
            List<Map<String, String>> queryOverrides,
            List<Integer> expectedStatuses,
            List<Boolean> noAuthFlags) {
        if (calls.stream().anyMatch(Objects::isNull)) {
            emitDisabledScenario(tests, methodSuffix,
                    "One or more operations missing from OpenAPI spec for scenario: " + displayComment);
            return;
        }
        queryOverrides = new ArrayList<>(queryOverrides);
        expectedStatuses = new ArrayList<>(expectedStatuses);
        noAuthFlags = new ArrayList<>(noAuthFlags);
        while (queryOverrides.size() < calls.size()) {
            queryOverrides.add(null);
        }
        while (expectedStatuses.size() < calls.size()) {
            expectedStatuses.add(-1);
        }
        while (noAuthFlags.size() < calls.size()) {
            noAuthFlags.add(false);
        }

        String capSuffix = methodSuffix.substring(0, 1).toUpperCase(ROOT) + methodSuffix.substring(1);
        templateRefs.add(capSuffix);
        helpers.append("    /** ").append(escapeJavaStringLiteral(displayComment)).append(" */\n");
        helpers.append("    private List<APICall> scenario").append(capSuffix).append("() {\n");
        helpers.append("        return Arrays.asList(\n");
        for (int i = 0; i < calls.size(); i++) {
            helpers.append("            ");
            appendAPICallConstructor(helpers, "", "APICall", calls.get(i), spec, queryOverrides.get(i), expectedStatuses.get(i), noAuthFlags.get(i));
            helpers.append(i < calls.size() - 1 ? ",\n" : "\n");
        }
        helpers.append("        );\n    }\n\n");

        tests.append("    @Test\n    void testScenario_").append(methodSuffix).append("() {\n");
        tests.append("        assertTrue(validateSequenceResults(executeSequence(scenario")
                .append(capSuffix).append("())), \"")
                .append(escapeJavaStringLiteral(displayComment))
                .append("\");\n    }\n\n");
    }
}

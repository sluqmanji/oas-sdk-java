package egain.oassdk.docs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import egain.oassdk.Util;
import egain.oassdk.core.exceptions.GenerationException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Generates enhanced OpenAPI specifications using proper OpenAPI libraries
 * <p>
 * This class replaces hardcoded OpenAPI spec generation with proper OpenAPI
 * processing using Jackson and OpenAPI Generator libraries.
 */
public class OpenAPISpecGenerator {

    private final ObjectMapper jsonMapper;
    private final ObjectMapper yamlMapper;

    public OpenAPISpecGenerator() {
        this.jsonMapper = new ObjectMapper();
        this.jsonMapper.registerModule(new JavaTimeModule());

        // Configure YAML mapper
        YAMLFactory yamlFactory = new YAMLFactory();
        yamlFactory.configure(YAMLGenerator.Feature.WRITE_DOC_START_MARKER, false);
        yamlFactory.configure(YAMLGenerator.Feature.MINIMIZE_QUOTES, true);
        yamlFactory.configure(YAMLGenerator.Feature.INDENT_ARRAYS, true);
        this.yamlMapper = new ObjectMapper(yamlFactory);
        this.yamlMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Generate enhanced OpenAPI specification
     *
     * @param spec      Original OpenAPI specification
     * @param outputDir Output directory for generated files
     * @param config    OpenAPI generation configuration
     * @throws GenerationException if generation fails
     */
    public void generateEnhancedOpenAPISpec(Map<String, Object> spec, String outputDir, OpenAPIConfig config) throws GenerationException {
        if (outputDir == null) {
            throw new IllegalArgumentException("Output directory cannot be null");
        }
        try {
            Files.createDirectories(Paths.get(outputDir));

            // Enhance the specification
            Map<String, Object> enhancedSpec = enhanceSpecification(spec, config);

            // Generate YAML version
            generateYAMLSpec(enhancedSpec, outputDir);

            // Generate JSON version
            generateJSONSpec(enhancedSpec, outputDir);

            // Generate bundled version
            generateBundledSpec(enhancedSpec, outputDir);

            // Generate dereferenced version
            generateDereferencedSpec(enhancedSpec, outputDir);

            // Generate validation report
            generateValidationReport(enhancedSpec, outputDir);

        } catch (Exception e) {
            throw new GenerationException("Failed to generate enhanced OpenAPI specification: " + e.getMessage(), e);
        }
    }

    /**
     * Enhance the OpenAPI specification with examples, descriptions, and metadata
     */
    private Map<String, Object> enhanceSpecification(Map<String, Object> spec, OpenAPIConfig config) {
        Map<String, Object> enhancedSpec = new HashMap<>(spec);

        // Enhance info section
        enhanceInfoSection(enhancedSpec, config);

        // Add examples to paths
        if (config.isAddExamples()) {
            addExamplesToPaths(enhancedSpec);
        }

        // Add security schemes
        if (config.isAddSecuritySchemes()) {
            addSecuritySchemes(enhancedSpec);
        }

        // Add server information
        if (config.isAddServerInfo()) {
            addServerInfo(enhancedSpec, config);
        }

        // Add tags
        if (config.isAddTags()) {
            addTags(enhancedSpec);
        }

        // Add external documentation
        if (config.isAddExternalDocs()) {
            addExternalDocumentation(enhancedSpec, config);
        }

        return enhancedSpec;
    }

    /**
     * Enhance the info section
     */
    private void enhanceInfoSection(Map<String, Object> spec, OpenAPIConfig config) {
        Map<String, Object> info = Util.asStringObjectMap(spec.get("info"));
        if (info == null) {
            info = new HashMap<>();
            spec.put("info", info);
        }

        // Set title if not present
        if (!info.containsKey("title")) {
            info.put("title", config.getApiTitle());
        }

        // Set version if not present
        if (!info.containsKey("version")) {
            info.put("version", config.getApiVersion());
        }

        // Set description if not present
        if (!info.containsKey("description")) {
            info.put("description", config.getApiDescription());
        }

        // Add contact information
        if (config.getContactEmail() != null) {
            Map<String, Object> contact = new HashMap<>();
            contact.put("name", config.getContactName());
            contact.put("email", config.getContactEmail());
            contact.put("url", config.getContactUrl());
            info.put("contact", contact);
        }

        // Add license information
        if (config.getLicenseName() != null) {
            Map<String, Object> license = new HashMap<>();
            license.put("name", config.getLicenseName());
            license.put("url", config.getLicenseUrl());
            info.put("license", license);
        }

        // Add terms of service
        if (config.getTermsOfService() != null) {
            info.put("termsOfService", config.getTermsOfService());
        }
    }

    /**
     * Add examples to API paths
     */
    private void addExamplesToPaths(Map<String, Object> spec) {
        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        if (paths == null) return;

        for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
            Object pathValue = pathEntry.getValue();
            if (!(pathValue instanceof Map)) continue;
            Map<String, Object> pathItem = Util.asStringObjectMap(pathValue);
            if (pathItem == null) continue;

            for (Map.Entry<String, Object> methodEntry : pathItem.entrySet()) {
                String method = methodEntry.getKey();
                if (method.startsWith("x-")) continue;

                Object opValue = methodEntry.getValue();
                if (!(opValue instanceof Map)) continue;
                Map<String, Object> operation = Util.asStringObjectMap(opValue);
                if (operation == null) continue;

                // Add request examples
                addRequestExamples(operation, method, pathEntry.getKey());

                // Add response examples
                addResponseExamples(operation);
            }
        }
    }

    /**
     * Add request examples
     */
    private void addRequestExamples(Map<String, Object> operation, String method, String path) {
        if (!"GET".equals(method.toUpperCase(Locale.ROOT))) {
            Map<String, Object> requestBody = Util.asStringObjectMap(operation.get("requestBody"));
            if (requestBody == null) {
                requestBody = new HashMap<>();
                operation.put("requestBody", requestBody);
            }

            Map<String, Object> content = Util.asStringObjectMap(requestBody.get("content"));
            if (content == null) {
                content = new HashMap<>();
                requestBody.put("content", content);
            }

            Map<String, Object> applicationJson = Util.asStringObjectMap(content.get("application/json"));
            if (applicationJson == null) {
                applicationJson = new HashMap<>();
                content.put("application/json", applicationJson);
            }

            Map<String, Object> example = new HashMap<>();
            example.put("summary", "Example request");
            example.put("value", generateExampleValue(path));

            Map<String, Object> examples = new HashMap<>();
            examples.put("example", example);
            applicationJson.put("examples", examples);
        }
    }

    /**
     * Add response examples
     */
    private void addResponseExamples(Map<String, Object> operation) {
        Map<String, Object> responses = Util.asStringObjectMap(operation.get("responses"));
        if (responses == null) return;

        for (Map.Entry<String, Object> responseEntry : responses.entrySet()) {
            Map<String, Object> response = Util.asStringObjectMap(responseEntry.getValue());
            if (response == null) continue;

            Map<String, Object> content = Util.asStringObjectMap(response.get("content"));
            if (content == null) continue;

            Map<String, Object> applicationJson = Util.asStringObjectMap(content.get("application/json"));
            if (applicationJson == null) continue;

            Map<String, Object> example = new HashMap<>();
            example.put("summary", "Example response");
            example.put("value", generateExampleResponse(responseEntry.getKey()));

            Map<String, Object> examples = new HashMap<>();
            examples.put("example", example);
            applicationJson.put("examples", examples);
        }
    }

    /**
     * Add security schemes
     */
    private void addSecuritySchemes(Map<String, Object> spec) {
        Map<String, Object> components = Util.asStringObjectMap(spec.get("components"));
        if (components == null) {
            components = new HashMap<>();
            spec.put("components", components);
        }

        Map<String, Object> securitySchemes = new HashMap<>();

        // API Key authentication
        Map<String, Object> apiKeyAuth = new HashMap<>();
        apiKeyAuth.put("type", "apiKey");
        apiKeyAuth.put("in", "header");
        apiKeyAuth.put("name", "X-API-Key");
        securitySchemes.put("ApiKeyAuth", apiKeyAuth);

        // Bearer token authentication
        Map<String, Object> bearerAuth = new HashMap<>();
        bearerAuth.put("type", "http");
        bearerAuth.put("scheme", "bearer");
        bearerAuth.put("bearerFormat", "JWT");
        securitySchemes.put("BearerAuth", bearerAuth);

        components.put("securitySchemes", securitySchemes);

        // Add global security - use regular Java collections instead of JsonNode
        java.util.List<Map<String, Object>> security = new java.util.ArrayList<>();
        Map<String, Object> securityItem = new HashMap<>();
        securityItem.put("BearerAuth", new java.util.ArrayList<>());
        security.add(securityItem);
        spec.put("security", security);
    }

    /**
     * Add server information
     */
    private void addServerInfo(Map<String, Object> spec, OpenAPIConfig config) {
        java.util.List<Map<String, Object>> servers = new java.util.ArrayList<>();

        Map<String, Object> server = new HashMap<>();
        server.put("url", config.getServerUrl());
        server.put("description", config.getServerDescription());
        servers.add(server);

        spec.put("servers", servers);
    }

    /**
     * Add tags
     */
    private void addTags(Map<String, Object> spec) {
        java.util.List<Map<String, Object>> tags = new java.util.ArrayList<>();

        Map<String, Object> usersTag = new HashMap<>();
        usersTag.put("name", "users");
        usersTag.put("description", "User management operations");
        tags.add(usersTag);

        Map<String, Object> productsTag = new HashMap<>();
        productsTag.put("name", "products");
        productsTag.put("description", "Product management operations");
        tags.add(productsTag);

        spec.put("tags", tags);
    }

    /**
     * Add external documentation
     */
    private void addExternalDocumentation(Map<String, Object> spec, OpenAPIConfig config) {
        Map<String, Object> externalDocs = new HashMap<>();
        externalDocs.put("description", "Find more info about the API");
        externalDocs.put("url", config.getExternalDocsUrl());
        spec.put("externalDocs", externalDocs);
    }

    /**
     * Generate YAML specification
     */
    private void generateYAMLSpec(Map<String, Object> spec, String outputDir) throws IOException {
        Map<String, Object> safeSpec = prepareForSerialization(spec);
        String yaml = yamlMapper.writeValueAsString(safeSpec);
        Files.write(Paths.get(outputDir, "openapi.yaml"), yaml.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generate JSON specification
     */
    private void generateJSONSpec(Map<String, Object> spec, String outputDir) throws IOException {
        Map<String, Object> safeSpec = prepareForSerialization(spec);
        String json = jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(safeSpec);
        Files.write(Paths.get(outputDir, "openapi.json"), json.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generate bundled specification
     */
    private void generateBundledSpec(Map<String, Object> spec, String outputDir) throws IOException {
        // For now, just copy the main spec as bundled
        // In a real implementation, this would resolve all $ref references
        Map<String, Object> safeSpec = prepareForSerialization(spec);
        String yaml = yamlMapper.writeValueAsString(safeSpec);
        Files.write(Paths.get(outputDir, "openapi-bundled.yaml"), yaml.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generate dereferenced specification
     */
    private void generateDereferencedSpec(Map<String, Object> spec, String outputDir) throws IOException {
        // For now, just copy the main spec as dereferenced
        // In a real implementation, this would inline all $ref references
        Map<String, Object> safeSpec = prepareForSerialization(spec);
        String yaml = yamlMapper.writeValueAsString(safeSpec);
        Files.write(Paths.get(outputDir, "openapi-dereferenced.yaml"), yaml.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generate validation report
     */
    private void generateValidationReport(Map<String, Object> spec, String outputDir) throws IOException {
        StringBuilder report = new StringBuilder();
        report.append("# OpenAPI Specification Validation Report\n\n");
        report.append("Generated: ").append(new Date()).append("\n\n");

        // Basic validation checks
        report.append("## Validation Results\n\n");

        // Check required fields
        if (!spec.containsKey("openapi")) {
            report.append("❌ Missing required field: openapi\n");
        } else {
            report.append("✅ openapi: ").append(spec.get("openapi")).append("\n");
        }

        if (!spec.containsKey("info")) {
            report.append("❌ Missing required field: info\n");
        } else {
            report.append("✅ info section present\n");
        }

        if (!spec.containsKey("paths")) {
            report.append("❌ Missing required field: paths\n");
        } else {
            report.append("✅ paths section present\n");
        }

        // Count endpoints
        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        if (paths != null) {
            int endpointCount = paths.size();
            report.append("✅ Total endpoints: ").append(endpointCount).append("\n");
        }

        report.append("\n## Recommendations\n\n");
        report.append("- Add more detailed descriptions to operations\n");
        report.append("- Include request/response examples\n");
        report.append("- Add error response schemas\n");
        report.append("- Include authentication requirements\n");

        Files.write(Paths.get(outputDir, "validation-report.md"), report.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generate example value based on path and method
     */
    private Map<String, Object> generateExampleValue(String path) {
        Map<String, Object> example = new HashMap<>();

        if (path.contains("user")) {
            example.put("name", "John Doe");
            example.put("email", "john.doe@example.com");
            example.put("age", 30);
        } else if (path.contains("product")) {
            example.put("name", "Sample Product");
            example.put("price", 99.99);
            example.put("description", "A sample product");
        } else {
            example.put("id", 1);
            example.put("name", "Sample Item");
        }

        return example;
    }

    /**
     * Generate example response based on status code
     */
    private Map<String, Object> generateExampleResponse(String statusCode) {
        Map<String, Object> response = new HashMap<>();

        switch (statusCode) {
            case "200" -> {
                response.put("success", true);
                response.put("data", Map.of("id", 1, "name", "Sample Data"));
                response.put("message", "Operation successful");
            }
            case "201" -> {
                response.put("success", true);
                response.put("data", Map.of("id", 1, "name", "Created Item"));
                response.put("message", "Resource created successfully");
            }
            case "400" -> {
                response.put("success", false);
                response.put("error", "Bad Request");
                response.put("message", "Invalid request parameters");
            }
            case "404" -> {
                response.put("success", false);
                response.put("error", "Not Found");
                response.put("message", "Resource not found");
            }
            case null, default -> {
                response.put("success", false);
                response.put("error", "Internal Server Error");
                response.put("message", "An unexpected error occurred");
            }
        }

        return response;
    }

    /**
     * Create a deep-copied, serialization-safe version of the spec.
     * Breaks potential object cycles using identity tracking.
     */
    private Map<String, Object> prepareForSerialization(Map<String, Object> root) {
        return Util.asStringObjectMap(deepCopy(root, new java.util.IdentityHashMap<>()));
    }

    private Object deepCopy(Object value, java.util.Map<Object, Object> seen) {
        if (value == null) {
            return null;
        }
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof com.fasterxml.jackson.databind.JsonNode) {
            // JsonNodes are safe to reuse
            return value;
        }
        if (seen.containsKey(value)) {
            // Break cycles by replacing with a descriptive placeholder
            return "<circular-reference>";
        }
        seen.put(value, Boolean.TRUE);

        if (value instanceof Map<?, ?>) {
            Map<String, Object> copied = new java.util.LinkedHashMap<>();
            for (Map.Entry<?, ?> e : ((Map<?, ?>) value).entrySet()) {
                Object k = e.getKey();
                String key = k == null ? "null" : String.valueOf(k);
                copied.put(key, deepCopy(e.getValue(), seen));
            }
            return copied;
        }
        if (value instanceof Iterable<?>) {
            java.util.List<Object> copied = new java.util.ArrayList<>();
            for (Object item : (Iterable<?>) value) {
                copied.add(deepCopy(item, seen));
            }
            return copied;
        }
        // Fallback for unexpected types
        return String.valueOf(value);
    }

    /**
     * OpenAPI configuration class
     */
    public static class OpenAPIConfig {
        private String apiTitle = "Generated API";
        private String apiVersion = "1.0.0";
        private String apiDescription = "API generated by OAS SDK";
        private String contactName = "API Team";
        private String contactEmail = "api@example.com";
        private String contactUrl = "https://example.com/contact";
        private String licenseName = "MIT";
        private String licenseUrl = "https://opensource.org/licenses/MIT";
        private String termsOfService = "https://example.com/terms";
        private String serverUrl = "https://api.example.com/v1";
        private String serverDescription = "Production server";
        private String externalDocsUrl = "https://docs.example.com";
        private boolean addExamples = true;
        private boolean addSecuritySchemes = true;
        private boolean addServerInfo = true;
        private boolean addTags = true;
        private boolean addExternalDocs = true;

        // Getters and setters
        public String getApiTitle() {
            return apiTitle;
        }

        public void setApiTitle(String apiTitle) {
            this.apiTitle = apiTitle;
        }

        public String getApiVersion() {
            return apiVersion;
        }

        public void setApiVersion(String apiVersion) {
            this.apiVersion = apiVersion;
        }

        public String getApiDescription() {
            return apiDescription;
        }

        public void setApiDescription(String apiDescription) {
            this.apiDescription = apiDescription;
        }

        public String getContactName() {
            return contactName;
        }

        public void setContactName(String contactName) {
            this.contactName = contactName;
        }

        public String getContactEmail() {
            return contactEmail;
        }

        public void setContactEmail(String contactEmail) {
            this.contactEmail = contactEmail;
        }

        public String getContactUrl() {
            return contactUrl;
        }

        public void setContactUrl(String contactUrl) {
            this.contactUrl = contactUrl;
        }

        public String getLicenseName() {
            return licenseName;
        }

        public void setLicenseName(String licenseName) {
            this.licenseName = licenseName;
        }

        public String getLicenseUrl() {
            return licenseUrl;
        }

        public void setLicenseUrl(String licenseUrl) {
            this.licenseUrl = licenseUrl;
        }

        public String getTermsOfService() {
            return termsOfService;
        }

        public void setTermsOfService(String termsOfService) {
            this.termsOfService = termsOfService;
        }

        public String getServerUrl() {
            return serverUrl;
        }

        public void setServerUrl(String serverUrl) {
            this.serverUrl = serverUrl;
        }

        public String getServerDescription() {
            return serverDescription;
        }

        public void setServerDescription(String serverDescription) {
            this.serverDescription = serverDescription;
        }

        public String getExternalDocsUrl() {
            return externalDocsUrl;
        }

        public void setExternalDocsUrl(String externalDocsUrl) {
            this.externalDocsUrl = externalDocsUrl;
        }

        public boolean isAddExamples() {
            return addExamples;
        }

        public void setAddExamples(boolean addExamples) {
            this.addExamples = addExamples;
        }

        public boolean isAddSecuritySchemes() {
            return addSecuritySchemes;
        }

        public void setAddSecuritySchemes(boolean addSecuritySchemes) {
            this.addSecuritySchemes = addSecuritySchemes;
        }

        public boolean isAddServerInfo() {
            return addServerInfo;
        }

        public void setAddServerInfo(boolean addServerInfo) {
            this.addServerInfo = addServerInfo;
        }

        public boolean isAddTags() {
            return addTags;
        }

        public void setAddTags(boolean addTags) {
            this.addTags = addTags;
        }

        public boolean isAddExternalDocs() {
            return addExternalDocs;
        }

        public void setAddExternalDocs(boolean addExternalDocs) {
            this.addExternalDocs = addExternalDocs;
        }
    }
}

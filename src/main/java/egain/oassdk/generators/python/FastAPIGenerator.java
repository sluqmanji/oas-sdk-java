package egain.oassdk.generators.python;

import egain.oassdk.Util;
import egain.oassdk.core.Constants;
import egain.oassdk.config.GeneratorConfig;
import egain.oassdk.config.ObservabilityConfig;
import egain.oassdk.core.exceptions.GenerationException;
import egain.oassdk.generators.CodeGenerator;
import egain.oassdk.generators.ConfigurableGenerator;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * FastAPI code generator
 */
public class FastAPIGenerator implements CodeGenerator, ConfigurableGenerator {

    private GeneratorConfig config;
    // Map to store in-lined schemas: schema object -> generated model name
    private final Map<Object, String> inlinedSchemas = new java.util.IdentityHashMap<>();

    @Override
    public void generate(Map<String, Object> spec, String outputDir, GeneratorConfig config, String packageName) throws GenerationException {
        if (outputDir == null) {
            throw new IllegalArgumentException("Output directory cannot be null");
        }
        this.config = config;

        try {
            // Create directory structure
            createDirectoryStructure(outputDir, packageName);

            // Generate main application file
            generateMainApplication(spec, outputDir, packageName);

            // Collect in-lined schemas from responses before generating models
            collectInlinedSchemas(spec);

            // Generate routers
            generateRouters(spec, outputDir, packageName);

            // Generate models (including in-lined schemas)
            generateModels(spec, outputDir, packageName);

            // Generate services
            generateServices(outputDir, packageName);

            // Generate configuration
            generateConfiguration(spec, outputDir, packageName);

            // Generate exception handlers
            generateExceptionHandlers(outputDir, packageName);

            // Generate build files
            generateBuildFiles(spec, outputDir);

        } catch (Exception e) {
            throw new GenerationException("Failed to generate FastAPI application: " + e.getMessage(), e);
        }
    }

    @Override
    public String getName() {
        return "FastAPI Generator";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getLanguage() {
        return "python";
    }

    @Override
    public String getFramework() {
        return "fastapi";
    }

    @Override
    public void setConfig(GeneratorConfig config) {
        this.config = config;
    }

    @Override
    public GeneratorConfig getConfig() {
        return this.config;
    }

    /**
     * Create directory structure
     */
    private void createDirectoryStructure(String outputDir, String packageName) throws IOException {
        if (outputDir == null) {
            throw new IllegalArgumentException("Output directory cannot be null");
        }
        String packagePath = packageName != null ? packageName.replace(".", "/") : "api";

        String[] directories = {
                outputDir + "/" + packagePath,
                outputDir + "/" + packagePath + "/routers",
                outputDir + "/" + packagePath + "/models",
                outputDir + "/" + packagePath + "/services",
                outputDir + "/" + packagePath + "/config",
                outputDir + "/" + packagePath + "/exceptions",
                outputDir + "/tests"
        };

        for (String dir : directories) {
            Files.createDirectories(Paths.get(dir));
        }

        // Create __init__.py files for Python packages
        createInitFile(outputDir + "/" + packagePath);
        createInitFile(outputDir + "/" + packagePath + "/routers");
        createInitFile(outputDir + "/" + packagePath + "/models");
        createInitFile(outputDir + "/" + packagePath + "/services");
        createInitFile(outputDir + "/" + packagePath + "/config");
        createInitFile(outputDir + "/" + packagePath + "/exceptions");
    }

    /**
     * Create __init__.py file
     */
    private void createInitFile(String dirPath) throws IOException {
        writeFile(dirPath + "/__init__.py", "");
    }

    /**
     * Generate main application file
     */
    private void generateMainApplication(Map<String, Object> spec, String outputDir, String packageName) throws IOException {
        StringBuilder content = new StringBuilder();
        content.append("from fastapi import FastAPI\n");
        content.append("from fastapi.middleware.cors import CORSMiddleware\n");
        content.append("from ").append(packageName != null ? packageName : "api").append(".routers import *\n");
        content.append("from ").append(packageName != null ? packageName : "api").append(".exceptions import setup_exception_handlers\n\n");

        content.append("app = FastAPI(\n");
        content.append("    title=\"").append(getAPITitle(spec)).append("\",\n");
        content.append("    description=\"").append(getAPIDescription(spec)).append("\",\n");
        content.append("    version=\"").append(getAPIVersion(spec)).append("\"\n");
        content.append(")\n\n");

        content.append("# CORS middleware\n");
        content.append("app.add_middleware(\n");
        content.append("    CORSMiddleware,\n");
        content.append("    allow_origins=[\"*\"],\n");
        content.append("    allow_credentials=True,\n");
        content.append("    allow_methods=[\"*\"],\n");
        content.append("    allow_headers=[\"*\"],\n");
        content.append(")\n\n");

        // Observability: OpenTelemetry + Prometheus
        ObservabilityConfig obsConfig = config != null ? config.getObservabilityConfig() : null;
        if (obsConfig != null && obsConfig.isEnabled()) {
            String svcName = obsConfig.getServiceName() != null ? obsConfig.getServiceName() : getAPITitle(spec);
            if (obsConfig.isEnableTracing()) {
                content.append("# Observability: OpenTelemetry tracing\n");
                content.append("from opentelemetry import trace\n");
                content.append("from opentelemetry.sdk.trace import TracerProvider\n");
                content.append("from opentelemetry.sdk.trace.export import BatchSpanProcessor\n");
                content.append("from opentelemetry.exporter.otlp.proto.grpc.trace_exporter import OTLPSpanExporter\n");
                content.append("from opentelemetry.instrumentation.fastapi import FastAPIInstrumentor\n");
                content.append("from opentelemetry.sdk.resources import Resource\n\n");
                content.append("resource = Resource.create({\"service.name\": \"").append(svcName).append("\"})\n");
                content.append("provider = TracerProvider(resource=resource)\n");
                content.append("provider.add_span_processor(BatchSpanProcessor(OTLPSpanExporter()))\n");
                content.append("trace.set_tracer_provider(provider)\n");
                content.append("FastAPIInstrumentor.instrument_app(app)\n\n");
            }
            if (obsConfig.isEnableMetrics()) {
                content.append("# Observability: Prometheus metrics\n");
                content.append("from prometheus_fastapi_instrumentator import Instrumentator\n");
                content.append("Instrumentator().instrument(app).expose(app, endpoint=\"/metrics\")\n\n");
            }
        }

        content.append("# Setup exception handlers\n");
        content.append("setup_exception_handlers(app)\n\n");

        // Include routers dynamically
        content.append("# Include routers\n");
        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        if (paths != null) {
            Set<String> parentPaths = new HashSet<>();
            for (String path : paths.keySet()) {
                String parentPath = extractParentPath(path);
                if (!parentPaths.contains(parentPath)) {
                    parentPaths.add(parentPath);
                    String routerName = generateRouterName(parentPath);
                    content.append("app.include_router(").append(routerName.toLowerCase()).append("_router)\n");
                }
            }
        }

        content.append("\n@app.get(\"/\")\n");
        content.append("async def root():\n");
        content.append("    return {\"message\": \"API is running\"}\n");

        writeFile(outputDir + "/main.py", content.toString());
    }

    /**
     * Generate routers
     * Groups paths by parent path and generates one router per parent path
     */
    private void generateRouters(Map<String, Object> spec, String outputDir, String packageName) throws IOException {
        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        if (paths == null) return;

        String packagePath = packageName != null ? packageName.replace(".", "/") : "api";

        // Extract server base path (includes API version)
        String serverBasePath = extractServerBasePath(spec);

        // Group paths by parent path (first segment)
        Map<String, List<PathOperation>> pathGroups = new LinkedHashMap<>();

        for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
            String path = pathEntry.getKey();
            Map<String, Object> pathItem = Util.asStringObjectMap(pathEntry.getValue());

            if (pathItem == null) continue;

            // Extract parent path (first segment)
            String parentPath = extractParentPath(path);

            // Get or create the list for this parent path
            List<PathOperation> operations = pathGroups.computeIfAbsent(parentPath, k -> new ArrayList<>());

            // Add all operations from this path to the parent path group
            String[] methods = {"get", "post", "put", "delete", "patch"};
            for (String method : methods) {
                if (pathItem.containsKey(method)) {
                    Map<String, Object> operation = Util.asStringObjectMap(pathItem.get(method));
                    operations.add(new PathOperation(path, method, operation));
                }
            }
        }

        // Generate one router per parent path
        for (Map.Entry<String, List<PathOperation>> groupEntry : pathGroups.entrySet()) {
            String parentPath = groupEntry.getKey();
            List<PathOperation> operations = groupEntry.getValue();

            // Build full path with server base path (includes API version)
            String fullParentPath = buildFullPath(serverBasePath, parentPath);

            generateRouterForParentPath(fullParentPath, operations, outputDir, packagePath, packageName, serverBasePath);
        }
    }

    /**
     * Extract parent path (first segment) from a full path
     */
    private String extractParentPath(String path) {
        if (path == null || path.isEmpty() || path.equals("/")) {
            return "/";
        }

        // Remove leading slash if present
        String normalizedPath = path.startsWith("/") ? path.substring(1) : path;

        // Find the first segment
        int firstSlash = normalizedPath.indexOf('/');
        if (firstSlash == -1) {
            // Single segment path
            return "/" + normalizedPath;
        }

        // Return first segment with leading slash
        return "/" + normalizedPath.substring(0, firstSlash);
    }

    /**
     * Generate router for a parent path with all its operations
     */
    private void generateRouterForParentPath(String parentPath, List<PathOperation> operations,
                                             String outputDir, String packagePath, String packageName, String serverBasePath) throws IOException {
        String routerName = generateRouterName(parentPath);

        StringBuilder content = new StringBuilder();
        content.append("from fastapi import APIRouter, Query, Path, Header, Body, Depends, HTTPException, status\n");
        content.append("from typing import Optional, List\n");
        content.append("from ").append(packageName != null ? packageName : "api").append(".models import *\n");
        content.append("from ").append(packageName != null ? packageName : "api").append(".services import api_service\n");

        // Add security imports if any operations have security requirements
        if (hasSecurityRequirements(operations)) {
            content.append("from ").append(packageName != null ? packageName : "api").append(".security import get_current_user, check_scopes\n");
        }
        content.append("\n");

        // Use full path with API version as prefix
        content.append(routerName.toLowerCase()).append("_router = APIRouter(prefix=\"").append(parentPath).append("\")\n\n");

        // Generate route handlers for each operation
        for (PathOperation pathOp : operations) {
            // Build full path for the operation
            String fullOperationPath = serverBasePath.isEmpty() ? pathOp.path : buildFullPath(serverBasePath, pathOp.path);
            String relativePath = getRelativePath(parentPath, fullOperationPath);
            generateRouteHandler(pathOp.method, pathOp.operation, relativePath, routerName, content);
        }

        writeFile(outputDir + "/" + packagePath + "/routers/" + routerName.toLowerCase() + ".py", content.toString());
    }

    /**
     * Get relative path from parent path to full path
     */
    private String getRelativePath(String parentPath, String fullPath) {
        if (fullPath.equals(parentPath)) {
            return "";
        }

        // Remove leading slash from parent path for comparison
        String normalizedParent = parentPath.startsWith("/") ? parentPath.substring(1) : parentPath;
        String normalizedFull = fullPath.startsWith("/") ? fullPath.substring(1) : fullPath;

        if (normalizedFull.startsWith(normalizedParent + "/")) {
            return "/" + normalizedFull.substring(normalizedParent.length() + 1);
        }

        // Fallback: return the full path if parent doesn't match
        return fullPath;
    }

    /**
     * Helper class to store path and operation information
     */
    private static class PathOperation {
        String path;
        String method;
        Map<String, Object> operation;

        PathOperation(String path, String method, Map<String, Object> operation) {
            this.path = path;
            this.method = method;
            this.operation = operation;
        }
    }

    /**
     * Generate route handler
     */
    private void generateRouteHandler(String method, Map<String, Object> operation, String relativePath, String routerName, StringBuilder content) {
        String operationId = (String) operation.get("operationId");
        String summary = (String) operation.get("summary");

        // Generate function signature
        String functionName = operationId != null ? toSnakeCase(operationId) : method;
        String pathParam = relativePath.isEmpty() ? "\"\"" : "\"" + relativePath + "\"";

        // Extract parameters
        List<Map<String, Object>> params = Util.asStringObjectMapList(operation.get("parameters"));
        List<String> parameterList = new ArrayList<>();

        if (params != null) {
            for (Map<String, Object> param : params) {
                String name = (String) param.get("name");
                String in = (String) param.get("in");
                Map<String, Object> schema = Util.asStringObjectMap(param.get("schema"));
                boolean required = param.containsKey("required") ? (Boolean) param.get("required") : false;

                if (name != null && in != null && schema != null) {
                    String pythonName = toSnakeCase(name);
                    String pythonType = getPythonType(schema);

                    switch (in) {
                        case "query" -> {
                            // Build query parameter with validation
                            String paramDef = buildQueryParameterWithValidation(pythonName, pythonType, schema, required);
                            parameterList.add(paramDef);
                        }
                        case "path" -> {
                            // Path parameters are always required
                            String paramDef = pythonName + ": " + pythonType + " = Path(...)";
                            parameterList.add(paramDef);
                        }
                        case "header" -> {
                            // Header parameters
                            String defaultValue = required ? "..." : "None";
                            String optionalType = required ? pythonType : "Optional[" + pythonType + "]";
                            String paramDef = pythonName + ": " + optionalType + " = Header(" + defaultValue + ")";
                            parameterList.add(paramDef);
                        }
                        default -> {
                            // Fallback
                            String pythonType2 = getPythonType(schema);
                            String paramAnnotation = getParameterAnnotation(in, name);
                            parameterList.add(paramAnnotation + ": " + pythonType2);
                        }
                    }
                }
            }
        }

        // Add security dependency if operation has security requirements
        SecurityInfo securityInfo = extractSecurityInfo(operation);
        if (securityInfo != null && securityInfo.hasRequirements) {
            parameterList.add("current_user: dict = Depends(get_current_user)");
        }

        // Generate decorator
        content.append("@").append(routerName.toLowerCase()).append("_router.").append(method.toLowerCase()).append("(").append(pathParam).append(")\n");

        // Generate function signature
        content.append("async def ").append(functionName).append("(");
        if (!parameterList.isEmpty()) {
            content.append(String.join(", ", parameterList));
        }
        content.append("):\n");

        // Generate function body
        content.append("    \"\"\"\n");
        if (summary != null) {
            content.append("    ").append(summary).append("\n");
        }
        content.append("    \"\"\"\n");

        // Add scope checking if required
        if (securityInfo != null && !securityInfo.scopes.isEmpty()) {
            content.append("    # Check required scopes\n");
            content.append("    required_scopes = [");
            for (int i = 0; i < securityInfo.scopes.size(); i++) {
                if (i > 0) content.append(", ");
                content.append("\"").append(securityInfo.scopes.get(i)).append("\"");
            }
            content.append("]\n");
            content.append("    check_scopes(current_user, required_scopes)\n\n");
        }

        content.append("    # Implementation placeholder\n");
        content.append("    # Replace this with actual business logic implementation\n");
        content.append("    return {\"message\": \"Not implemented\"}\n\n");
    }

    /**
     * Get parameter annotation based on parameter location
     */
    private String getParameterAnnotation(String in, String name) {
        return switch (in.toLowerCase()) {
            case "path" -> "Path(..., alias=\"" + name + "\")";
            case "query" -> "Query(None, alias=\"" + name + "\")";
            case "header" -> "Header(None, alias=\"" + name + "\")";
            default -> name;
        };
    }

    /**
     * Build query parameter with FastAPI validation constraints
     */
    private String buildQueryParameterWithValidation(String pythonName, String pythonType, Map<String, Object> schema, boolean required) {
        StringBuilder paramDef = new StringBuilder();

        // Determine if type should be Optional
        String actualType = required ? pythonType : "Optional[" + pythonType + "]";
        paramDef.append(pythonName).append(": ").append(actualType).append(" = Query(");

        // Default value
        if (required) {
            paramDef.append("...");
        } else {
            paramDef.append("None");
        }

        // Add validation constraints
        String type = (String) schema.get("type");

        if ("string".equals(type)) {
            // Min/Max length
            if (schema.containsKey("minLength")) {
                paramDef.append(", min_length=").append(schema.get("minLength"));
            }
            if (schema.containsKey("maxLength")) {
                paramDef.append(", max_length=").append(schema.get("maxLength"));
            }
            // Pattern (regex)
            if (schema.containsKey("pattern")) {
                String pattern = (String) schema.get("pattern");
                paramDef.append(", regex=\"").append(pattern.replace("\"", "\\\"")).append("\"");
            }
        } else if ("integer".equals(type) || "number".equals(type)) {
            // Min/Max value
            if (schema.containsKey("minimum")) {
                paramDef.append(", ge=").append(schema.get("minimum"));
            }
            if (schema.containsKey("maximum")) {
                paramDef.append(", le=").append(schema.get("maximum"));
            }
            // Exclusive min/max
            if (schema.containsKey("exclusiveMinimum")) {
                Object exMin = schema.get("exclusiveMinimum");
                if (exMin instanceof Boolean && (Boolean) exMin) {
                    // OpenAPI 3.0 style - minimum is exclusive
                    if (schema.containsKey("minimum")) {
                        paramDef.append(", gt=").append(schema.get("minimum"));
                    }
                } else {
                    // OpenAPI 3.1 style - value is the exclusive minimum
                    paramDef.append(", gt=").append(exMin);
                }
            }
            if (schema.containsKey("exclusiveMaximum")) {
                Object exMax = schema.get("exclusiveMaximum");
                if (exMax instanceof Boolean && (Boolean) exMax) {
                    // OpenAPI 3.0 style - maximum is exclusive
                    if (schema.containsKey("maximum")) {
                        paramDef.append(", lt=").append(schema.get("maximum"));
                    }
                } else {
                    // OpenAPI 3.1 style - value is the exclusive maximum
                    paramDef.append(", lt=").append(exMax);
                }
            }
        } else if ("array".equals(type)) {
            // Min/Max items
            if (schema.containsKey("minItems")) {
                paramDef.append(", min_length=").append(schema.get("minItems"));
            }
            if (schema.containsKey("maxItems")) {
                paramDef.append(", max_length=").append(schema.get("maxItems"));
            }
        }

        // Add description if available
        if (schema.containsKey("description")) {
            String description = (String) schema.get("description");
            paramDef.append(", description=\"").append(description.replace("\"", "\\\"")).append("\"");
        }

        paramDef.append(")");

        return paramDef.toString();
    }

    /**
     * Check if any operations have security requirements
     */
    private boolean hasSecurityRequirements(List<PathOperation> operations) {
        for (PathOperation pathOp : operations) {
            if (pathOp.operation != null && pathOp.operation.containsKey("security")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Helper class to store security information
     */
    private static class SecurityInfo {
        boolean hasRequirements;
        List<String> scopes = new ArrayList<>();

        SecurityInfo(boolean hasRequirements) {
            this.hasRequirements = hasRequirements;
        }
    }

    /**
     * Extract security information from operation
     */
    private SecurityInfo extractSecurityInfo(Map<String, Object> operation) {
        if (operation == null || !operation.containsKey("security")) {
            return null;
        }

        SecurityInfo info = new SecurityInfo(true);
        List<Map<String, Object>> securityList = Util.asStringObjectMapList(operation.get("security"));

        if (securityList != null) {
            for (Map<String, Object> securityMap : securityList) {
                if (securityMap != null) {
                    for (Map.Entry<String, Object> entry : securityMap.entrySet()) {
                        // Extract scopes
                        if (entry.getValue() instanceof List<?> scopeList) {
                            for (Object scope : scopeList) {
                                if (scope instanceof String scopeStr) {
                                    // Remove ${SCOPE_PREFIX} if present
                                    scopeStr = scopeStr.replace("${SCOPE_PREFIX}", "");
                                    if (!scopeStr.isEmpty()) {
                                        info.scopes.add(scopeStr);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return info;
    }

    /**
     * Generate models
     * Only generates models that are referenced by the filtered operations
     */
    private void generateModels(Map<String, Object> spec, String outputDir, String packageName) throws IOException {
        Map<String, Object> components = Util.asStringObjectMap(spec.get("components"));
        if (components == null) return;

        Map<String, Object> schemas = Util.asStringObjectMap(components.get("schemas"));
        if (schemas == null) return;

        String packagePath = packageName != null ? packageName.replace(".", "/") : "api";

        // Collect all schema references from filtered operations
        Set<String> referencedSchemas = collectReferencedSchemas(spec);

        // Check if we should filter models
        boolean shouldFilterModels = !referencedSchemas.isEmpty();

        for (Map.Entry<String, Object> schemaEntry : schemas.entrySet()) {
            String schemaName = schemaEntry.getKey();
            Map<String, Object> schema = Util.asStringObjectMap(schemaEntry.getValue());

            // Filter out error schemas
            if (isErrorSchema(schemaName, schema)) {
                continue;
            }

            // Only filter models if we found referenced schemas
            if (shouldFilterModels && !referencedSchemas.contains(schemaName)) {
                continue;
            }

            // Convert schema name to valid Python class name
            String pythonClassName = toPythonClassName(schemaName);

            generateModel(pythonClassName, schema, outputDir, packagePath, spec);
        }
    }

    /**
     * Collect all schema names referenced by operations in the filtered spec
     */
    private Set<String> collectReferencedSchemas(Map<String, Object> spec) {
        Set<String> referencedSchemas = new HashSet<>();

        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        if (paths == null) {
            return referencedSchemas;
        }

        // Iterate through all paths and operations
        for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
            Map<String, Object> pathItem = Util.asStringObjectMap(pathEntry.getValue());
            if (pathItem == null) continue;

            // Check all HTTP methods
            String[] methods = Constants.HTTP_METHODS;
            for (String method : methods) {
                if (pathItem.containsKey(method)) {
                    Map<String, Object> operation = Util.asStringObjectMap(pathItem.get(method));
                    if (operation == null) continue;

                    // Collect from parameters
                    if (operation.containsKey("parameters")) {
                        List<Map<String, Object>> parameters = Util.asStringObjectMapList(operation.get("parameters"));
                        for (Map<String, Object> param : parameters) {
                            collectSchemasFromSchemaObject(param.get("schema"), referencedSchemas, spec);
                        }
                    }

                    // Collect from path-level parameters
                    if (pathItem.containsKey("parameters")) {
                        List<Map<String, Object>> pathParams = Util.asStringObjectMapList(pathItem.get("parameters"));
                        for (Map<String, Object> param : pathParams) {
                            collectSchemasFromSchemaObject(param.get("schema"), referencedSchemas, spec);
                        }
                    }

                    // Collect from request body
                    if (operation.containsKey("requestBody")) {
                        Map<String, Object> requestBody = Util.asStringObjectMap(operation.get("requestBody"));
                        if (requestBody.containsKey("content")) {
                            Map<String, Object> content = Util.asStringObjectMap(requestBody.get("content"));
                            for (Object mediaTypeObj : content.values()) {
                                if (mediaTypeObj instanceof Map) {
                                    Map<String, Object> mediaType = Util.asStringObjectMap(mediaTypeObj);
                                    collectSchemasFromSchemaObject(mediaType.get("schema"), referencedSchemas, spec);
                                }
                            }
                        }
                    }

                    // Collect from responses
                    if (operation.containsKey("responses")) {
                        Map<String, Object> responses = Util.asStringObjectMap(operation.get("responses"));
                        for (Object responseObj : responses.values()) {
                            if (responseObj instanceof Map) {
                                Map<String, Object> response = Util.asStringObjectMap(responseObj);
                                if (response.containsKey("content")) {
                                    Map<String, Object> content = Util.asStringObjectMap(response.get("content"));
                                    for (Object mediaTypeObj : content.values()) {
                                        if (mediaTypeObj instanceof Map) {
                                            Map<String, Object> mediaType = Util.asStringObjectMap(mediaTypeObj);
                                            collectSchemasFromSchemaObject(mediaType.get("schema"), referencedSchemas, spec);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return referencedSchemas;
    }

    /**
     * Recursively collect schema names from a schema object
     */
    @SuppressWarnings("unchecked")
    private void collectSchemasFromSchemaObject(Object schemaObj, Set<String> referencedSchemas, Map<String, Object> spec) {
        if (schemaObj == null) {
            return;
        }

        if (!(schemaObj instanceof Map)) {
            return;
        }

        Map<String, Object> schema = Util.asStringObjectMap(schemaObj);

        // Check for direct $ref
        if (schema.containsKey("$ref")) {
            String ref = (String) schema.get("$ref");
            if (ref != null && ref.startsWith("#/components/schemas/")) {
                String schemaName = ref.substring(ref.lastIndexOf("/") + 1);
                addSchemaAndCollectNested(schemaName, referencedSchemas, spec);
            }
            return;
        }

        // Check allOf, oneOf, anyOf
        for (String compositionType : new String[]{"allOf", "oneOf", "anyOf"}) {
            if (schema.containsKey(compositionType)) {
                Object compObj = schema.get(compositionType);
                if (compObj instanceof List) {
                    List<Object> compositions = (List<Object>) compObj;
                    for (Object compItem : compositions) {
                        collectSchemasFromSchemaObject(compItem, referencedSchemas, spec);
                    }
                }
            }
        }

        // Check items (for arrays)
        if (schema.containsKey("items")) {
            collectSchemasFromSchemaObject(schema.get("items"), referencedSchemas, spec);
        }

        // Check properties (for objects)
        if (schema.containsKey("properties")) {
            Object propsObj = schema.get("properties");
            if (propsObj instanceof Map) {
                Map<String, Object> properties = Util.asStringObjectMap(propsObj);
                for (Object propSchema : properties.values()) {
                    collectSchemasFromSchemaObject(propSchema, referencedSchemas, spec);
                }
            }
        }

        // Check additionalProperties
        if (schema.containsKey("additionalProperties")) {
            Object additionalProps = schema.get("additionalProperties");
            if (!(additionalProps instanceof Boolean && (Boolean) additionalProps)) {
                collectSchemasFromSchemaObject(additionalProps, referencedSchemas, spec);
            }
        }
    }

    /**
     * Add schema to referenced set and recursively collect nested schemas
     */
    private void addSchemaAndCollectNested(String schemaName, Set<String> referencedSchemas, Map<String, Object> spec) {
        if (referencedSchemas.contains(schemaName)) {
            return; // Already processed to avoid infinite loops
        }

        referencedSchemas.add(schemaName);

        // Recursively collect schemas referenced by this schema
        Map<String, Object> components = Util.asStringObjectMap(spec.get("components"));
        if (components != null) {
            Map<String, Object> schemas = Util.asStringObjectMap(components.get("schemas"));
            if (schemas != null && schemas.containsKey(schemaName)) {
                Map<String, Object> referencedSchema = Util.asStringObjectMap(schemas.get(schemaName));
                collectSchemasFromSchemaObject(referencedSchema, referencedSchemas, spec);
            }
        }
    }

    /**
     * Generate individual model (Pydantic model)
     */
    private void generateModel(String schemaName, Map<String, Object> schema, String outputDir, String packagePath,
                               Map<String, Object> spec) throws IOException {
        StringBuilder content = new StringBuilder();
        content.append("from pydantic import BaseModel, Field\n");
        content.append("from typing import Optional, List, Union, Any\n");
        content.append("from datetime import date, datetime\n\n");

        content.append("class ").append(schemaName).append("(BaseModel):\n");

        // Extract properties from schema (handling allOf, oneOf, anyOf, and direct properties)
        Map<String, Object> allProperties = new LinkedHashMap<>();
        List<String> allRequired = new ArrayList<>();

        // Handle allOf - merge properties from all schemas
        if (schema.containsKey("allOf")) {
            List<Map<String, Object>> allOfSchemas = Util.asStringObjectMapList(schema.get("allOf"));
            for (Map<String, Object> subSchema : allOfSchemas) {
                mergeSchemaProperties(subSchema, allProperties, allRequired, spec);
            }
        }
        // Handle oneOf/anyOf - merge properties from all schemas to create union type
        else if (schema.containsKey("oneOf") || schema.containsKey("anyOf")) {
            List<Map<String, Object>> schemas = Util.asStringObjectMapList(
                    schema.containsKey("oneOf") ? schema.get("oneOf") : schema.get("anyOf"));
            // Merge properties from all schemas in oneOf/anyOf to include all possible fields
            for (Map<String, Object> subSchema : schemas) {
                mergeSchemaProperties(subSchema, allProperties, allRequired, spec);
            }
        }
        // Handle direct properties
        else {
            mergeSchemaProperties(schema, allProperties, allRequired, spec);
        }

        // Generate fields from merged properties
        for (Map.Entry<String, Object> property : allProperties.entrySet()) {
            String fieldName = property.getKey();
            Map<String, Object> fieldSchema = Util.asStringObjectMap(property.getValue());

            // Generate field declaration
            content.append("    ");

            String pythonFieldName = toSnakeCase(fieldName);
            String fieldType = getPythonType(fieldSchema);

            // Check if field is required
            boolean isRequired = allRequired.contains(fieldName);

            // Generate field with Field() if name differs or if optional
            if (!fieldName.equals(pythonFieldName) || !isRequired) {
                content.append(pythonFieldName).append(": ");
                if (!isRequired) {
                    content.append("Optional[").append(fieldType).append("] = None");
                } else {
                    content.append(fieldType);
                }
                if (!fieldName.equals(pythonFieldName)) {
                    content.append(" = Field(..., alias=\"").append(fieldName).append("\")");
                }
            } else {
                content.append(pythonFieldName).append(": ").append(fieldType);
            }

            content.append("\n");
        }

        // Add Config class for Pydantic v2 compatibility
        content.append("\n    class Config:\n");
        content.append("        populate_by_name = True\n");
        content.append("        from_attributes = True\n");

        writeFile(outputDir + "/" + packagePath + "/models/" + schemaName.toLowerCase() + ".py", content.toString());
    }

    /**
     * Generate services
     */
    private void generateServices(String outputDir, String packageName) throws IOException {
        String packagePath = packageName != null ? packageName.replace(".", "/") : "api";

        String serviceContent = "# Business logic implementation placeholder\n";
        serviceContent += "# This service should contain the core business logic for the API\n";
        serviceContent += "# Implement methods that correspond to the operations defined in the OpenAPI specification\n\n";
        serviceContent += "class ApiService:\n";
        serviceContent += "    pass\n\n";
        serviceContent += "api_service = ApiService()\n";

        writeFile(outputDir + "/" + packagePath + "/services/api_service.py", serviceContent);
    }

    /**
     * Generate configuration
     */
    private void generateConfiguration(Map<String, Object> spec, String outputDir, String packageName) throws IOException {
        String packagePath = packageName != null ? packageName.replace(".", "/") : "api";

        String configContent = "from pydantic_settings import BaseSettings\n\n";
        configContent += "class Settings(BaseSettings):\n";
        configContent += "    app_name: str = \"" + getAPITitle(spec) + "\"\n";
        configContent += "    debug: bool = False\n\n";
        configContent += "    class Config:\n";
        configContent += "        env_file = \".env\"\n\n";
        configContent += "settings = Settings()\n";

        writeFile(outputDir + "/" + packagePath + "/config/settings.py", configContent);
    }

    /**
     * Generate exception handlers
     */
    private void generateExceptionHandlers(String outputDir, String packageName) throws IOException {
        String packagePath = packageName != null ? packageName.replace(".", "/") : "api";

        String exceptionContent = "from fastapi import FastAPI, Request, status\n";
        exceptionContent += "from fastapi.responses import JSONResponse\n";
        exceptionContent += "from fastapi.exceptions import RequestValidationError\n";
        exceptionContent += "import traceback\n\n";
        exceptionContent += "def setup_exception_handlers(app: FastAPI):\n";
        exceptionContent += "    @app.exception_handler(Exception)\n";
        exceptionContent += "    async def global_exception_handler(request: Request, exc: Exception):\n";
        exceptionContent += "        return JSONResponse(\n";
        exceptionContent += "            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,\n";
        exceptionContent += "            content={\"detail\": f\"An error occurred: {str(exc)}\"}\n";
        exceptionContent += "        )\n\n";
        exceptionContent += "    @app.exception_handler(RequestValidationError)\n";
        exceptionContent += "    async def validation_exception_handler(request: Request, exc: RequestValidationError):\n";
        exceptionContent += "        return JSONResponse(\n";
        exceptionContent += "            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,\n";
        exceptionContent += "            content={\"detail\": exc.errors()}\n";
        exceptionContent += "        )\n";

        writeFile(outputDir + "/" + packagePath + "/exceptions/handlers.py", exceptionContent);
    }

    /**
     * Generate build files
     */
    private void generateBuildFiles(Map<String, Object> spec, String outputDir) throws IOException {
        // Generate requirements.txt
        String requirementsContent = generateRequirementsTxt();
        writeFile(outputDir + "/requirements.txt", requirementsContent);

        // Generate .env.example
        String envContent = generateEnvExample();
        writeFile(outputDir + "/.env.example", envContent);

        // Generate README.md
        String readmeContent = generateReadme(spec);
        writeFile(outputDir + "/README.md", readmeContent);
    }

    /**
     * Generate requirements.txt
     */
    private String generateRequirementsTxt() {
        StringBuilder reqs = new StringBuilder();
        reqs.append("fastapi==0.104.1\n");
        reqs.append("uvicorn[standard]==0.24.0\n");
        reqs.append("pydantic==2.5.0\n");
        reqs.append("pydantic-settings==2.1.0\n");
        reqs.append("python-multipart==0.0.6\n");

        ObservabilityConfig obsConfig = config != null ? config.getObservabilityConfig() : null;
        if (obsConfig != null && obsConfig.isEnabled()) {
            if (obsConfig.isEnableTracing()) {
                reqs.append("opentelemetry-api==1.22.0\n");
                reqs.append("opentelemetry-sdk==1.22.0\n");
                reqs.append("opentelemetry-exporter-otlp==1.22.0\n");
                reqs.append("opentelemetry-instrumentation-fastapi==0.43b0\n");
            }
            if (obsConfig.isEnableMetrics()) {
                reqs.append("prometheus-fastapi-instrumentator==6.1.0\n");
            }
        }

        return reqs.toString();
    }

    /**
     * Generate .env.example
     */
    private String generateEnvExample() {
        return """
                # Application Settings
                DEBUG=False
                APP_NAME=API Service
                """;
    }

    /**
     * Generate README.md
     */
    private String generateReadme(Map<String, Object> spec) {
        return "# " + getAPITitle(spec) + "\n\n" +
                getAPIDescription(spec) + "\n\n" +
                "## Installation\n\n" +
                "```bash\n" +
                "pip install -r requirements.txt\n" +
                "```\n\n" +
                "## Running the Application\n\n" +
                "```bash\n" +
                "uvicorn main:app --reload\n" +
                "```\n\n" +
                "## API Documentation\n\n" +
                "Once the application is running, visit:\n" +
                "- Swagger UI: http://localhost:8000/docs\n" +
                "- ReDoc: http://localhost:8000/redoc\n";
    }

    /**
     * Helper methods
     */
    private String getAPITitle(Map<String, Object> spec) {
        Map<String, Object> info = Util.asStringObjectMap(spec.get("info"));
        return info != null ? (String) info.get("title") : "API";
    }

    private String getAPIDescription(Map<String, Object> spec) {
        Map<String, Object> info = Util.asStringObjectMap(spec.get("info"));
        return info != null ? (String) info.get("description") : "Generated API";
    }

    private String getAPIVersion(Map<String, Object> spec) {
        Map<String, Object> info = Util.asStringObjectMap(spec.get("info"));
        return info != null ? (String) info.get("version") : "1.0.0";
    }

    private String generateRouterName(String path) {
        String name = path.replaceAll("[^a-zA-Z0-9]", "");
        return name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1) + "Router";
    }

    private void writeFile(String filePath, String content) throws IOException {
        Files.write(Paths.get(filePath), content.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Merge schema properties into the allProperties map, handling $ref resolution and allOf/oneOf/anyOf
     */
    private void mergeSchemaProperties(Map<String, Object> schema, Map<String, Object> allProperties,
                                       List<String> allRequired, Map<String, Object> spec) {
        if (schema == null) return;

        // Handle $ref - resolve reference
        if (schema.containsKey("$ref")) {
            String ref = (String) schema.get("$ref");
            if (ref.startsWith("#/components/schemas/")) {
                String schemaName = ref.substring(ref.lastIndexOf("/") + 1);
                Map<String, Object> components = Util.asStringObjectMap(spec.get("components"));
                if (components != null) {
                    Map<String, Object> schemas = Util.asStringObjectMap(components.get("schemas"));
                    if (schemas != null && schemas.containsKey(schemaName)) {
                        Map<String, Object> referencedSchema = Util.asStringObjectMap(schemas.get(schemaName));
                        // Recursively merge the referenced schema (which may have allOf/oneOf/anyOf)
                        mergeSchemaProperties(referencedSchema, allProperties, allRequired, spec);
                    }
                }
            }
            return;
        }

        // Handle allOf - merge properties from all schemas
        if (schema.containsKey("allOf")) {
            List<Map<String, Object>> allOfSchemas = Util.asStringObjectMapList(schema.get("allOf"));
            for (Map<String, Object> subSchema : allOfSchemas) {
                mergeSchemaProperties(subSchema, allProperties, allRequired, spec);
            }
            return;
        }

        // Handle oneOf/anyOf - merge properties from all schemas
        if (schema.containsKey("oneOf") || schema.containsKey("anyOf")) {
            List<Map<String, Object>> schemas = Util.asStringObjectMapList(
                    schema.containsKey("oneOf") ? schema.get("oneOf") : schema.get("anyOf"));
            for (Map<String, Object> subSchema : schemas) {
                mergeSchemaProperties(subSchema, allProperties, allRequired, spec);
            }
            return;
        }

        // Merge direct properties
        if (schema.containsKey("properties")) {
            Map<String, Object> properties = Util.asStringObjectMap(schema.get("properties"));
            if (properties != null) {
                allProperties.putAll(properties);
            }
        }

        // Merge required fields
        if (schema.containsKey("required")) {
            List<String> required = Util.asStringList(schema.get("required"));
            if (required != null) {
                for (String field : required) {
                    if (!allRequired.contains(field)) {
                        allRequired.add(field);
                    }
                }
            }
        }
    }

    /**
     * Collect in-lined schemas from operation responses
     */
    private void collectInlinedSchemas(Map<String, Object> spec) {
        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        if (paths == null) return;

        for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
            String path = pathEntry.getKey();
            Map<String, Object> pathItem = Util.asStringObjectMap(pathEntry.getValue());
            if (pathItem == null) continue;

            // Process each HTTP method
            String[] methods = Constants.HTTP_METHODS;
            for (String method : methods) {
                if (pathItem.containsKey(method)) {
                    Map<String, Object> operation = Util.asStringObjectMap(pathItem.get(method));
                    collectInlinedSchemasFromOperation(operation, path, method, spec);
                }
            }
        }
    }

    /**
     * Collect in-lined schemas from a single operation
     */
    private void collectInlinedSchemasFromOperation(Map<String, Object> operation, String path, String method, Map<String, Object> spec) {
        if (operation == null || !operation.containsKey("responses")) return;

        Map<String, Object> responses = Util.asStringObjectMap(operation.get("responses"));
        if (responses == null) return;
        for (Map.Entry<String, Object> responseEntry : responses.entrySet()) {
            //String statusCode = responseEntry.getKey();
            Object responseObj = responseEntry.getValue();

            Map<String, Object> response = Util.asStringObjectMap(responseObj);
            if (response == null || !response.containsKey("content")) continue;
            Map<String, Object> content = Util.asStringObjectMap(response.get("content"));
            if (content == null) continue;

            for (Map.Entry<String, Object> mediaTypeEntry : content.entrySet()) {
                Object mediaTypeObj = mediaTypeEntry.getValue();

                Map<String, Object> mediaTypeMap = Util.asStringObjectMap(mediaTypeObj);
                if (mediaTypeMap == null) continue;

                if (!mediaTypeMap.containsKey("schema")) continue;
                Object schemaObj = mediaTypeMap.get("schema");

                if (!(schemaObj instanceof Map)) continue;
                Map<String, Object> schema = Util.asStringObjectMap(schemaObj);

                // Check if this is an inline schema (has properties but no $ref)
                if (isInlineSchema(schema)) {
                    // Generate a name for this inline schema
                    String modelName = generateInlineSchemaName(path, method, spec);

                    // Store the mapping
                    if (!inlinedSchemas.containsKey(schemaObj)) {
                        inlinedSchemas.put(schemaObj, modelName);

                        // Add to components/schemas for model generation
                        addInlineSchemaToComponents(modelName, schema, spec);
                    }
                }
            }
        }
    }

    /**
     * Check if schema is an inline schema (not a reference)
     */
    private boolean isInlineSchema(Map<String, Object> schema) {
        if (schema == null) return false;

        // If it has a $ref, it's not inline
        if (schema.containsKey("$ref")) return false;

        // If it's an object with properties, it's inline
        if ("object".equals(schema.get("type")) && schema.containsKey("properties")) {
            return true;
        }

        // If it's an array of objects with properties, check items
        if ("array".equals(schema.get("type")) && schema.containsKey("items")) {
            Object items = schema.get("items");
            Map<String, Object> itemsMap = Util.asStringObjectMap(items);
            if (itemsMap != null) {
                return !itemsMap.containsKey("$ref") &&
                        "object".equals(itemsMap.get("type")) &&
                        itemsMap.containsKey("properties");
            }
        }

        return false;
    }

    /**
     * Generate a meaningful name for inline schema
     */
    private String generateInlineSchemaName(String path, String method, Map<String, Object> spec) {
        // Try to use operationId if available
        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        if (paths != null) {
            Map<String, Object> pathItem = Util.asStringObjectMap(paths.get(path));
            if (pathItem != null) {
                Map<String, Object> operation = Util.asStringObjectMap(pathItem.get(method));
                if (operation != null && operation.containsKey("operationId")) {
                    String operationId = (String) operation.get("operationId");
                    // Convert operationId to PascalCase
                    String baseName = toPascalCase(operationId);
                    return baseName + "Response";
                }
            }
        }

        // Fallback: generate from path and method
        String pathPart = path.replaceAll("[^a-zA-Z0-9]", "");
        String methodPart = method.substring(0, 1).toUpperCase(Locale.ROOT) + method.substring(1).toLowerCase();
        return pathPart + methodPart + "Response";
    }

    /**
     * Add inline schema to components/schemas for model generation
     */
    private void addInlineSchemaToComponents(String modelName, Map<String, Object> schema, Map<String, Object> spec) {
        // Get or create components
        Map<String, Object> components = Util.asStringObjectMap(spec.get("components"));
        if (components == null) {
            components = new LinkedHashMap<>();
            spec.put("components", components);
        }

        // Get or create schemas
        Map<String, Object> schemas = Util.asStringObjectMap(components.get("schemas"));
        if (schemas == null) {
            schemas = new LinkedHashMap<>();
            components.put("schemas", schemas);
        }

        // Add the inline schema if not already present
        if (!schemas.containsKey(modelName)) {
            // Create a copy of the schema to avoid modifying the original
            Map<String, Object> schemaCopy = new LinkedHashMap<>(schema);
            schemas.put(modelName, schemaCopy);
        }
    }

    /**
     * Convert string to PascalCase
     */
    private String toPascalCase(String input) {
        if (input == null || input.isEmpty()) return input;

        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;

        for (char c : input.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                if (capitalizeNext) {
                    result.append(Character.toUpperCase(c));
                    capitalizeNext = false;
                } else {
                    result.append(c);
                }
            } else {
                capitalizeNext = true;
            }
        }

        return result.toString();
    }

    /**
     * Extract server base path from OpenAPI spec (includes API version)
     */
    private String extractServerBasePath(Map<String, Object> spec) {
        List<Map<String, Object>> servers = Util.asStringObjectMapList(spec.get("servers"));
        if (servers != null && !servers.isEmpty()) {
            Map<String, Object> firstServer = servers.getFirst();
            String url = (String) firstServer.get("url");
            if (url != null && !url.isEmpty()) {
                try {
                    // Try to parse as full URL
                    URI uri = URI.create(url);
                    java.net.URL parsedUrl = uri.toURL();
                    String path = parsedUrl.getPath();
                    return (path != null && !path.isEmpty() && !path.equals("/")) ? path : "";
                } catch (Exception e) {
                    // If not a full URL, treat as path
                    if (url.startsWith("/")) {
                        return url.equals("/") ? "" : url;
                    } else {
                        return "/" + url;
                    }
                }
            }
        }
        return "";
    }

    /**
     * Build full path with server base path
     */
    private String buildFullPath(String serverBasePath, String relativePath) {
        if (serverBasePath == null || serverBasePath.isEmpty()) {
            return relativePath;
        }

        // Ensure server base path doesn't end with /
        String normalizedBase = serverBasePath.endsWith("/")
                ? serverBasePath.substring(0, serverBasePath.length() - 1)
                : serverBasePath;

        // Ensure relative path starts with /
        String normalizedRelative = relativePath.startsWith("/") ? relativePath : "/" + relativePath;

        return normalizedBase + normalizedRelative;
    }

    /**
     * Convert OpenAPI type to Python type
     */
    private String getPythonType(Map<String, Object> schema) {
        if (schema == null) {
            return "Any";
        }

        String type = (String) schema.get("type");
        String format = (String) schema.get("format");

        switch (type) {
            case "string" -> {
                return switch (format) {
                    case "date" -> "date";
                    case "date-time" -> "datetime";
                    case null, default -> "str";
                };
            }
            case "integer" -> {
                if ("int32".equals(format)) {
                    return "int";
                } else if ("int64".equals(format)) {
                    return "int";
                } else {
                    return "int";
                }
            }
            case "number" -> {
                if ("float".equals(format)) {
                    return "float";
                } else if ("double".equals(format)) {
                    return "float";
                } else {
                    return "float";
                }
            }
            case "boolean" -> {
                return "bool";
            }
            case "array" -> {
                if (schema.containsKey("items")) {
                    Map<String, Object> items = Util.asStringObjectMap(schema.get("items"));
                    if (items != null) {
                        String itemType = getPythonType(items);
                        return "List[" + itemType + "]";
                    }
                }
                return "List[Any]";
            }
            case "object" -> {
                // Check if it's a reference to another schema
                String ref = (String) schema.get("$ref");
                if (ref != null && ref.startsWith("#/components/schemas/")) {
                    String schemaRef = ref.substring(ref.lastIndexOf("/") + 1);
                    return toPythonClassName(schemaRef);
                }
                return "dict";
            }
            case null, default -> {
                return "Any";
            }
        }
    }

    /**
     * Convert camelCase or kebab-case to snake_case
     */
    private String toSnakeCase(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    result.append('_');
                }
                result.append(Character.toLowerCase(c));
            } else if (c == '-' || c == ' ') {
                result.append('_');
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    /**
     * Check if schema is an error schema
     */
    private boolean isErrorSchema(String schemaName, Map<String, Object> schema) {
        if (schemaName == null) {
            return false;
        }

        String lowerName = schemaName.toLowerCase();
        if (lowerName.contains("error") ||
                lowerName.contains("exception") ||
                lowerName.contains("fault") ||
                lowerName.endsWith("error") ||
                lowerName.endsWith("exception")) {
            return true;
        }

        if (schema != null) {
            Object description = schema.get("description");
            if (description instanceof String descStr) {
                String desc = descStr.toLowerCase();
                return desc.contains("error") || desc.contains("exception") || desc.contains("fault");
            }
        }

        return false;
    }

    /**
     * Convert schema name to valid Python class name
     */
    private String toPythonClassName(String schemaName) {
        if (schemaName == null || schemaName.isEmpty()) {
            return "Unknown";
        }

        // Convert to PascalCase
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;

        for (char c : schemaName.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                if (capitalizeNext) {
                    result.append(Character.toUpperCase(c));
                    capitalizeNext = false;
                } else {
                    result.append(c);
                }
            } else if (c == '-' || c == '_' || c == ' ' || c == '.') {
                capitalizeNext = true;
            }
        }

        // Ensure it starts with a letter
        if (result.isEmpty() || !Character.isLetter(result.charAt(0))) {
            return "Schema" + result;
        }

        return result.toString();
    }
}

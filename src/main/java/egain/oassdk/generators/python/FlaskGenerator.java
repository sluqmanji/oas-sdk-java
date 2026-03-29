package egain.oassdk.generators.python;

import egain.oassdk.Util;
import egain.oassdk.core.Constants;
import egain.oassdk.config.GeneratorConfig;
import egain.oassdk.config.ObservabilityConfig;
import egain.oassdk.core.exceptions.GenerationException;
import egain.oassdk.core.logging.LoggerConfig;
import egain.oassdk.generators.CodeGenerator;
import egain.oassdk.generators.ConfigurableGenerator;

import java.io.IOException;
import java.util.logging.Logger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Flask code generator for Python REST APIs
 */
public class FlaskGenerator implements CodeGenerator, ConfigurableGenerator {

    private static final Logger logger = LoggerConfig.getLogger(FlaskGenerator.class);

    private GeneratorConfig config;
    // Map to store in-lined schemas: schema object -> generated model name
    private Map<Object, String> inlinedSchemas = new java.util.IdentityHashMap<>();

    @Override
    public void generate(Map<String, Object> spec, String outputDir, GeneratorConfig config, String packageName) throws GenerationException {
        if (spec == null) {
            throw new GenerationException("Specification cannot be null");
        }
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

            // Generate blueprints (routes)
            generateBlueprints(spec, outputDir, packageName);

            // Generate models (including in-lined schemas)
            generateModels(spec, outputDir, packageName);

            // Generate services
            generateServices(spec, outputDir, packageName);

            // Generate configuration
            generateConfiguration(spec, outputDir, packageName);

            // Generate exception handlers
            generateExceptionHandlers(spec, outputDir, packageName);

            // Generate build files
            generateBuildFiles(spec, outputDir, packageName);

        } catch (Exception e) {
            logger.log(java.util.logging.Level.SEVERE, "Failed to generate Flask application: " + e.getMessage(), e);
            throw new GenerationException("Failed to generate Flask application: " + e.getMessage(), e);
        }
    }

    @Override
    public String getName() {
        return "Flask Generator";
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
        return "flask";
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
                outputDir + "/" + packagePath + "/blueprints",
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
        createInitFile(outputDir + "/" + packagePath + "/blueprints");
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
        content.append("from flask import Flask\n");
        content.append("from flask_cors import CORS\n");
        content.append("from ").append(packageName != null ? packageName : "api").append(".config.settings import settings\n");
        content.append("from ").append(packageName != null ? packageName : "api").append(".exceptions.handlers import register_error_handlers\n");

        // Import blueprints
        Map<String, Object> paths = null;
        if (spec != null)
            paths = Util.asStringObjectMap(spec.get("paths"));

        if (paths != null) {
            Set<String> parentPaths = new HashSet<>();
            for (String path : paths.keySet()) {
                String parentPath = extractParentPath(path);
                if (!parentPaths.contains(parentPath)) {
                    parentPaths.add(parentPath);
                    String blueprintName = generateBlueprintName(parentPath);
                    content.append("from ").append(packageName != null ? packageName : "api")
                            .append(".blueprints.").append(blueprintName.toLowerCase())
                            .append(" import ").append(blueprintName.toLowerCase()).append("_bp\n");
                }
            }
        }

        content.append("\n\ndef create_app():\n");
        content.append("    \"\"\"Application factory pattern\"\"\"\n");
        content.append("    app = Flask(__name__)\n\n");

        content.append("    # Load configuration\n");
        content.append("    app.config.from_object(settings)\n\n");

        content.append("    # Enable CORS\n");
        content.append("    CORS(app)\n\n");

        // Observability: OpenTelemetry + Prometheus
        ObservabilityConfig obsConfig = config != null ? config.getObservabilityConfig() : null;
        if (obsConfig != null && obsConfig.isEnabled()) {
            String svcName = obsConfig.getServiceName() != null ? obsConfig.getServiceName() : getAPITitle(spec);
            if (obsConfig.isEnableTracing()) {
                content.append("    # Observability: OpenTelemetry tracing\n");
                content.append("    from opentelemetry import trace\n");
                content.append("    from opentelemetry.sdk.trace import TracerProvider\n");
                content.append("    from opentelemetry.sdk.trace.export import BatchSpanProcessor\n");
                content.append("    from opentelemetry.exporter.otlp.proto.grpc.trace_exporter import OTLPSpanExporter\n");
                content.append("    from opentelemetry.instrumentation.flask import FlaskInstrumentor\n");
                content.append("    from opentelemetry.sdk.resources import Resource\n\n");
                content.append("    resource = Resource.create({\"service.name\": \"").append(svcName).append("\"})\n");
                content.append("    provider = TracerProvider(resource=resource)\n");
                content.append("    provider.add_span_processor(BatchSpanProcessor(OTLPSpanExporter()))\n");
                content.append("    trace.set_tracer_provider(provider)\n");
                content.append("    FlaskInstrumentor().instrument_app(app)\n\n");
            }
            if (obsConfig.isEnableMetrics()) {
                content.append("    # Observability: Prometheus metrics\n");
                content.append("    from prometheus_flask_exporter import PrometheusMetrics\n");
                content.append("    metrics = PrometheusMetrics(app)\n\n");
            }
        }

        content.append("    # Register error handlers\n");
        content.append("    register_error_handlers(app)\n\n");

        // Register blueprints
        content.append("    # Register blueprints\n");
        if (paths != null) {
            Set<String> parentPaths = new HashSet<>();
            for (String path : paths.keySet()) {
                String parentPath = extractParentPath(path);
                if (!parentPaths.contains(parentPath)) {
                    parentPaths.add(parentPath);
                    String blueprintName = generateBlueprintName(parentPath);
                    content.append("    app.register_blueprint(").append(blueprintName.toLowerCase())
                            .append("_bp, url_prefix='").append(parentPath).append("')\n");
                }
            }
        }

        content.append("\n    @app.route('/')\n");
        content.append("    def index():\n");
        content.append("        return {'message': 'API is running', 'title': '").append(getAPITitle(spec)).append("'}\n\n");

        content.append("    @app.route('/health')\n");
        content.append("    def health():\n");
        content.append("        return {'status': 'healthy'}\n\n");

        content.append("    return app\n\n");

        content.append("if __name__ == '__main__':\n");
        content.append("    app = create_app()\n");
        content.append("    app.run(host='0.0.0.0', port=5000, debug=settings.DEBUG)\n");

        writeFile(outputDir + "/app.py", content.toString());
    }

    /**
     * Generate blueprints (Flask's equivalent of routers)
     * Groups paths by parent path and generates one blueprint per parent path
     */

    private void generateBlueprints(Map<String, Object> spec, String outputDir, String packageName) throws IOException {
        Map<String, Object> paths = Util.asStringObjectMap(spec == null ? null : spec.get("paths"));
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

        // Generate one blueprint per parent path
        for (Map.Entry<String, List<PathOperation>> groupEntry : pathGroups.entrySet()) {
            String parentPath = groupEntry.getKey();
            List<PathOperation> operations = groupEntry.getValue();

            // Build full path with server base path (includes API version)
            String fullParentPath = buildFullPath(serverBasePath, parentPath);

            generateBlueprintForParentPath(fullParentPath, operations, outputDir, packagePath, packageName, serverBasePath);
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
     * Generate blueprint for a parent path with all its operations
     */
    private void generateBlueprintForParentPath(String parentPath, List<PathOperation> operations,
                                                String outputDir, String packagePath, String packageName, String serverBasePath) throws IOException {
        String blueprintName = generateBlueprintName(parentPath);

        StringBuilder content = new StringBuilder();
        content.append("from flask import Blueprint, request, jsonify\n");
        content.append("from typing import Optional, List\n");
        content.append("from functools import wraps\n");
        content.append("from ").append(packageName != null ? packageName : "api").append(".models import *\n");
        content.append("from ").append(packageName != null ? packageName : "api").append(".services.api_service import api_service\n");

        // Add security imports if any operations have security requirements
        if (hasSecurityRequirements(operations)) {
            content.append("from ").append(packageName != null ? packageName : "api").append(".security import require_auth, check_scopes\n");
        }
        content.append("\n");

        content.append(blueprintName.toLowerCase()).append("_bp = Blueprint('").append(blueprintName.toLowerCase())
                .append("', __name__, url_prefix='").append(parentPath).append("')\n\n");

        // Generate route handlers for each operation
        for (PathOperation pathOp : operations) {
            // Build full path for the operation
            String fullOperationPath = serverBasePath.isEmpty() ? pathOp.path : buildFullPath(serverBasePath, pathOp.path);
            String relativePath = getRelativePath(parentPath, fullOperationPath);
            generateRouteHandler(pathOp.method, pathOp.operation, relativePath, blueprintName, content);
        }

        writeFile(outputDir + "/" + packagePath + "/blueprints/" + blueprintName.toLowerCase() + ".py", content.toString());
    }

    /**
     * Get relative path from parent path to full path
     */
    private String getRelativePath(String parentPath, String fullPath) {
        if (fullPath.equals(parentPath)) {
            return "/";
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
     * Generate route handler for Flask
     */
    private void generateRouteHandler(String method, Map<String, Object> operation, String relativePath,
                                      String blueprintName, StringBuilder content) {
        String operationId = (String) operation.get("operationId");
        String summary = (String) operation.get("summary");

        // Generate function name
        String functionName = operationId != null ? toSnakeCase(operationId) : method + "_handler";

        // Convert path parameters from OpenAPI format to Flask format
        String flaskPath = convertToFlaskPath(relativePath);

        // Extract parameters
        List<Map<String, Object>> params = Util.asStringObjectMapList(operation.get("parameters"));
        List<String> pathParams = new ArrayList<>();
        List<String> queryParams = new ArrayList<>();
        List<String> headerParams = new ArrayList<>();

        if (params != null) {
            for (Map<String, Object> param : params) {
                String name = (String) param.get("name");
                String in = (String) param.get("in");

                if (name != null && in != null) {
                    switch (in.toLowerCase()) {
                        case "path":
                            pathParams.add(name);
                            break;
                        case "query":
                            queryParams.add(name);
                            break;
                        case "header":
                            headerParams.add(name);
                            break;
                    }
                }
            }
        }

        // Extract security information
        SecurityInfo securityInfo = extractSecurityInfo(operation);

        // Generate route decorator
        content.append("@").append(blueprintName.toLowerCase()).append("_bp.route('")
                .append(flaskPath).append("', methods=['").append(method.toUpperCase(Locale.ROOT)).append("'])\n");

        // Add security decorator if needed
        if (securityInfo != null && securityInfo.hasRequirements) {
            if (!securityInfo.scopes.isEmpty()) {
                content.append("@require_auth(scopes=[");
                for (int i = 0; i < securityInfo.scopes.size(); i++) {
                    if (i > 0) content.append(", ");
                    content.append("\"").append(securityInfo.scopes.get(i)).append("\"");
                }
                content.append("])\n");
            } else {
                content.append("@require_auth()\n");
            }
        }

        // Generate function signature
        content.append("def ").append(functionName).append("(");
        if (!pathParams.isEmpty()) {
            content.append(String.join(", ", pathParams));
        }
        content.append("):\n");

        // Generate function body
        content.append("    \"\"\"\n");
        if (summary != null) {
            content.append("    ").append(summary).append("\n");
        }
        content.append("    \"\"\"\n");

        // Extract query parameters with validation
        if (!queryParams.isEmpty()) {
            for (String queryParam : queryParams) {
                // Find the param definition to get schema and validation rules
                Map<String, Object> paramDef = findParameterDefinition(params, queryParam, "query");
                if (paramDef != null) {
                    Map<String, Object> schema = Util.asStringObjectMap(paramDef.get("schema"));
                    Boolean required = paramDef.containsKey("required") ? (Boolean) paramDef.get("required") : false;

                    String varName = toSnakeCase(queryParam);
                    content.append("    ").append(varName).append(" = request.args.get('").append(queryParam).append("')\n");

                    // Generate validation code
                    if (schema != null) {
                        generateFlaskParameterValidation(content, varName, queryParam, schema, required);
                    }
                }
            }
        }

        // Extract header parameters
        if (!headerParams.isEmpty()) {
            for (String headerParam : headerParams) {
                content.append("    ").append(toSnakeCase(headerParam)).append(" = request.headers.get('")
                        .append(headerParam).append("')\n");
            }
        }

        // Extract request body for POST/PUT/PATCH
        if ("post".equalsIgnoreCase(method) || "put".equalsIgnoreCase(method) || "patch".equalsIgnoreCase(method)) {
            content.append("    data = request.get_json()\n");
        }

        content.append("\n");
        content.append("    # Implementation placeholder\n");
        content.append("    # Replace this with actual business logic implementation\n");
        content.append("    return jsonify({'message': 'Not implemented'}), 501\n\n");
    }

    /**
     * Convert OpenAPI path format to Flask path format
     * Example: /users/{id} -> /users/<id>
     */
    private String convertToFlaskPath(String path) {
        return path.replaceAll("\\{([^}]+)\\}", "<$1>");
    }

    /**
     * Find parameter definition by name and location
     */
    private Map<String, Object> findParameterDefinition(List<Map<String, Object>> params, String name, String in) {
        if (params == null) return null;

        for (Map<String, Object> param : params) {
            String paramName = (String) param.get("name");
            String paramIn = (String) param.get("in");

            if (name.equals(paramName) && in.equals(paramIn)) {
                return param;
            }
        }

        return null;
    }

    /**
     * Generate Flask parameter validation code
     */
    private void generateFlaskParameterValidation(StringBuilder content, String varName, String originalName,
                                                  Map<String, Object> schema, boolean required) {
        String type = (String) schema.get("type");

        // Required validation
        if (required) {
            content.append("    if ").append(varName).append(" is None:\n");
            content.append("        return jsonify({'error': 'Missing required parameter: ").append(originalName).append("'}), 400\n");
        }

        // Type-specific validations (only if parameter is present)
        if ("string".equals(type)) {
            // Min length
            if (schema.containsKey("minLength")) {
                int minLength = ((Number) schema.get("minLength")).intValue();
                content.append("    if ").append(varName).append(" is not None and len(").append(varName).append(") < ").append(minLength).append(":\n");
                content.append("        return jsonify({'error': 'Parameter ").append(originalName).append(" must be at least ").append(minLength).append(" characters'}), 400\n");
            }

            // Max length
            if (schema.containsKey("maxLength")) {
                int maxLength = ((Number) schema.get("maxLength")).intValue();
                content.append("    if ").append(varName).append(" is not None and len(").append(varName).append(") > ").append(maxLength).append(":\n");
                content.append("        return jsonify({'error': 'Parameter ").append(originalName).append(" must be at most ").append(maxLength).append(" characters'}), 400\n");
            }

            // Pattern
            if (schema.containsKey("pattern")) {
                String pattern = (String) schema.get("pattern");
                content.append("    import re\n");
                content.append("    if ").append(varName).append(" is not None and not re.match(r'").append(pattern.replace("'", "\\'")).append("', ").append(varName).append("):\n");
                content.append("        return jsonify({'error': 'Parameter ").append(originalName).append(" does not match required pattern'}), 400\n");
            }
        } else if ("integer".equals(type) || "number".equals(type)) {
            // Type conversion and validation
            content.append("    if ").append(varName).append(" is not None:\n");
            content.append("        try:\n");
            if ("integer".equals(type)) {
                content.append("            ").append(varName).append(" = int(").append(varName).append(")\n");
            } else {
                content.append("            ").append(varName).append(" = float(").append(varName).append(")\n");
            }
            content.append("        except ValueError:\n");
            content.append("            return jsonify({'error': 'Parameter ").append(originalName).append(" must be a valid ").append(type).append("'}), 400\n");

            // Min value
            if (schema.containsKey("minimum")) {
                Number minimum = (Number) schema.get("minimum");
                content.append("        if ").append(varName).append(" < ").append(minimum).append(":\n");
                content.append("            return jsonify({'error': 'Parameter ").append(originalName).append(" must be >= ").append(minimum).append("'}), 400\n");
            }

            // Max value
            if (schema.containsKey("maximum")) {
                Number maximum = (Number) schema.get("maximum");
                content.append("        if ").append(varName).append(" > ").append(maximum).append(":\n");
                content.append("            return jsonify({'error': 'Parameter ").append(originalName).append(" must be <= ").append(maximum).append("'}), 400\n");
            }
        }
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
     * Generate models using dataclasses (Flask-friendly approach)
     */

    private void generateModels(Map<String, Object> spec, String outputDir, String packageName) throws IOException {
        Map<String, Object> components = Util.asStringObjectMap(spec == null ? null : spec.get("components"));
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

            generateModel(pythonClassName, schema, outputDir, packagePath, packageName, spec);
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
    private void collectSchemasFromSchemaObject(Object schemaObj, Set<String> referencedSchemas, Map<String, Object> spec) {
        if (schemaObj == null || !(schemaObj instanceof Map<?, ?>)) {
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
                if (compObj instanceof List<?> compositions) {
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
    }

    /**
     * Add schema to referenced set and recursively collect nested schemas
     */
    private void addSchemaAndCollectNested(String schemaName, Set<String> referencedSchemas, Map<String, Object> spec) {
        if (referencedSchemas.contains(schemaName)) {
            return; // Already processed
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
     * Generate individual model using dataclasses
     */
    private void generateModel(String schemaName, Map<String, Object> schema, String outputDir, String packagePath,
                               String packageName, Map<String, Object> spec) throws IOException {
        StringBuilder content = new StringBuilder();
        content.append("from dataclasses import dataclass, field\n");
        content.append("from typing import Optional, List, Union, Any\n");
        content.append("from datetime import date, datetime\n\n");

        content.append("@dataclass\n");
        content.append("class ").append(schemaName).append(":\n");

        // Extract properties from schema
        Map<String, Object> allProperties = new LinkedHashMap<>();
        List<String> allRequired = new ArrayList<>();

        // Handle allOf, oneOf, anyOf, and direct properties
        if (schema.containsKey("allOf")) {
            List<Map<String, Object>> allOfSchemas = Util.asStringObjectMapList(schema.get("allOf"));
            for (Map<String, Object> subSchema : allOfSchemas) {
                mergeSchemaProperties(subSchema, allProperties, allRequired, spec);
            }
        } else if (schema.containsKey("oneOf") || schema.containsKey("anyOf")) {
            List<Map<String, Object>> schemas = Util.asStringObjectMapList(
                    schema.containsKey("oneOf") ? schema.get("oneOf") : schema.get("anyOf"));
            for (Map<String, Object> subSchema : schemas) {
                mergeSchemaProperties(subSchema, allProperties, allRequired, spec);
            }
        } else {
            mergeSchemaProperties(schema, allProperties, allRequired, spec);
        }

        // Generate fields
        if (allProperties.isEmpty()) {
            content.append("    pass\n");
        } else {
            for (Map.Entry<String, Object> property : allProperties.entrySet()) {
                String fieldName = property.getKey();
                Map<String, Object> fieldSchema = Util.asStringObjectMap(property.getValue());

                content.append("    ");
                String pythonFieldName = toSnakeCase(fieldName);
                String fieldType = getPythonType(fieldSchema);

                boolean isRequired = allRequired.contains(fieldName);

                if (!isRequired) {
                    content.append(pythonFieldName).append(": Optional[").append(fieldType).append("] = None");
                } else {
                    content.append(pythonFieldName).append(": ").append(fieldType);
                }

                content.append("\n");
            }
        }

        writeFile(outputDir + "/" + packagePath + "/models/" + schemaName.toLowerCase() + ".py", content.toString());
    }

    /**
     * Generate services
     */
    private void generateServices(Map<String, Object> spec, String outputDir, String packageName) throws IOException {
        String packagePath = packageName != null ? packageName.replace(".", "/") : "api";

        StringBuilder serviceContent = new StringBuilder();
        serviceContent.append("# Business logic implementation placeholder\n");
        serviceContent.append("# This service should contain the core business logic for the API\n\n");
        serviceContent.append("class ApiService:\n");
        serviceContent.append("    \"\"\"Core business logic service\"\"\"\n\n");
        serviceContent.append("    def __init__(self):\n");
        serviceContent.append("        pass\n\n");
        serviceContent.append("    # Add your business logic methods here\n\n");
        serviceContent.append("# Singleton instance\n");
        serviceContent.append("api_service = ApiService()\n");

        writeFile(outputDir + "/" + packagePath + "/services/api_service.py", serviceContent.toString());
    }

    /**
     * Generate configuration
     */
    private void generateConfiguration(Map<String, Object> spec, String outputDir, String packageName) throws IOException {
        String packagePath = packageName != null ? packageName.replace(".", "/") : "api";

        StringBuilder configContent = new StringBuilder();
        configContent.append("import os\n\n");
        configContent.append("class Settings:\n");
        configContent.append("    \"\"\"Application configuration\"\"\"\n");
        configContent.append("    DEBUG = os.getenv('DEBUG', 'False').lower() in ('true', '1', 't')\n");
        configContent.append("    TESTING = os.getenv('TESTING', 'False').lower() in ('true', '1', 't')\n");
        configContent.append("    SECRET_KEY = os.getenv('SECRET_KEY', 'dev-secret-key-change-in-production')\n");
        configContent.append("    APP_NAME = os.getenv('APP_NAME', '").append(getAPITitle(spec)).append("')\n");
        configContent.append("    API_VERSION = os.getenv('API_VERSION', '").append(getAPIVersion(spec)).append("')\n\n");
        configContent.append("    # Database settings\n");
        configContent.append("    DATABASE_URL = os.getenv('DATABASE_URL', 'sqlite:///app.db')\n\n");
        configContent.append("    # CORS settings\n");
        configContent.append("    CORS_ORIGINS = os.getenv('CORS_ORIGINS', '*').split(',')\n\n");
        configContent.append("settings = Settings()\n");

        writeFile(outputDir + "/" + packagePath + "/config/settings.py", configContent.toString());
    }

    /**
     * Generate exception handlers
     */
    private void generateExceptionHandlers(Map<String, Object> spec, String outputDir, String packageName) throws IOException {
        String packagePath = packageName != null ? packageName.replace(".", "/") : "api";

        StringBuilder exceptionContent = new StringBuilder();
        exceptionContent.append("from flask import jsonify\n");
        exceptionContent.append("from werkzeug.exceptions import HTTPException\n\n");
        exceptionContent.append("def register_error_handlers(app):\n");
        exceptionContent.append("    \"\"\"Register error handlers for the Flask app\"\"\"\n\n");
        exceptionContent.append("    @app.errorhandler(404)\n");
        exceptionContent.append("    def not_found_error(error):\n");
        exceptionContent.append("        return jsonify({'error': 'Resource not found'}), 404\n\n");
        exceptionContent.append("    @app.errorhandler(500)\n");
        exceptionContent.append("    def internal_error(error):\n");
        exceptionContent.append("        return jsonify({'error': 'Internal server error'}), 500\n\n");
        exceptionContent.append("    @app.errorhandler(HTTPException)\n");
        exceptionContent.append("    def handle_http_exception(error):\n");
        exceptionContent.append("        return jsonify({\n");
        exceptionContent.append("            'error': error.description,\n");
        exceptionContent.append("            'code': error.code\n");
        exceptionContent.append("        }), error.code\n\n");
        exceptionContent.append("    @app.errorhandler(Exception)\n");
        exceptionContent.append("    def handle_exception(error):\n");
        exceptionContent.append("        app.logger.error(f'Unhandled exception: {error}')\n");
        exceptionContent.append("        return jsonify({\n");
        exceptionContent.append("            'error': 'An unexpected error occurred',\n");
        exceptionContent.append("            'message': str(error)\n");
        exceptionContent.append("        }), 500\n");

        writeFile(outputDir + "/" + packagePath + "/exceptions/handlers.py", exceptionContent.toString());
    }

    /**
     * Generate build files
     */
    private void generateBuildFiles(Map<String, Object> spec, String outputDir, String packageName) throws IOException {
        // Generate requirements.txt
        writeFile(outputDir + "/requirements.txt", generateRequirementsTxt());

        // Generate .env.example
        writeFile(outputDir + "/.env.example", generateEnvExample());

        // Generate README.md
        writeFile(outputDir + "/README.md", generateReadme(spec, packageName));

        // Generate wsgi.py for production deployment
        writeFile(outputDir + "/wsgi.py", generateWsgi());
    }

    /**
     * Generate requirements.txt
     */
    private String generateRequirementsTxt() {
        StringBuilder reqs = new StringBuilder();
        reqs.append("Flask==3.0.0\n");
        reqs.append("Flask-CORS==4.0.0\n");
        reqs.append("python-dotenv==1.0.0\n");
        reqs.append("gunicorn==21.2.0\n");
        reqs.append("marshmallow==3.20.1\n");

        ObservabilityConfig obsConfig = config != null ? config.getObservabilityConfig() : null;
        if (obsConfig != null && obsConfig.isEnabled()) {
            if (obsConfig.isEnableTracing()) {
                reqs.append("opentelemetry-api==1.22.0\n");
                reqs.append("opentelemetry-sdk==1.22.0\n");
                reqs.append("opentelemetry-exporter-otlp==1.22.0\n");
                reqs.append("opentelemetry-instrumentation-flask==0.43b0\n");
            }
            if (obsConfig.isEnableMetrics()) {
                reqs.append("prometheus-flask-exporter==0.23.0\n");
            }
        }

        return reqs.toString();
    }

    /**
     * Generate .env.example
     */
    private String generateEnvExample() {
        return "# Application Settings\n" +
                "DEBUG=False\n" +
                "SECRET_KEY=your-secret-key-here\n" +
                "APP_NAME=API Service\n" +
                "API_VERSION=1.0.0\n\n" +
                "# Database Settings\n" +
                "DATABASE_URL=sqlite:///app.db\n\n" +
                "# CORS Settings\n" +
                "CORS_ORIGINS=*\n";
    }

    /**
     * Generate README.md
     */
    private String generateReadme(Map<String, Object> spec, String packageName) {
        return "# " + getAPITitle(spec) + "\n\n" +
                getAPIDescription(spec) + "\n\n" +
                "## Installation\n\n" +
                "```bash\n" +
                "pip install -r requirements.txt\n" +
                "```\n\n" +
                "## Configuration\n\n" +
                "Copy `.env.example` to `.env` and update with your settings:\n\n" +
                "```bash\n" +
                "cp .env.example .env\n" +
                "```\n\n" +
                "## Running the Application\n\n" +
                "### Development\n\n" +
                "```bash\n" +
                "python app.py\n" +
                "```\n\n" +
                "Or using Flask CLI:\n\n" +
                "```bash\n" +
                "export FLASK_APP=app.py\n" +
                "export FLASK_ENV=development\n" +
                "flask run\n" +
                "```\n\n" +
                "### Production\n\n" +
                "```bash\n" +
                "gunicorn -w 4 -b 0.0.0.0:5000 wsgi:app\n" +
                "```\n\n" +
                "## API Documentation\n\n" +
                "The API will be available at http://localhost:5000\n\n" +
                "## Testing\n\n" +
                "```bash\n" +
                "pytest tests/\n" +
                "```\n";
    }

    /**
     * Generate wsgi.py for production deployment
     */
    private String generateWsgi() {
        return "from app import create_app\n\n" +
                "app = create_app()\n\n" +
                "if __name__ == '__main__':\n" +
                "    app.run()\n";
    }

    /**
     * Helper methods
     */
    private String getAPITitle(Map<String, Object> spec) {
        Map<String, Object> info = Util.asStringObjectMap(spec == null ? null : spec.get("info"));
        return info != null ? (String) info.get("title") : "API";
    }

    private String getAPIDescription(Map<String, Object> spec) {

        Map<String, Object> info = Util.asStringObjectMap(spec != null ? spec.get("info") : null);
        return info != null ? (String) info.get("description") : "Generated API";
    }

    private String getAPIVersion(Map<String, Object> spec) {

        Map<String, Object> info = Util.asStringObjectMap(spec == null ? null : spec.get("info"));
        return info != null ? (String) info.get("version") : "1.0.0";
    }

    private String generateBlueprintName(String path) {
        String name = path.replaceAll("[^a-zA-Z0-9]", "");
        return name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1) + "Blueprint";
    }

    private void writeFile(String filePath, String content) throws IOException {
        Files.write(Paths.get(filePath), content.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Merge schema properties
     */
    private void mergeSchemaProperties(Map<String, Object> schema, Map<String, Object> allProperties,
                                       List<String> allRequired, Map<String, Object> spec) {
        if (schema == null) return;

        // Handle $ref
        if (schema.containsKey("$ref")) {
            String ref = (String) schema.get("$ref");
            if (ref.startsWith("#/components/schemas/")) {
                String schemaName = ref.substring(ref.lastIndexOf("/") + 1);
                Map<String, Object> components = Util.asStringObjectMap(spec.get("components"));
                if (components != null) {
                    Map<String, Object> schemas = Util.asStringObjectMap(components.get("schemas"));
                    if (schemas != null && schemas.containsKey(schemaName)) {
                        Map<String, Object> referencedSchema = Util.asStringObjectMap(schemas.get(schemaName));
                        mergeSchemaProperties(referencedSchema, allProperties, allRequired, spec);
                    }
                }
            }
            return;
        }

        // Handle allOf
        if (schema.containsKey("allOf")) {
            List<Map<String, Object>> allOfSchemas = Util.asStringObjectMapList(schema.get("allOf"));
            for (Map<String, Object> subSchema : allOfSchemas) {
                mergeSchemaProperties(subSchema, allProperties, allRequired, spec);
            }
            return;
        }

        // Handle oneOf/anyOf
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
        Map<String, Object> paths = Util.asStringObjectMap(spec == null ? null : spec.get("paths"));
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
            String statusCode = responseEntry.getKey();
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
                    String modelName = generateInlineSchemaName(path, method, statusCode, spec);

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
    private String generateInlineSchemaName(String path, String method, String statusCode, Map<String, Object> spec) {
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
            Map<String, Object> firstServer = servers.get(0);
            String url = (String) firstServer.get("url");
            if (url != null && !url.isEmpty()) {
                try {
                    // Try to parse as full URL
                    java.net.URI uri = java.net.URI.create(url);
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

        if ("string".equals(type)) {
            if ("date".equals(format)) return "date";
            if ("date-time".equals(format)) return "datetime";
            return "str";
        } else if ("integer".equals(type)) {
            return "int";
        } else if ("number".equals(type)) {
            return "float";
        } else if ("boolean".equals(type)) {
            return "bool";
        } else if ("array".equals(type)) {
            if (schema.containsKey("items")) {
                Map<String, Object> items = Util.asStringObjectMap(schema.get("items"));
                if (items != null) {
                    String itemType = getPythonType(items);
                    return "List[" + itemType + "]";
                }
            }
            return "List[Any]";
        } else if ("object".equals(type)) {
            String ref = (String) schema.get("$ref");
            if (ref != null && ref.startsWith("#/components/schemas/")) {
                String schemaRef = ref.substring(ref.lastIndexOf("/") + 1);
                return toPythonClassName(schemaRef);
            }
            return "dict";
        }
        return "Any";
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
        return lowerName.contains("error") ||
                lowerName.contains("exception") ||
                lowerName.contains("fault");
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
        if (result.length() == 0 || !Character.isLetter(result.charAt(0))) {
            return "Schema" + result.toString();
        }

        return result.toString();
    }
}


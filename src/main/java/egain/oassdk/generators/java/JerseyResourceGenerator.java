package egain.oassdk.generators.java;

import egain.oassdk.Util;
import egain.oassdk.core.exceptions.GenerationException;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;

/**
 * Generates JAX-RS resource (controller) classes from OpenAPI path definitions.
 * Each resource groups operations by their first path segment (parent path).
 */
class JerseyResourceGenerator {

    private final JerseyGenerationContext ctx;
    /**
     * Function to resolve an OpenAPI schema map to a Java type string.
     * Provided by JerseyGenerator until JerseyTypeUtils is extracted.
     */
    private final Function<Map<String, Object>, String> javaTypeResolver;

    JerseyResourceGenerator(JerseyGenerationContext ctx, Function<Map<String, Object>, String> javaTypeResolver) {
        this.ctx = ctx;
        this.javaTypeResolver = javaTypeResolver;
    }

    /**
     * Generate resource classes for all operations in the spec.
     */
    void generate() throws IOException, GenerationException {
        generateResources(ctx.spec, ctx.outputDir, ctx.packageName);
    }

    /**
     * Group paths by parent and generate one resource class per parent path.
     */
    private void generateResources(Map<String, Object> spec, String outputDir, String packageName) throws IOException, GenerationException {
        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        if (paths == null) return;

        String packagePath = packageName != null ? packageName : "com.example.api";

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
                    if (operation != null) {
                        operations.add(new PathOperation(path, method, operation));
                    }
                }
            }
        }

        // Generate one resource per parent path
        for (Map.Entry<String, List<PathOperation>> groupEntry : pathGroups.entrySet()) {
            String parentPath = groupEntry.getKey();
            List<PathOperation> operations = groupEntry.getValue();

            try {
                generateResourceForParentPath(parentPath, operations, outputDir, packagePath, spec);
            } catch (GenerationException e) {
                throw new GenerationException("Failed to generate resource for path " + parentPath + ": " + e.getMessage(), e);
            }
        }
    }

    /**
     * Extract parent path (first segment) from a full path.
     * Examples:
     * "/users" -> "/users"
     * "/users/{id}" -> "/users"
     * "/users/{id}/posts" -> "/users"
     * "/api/v1/users" -> "/api"
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
     * Extract API path with version from parent path, operations, and server URL.
     * Looks for version patterns like /v1, /v2, /v4 in the server URL or paths.
     * Examples:
     * parentPath="/prompts", server URL="/knowledge/contentmgr/v4"
     * -> "/knowledge/contentmgr/v4/prompts"
     * parentPath="/articles", no version -> "/articles"
     */
    private String extractApiPathWithVersion(String parentPath, List<PathOperation> operations, Map<String, Object> spec) {
        String serverBasePath = JerseyGenerationContext.extractServerBasePath(spec);

        String normalizedParent = parentPath.startsWith("/") ? parentPath : "/" + parentPath;
        if (serverBasePath != null && !serverBasePath.isEmpty()) {
            // Server URL has a base path, combine it with parent path
            if (serverBasePath.endsWith("/")) {
                return serverBasePath + normalizedParent.substring(1);
            } else {
                return serverBasePath + normalizedParent;
            }
        }

        // Fallback: Look for version pattern in the first operation's path
        if (operations != null && !operations.isEmpty()) {
            String firstPath = operations.getFirst().path;
            if (firstPath != null) {
                // Check for version pattern like /v1, /v2, /v4, etc.
                java.util.regex.Pattern versionPattern = java.util.regex.Pattern.compile("(/v\\d+)");
                java.util.regex.Matcher matcher = versionPattern.matcher(firstPath);

                if (matcher.find()) {
                    // Extract the path up to and including the version
                    int versionEnd = matcher.end();
                    String basePath = firstPath.substring(0, versionEnd);

                    // Find where the parent path starts in the full path
                    int parentIndex = firstPath.indexOf(normalizedParent, versionEnd);

                    if (parentIndex != -1) {
                        // Include the parent path in the result
                        int parentEnd = parentIndex + normalizedParent.length();
                        return firstPath.substring(0, parentEnd);
                    } else {
                        // Parent path not found after version, append it
                        if (basePath.endsWith("/")) {
                            return basePath + normalizedParent.substring(1);
                        } else {
                            return basePath + normalizedParent;
                        }
                    }
                }
            }
        }

        // No version found, return parent path as is
        return parentPath;
    }

    /**
     * Extract security information from operations and generate @Actor annotation.
     */
    private String generateActorAnnotation(List<PathOperation> operations) {
        Set<String> actorTypes = new HashSet<>();
        Set<String> scopes = new HashSet<>();

        // Map security scheme names to ActorType enum values
        Map<String, String> securitySchemeToActorType = new LinkedHashMap<>();
        securitySchemeToActorType.put("oAuthUser", "USER");
        securitySchemeToActorType.put("oAuthCustomer", "CUSTOMER");
        securitySchemeToActorType.put("oAuthAnonymousCustomer", "ANONYMOUS_CUSTOMER");
        securitySchemeToActorType.put("oAuthClient", "CLIENT_APP");

        // Extract security information from all operations
        for (PathOperation pathOp : operations) {
            Map<String, Object> operation = pathOp.operation;
            if (operation != null && operation.containsKey("security")) {
                List<Map<String, Object>> securityList = Util.asStringObjectMapList(operation.get("security"));
                if (securityList != null) {
                    for (Map<String, Object> securityMap : securityList) {
                        if (securityMap != null) {
                            for (Map.Entry<String, Object> entry : securityMap.entrySet()) {
                                String schemeName = entry.getKey();
                                String actorType = securitySchemeToActorType.get(schemeName);
                                if (actorType != null) {
                                    actorTypes.add(actorType);
                                }

                                // Extract scopes
                                if (entry.getValue() instanceof List<?> scopeList) {
                                    for (Object scope : scopeList) {
                                        if (scope instanceof String scopeStr) {
                                            // Remove ${SCOPE_PREFIX} if present and convert to enum format
                                            scopeStr = scopeStr.replace("${SCOPE_PREFIX}", "");
                                            // Convert scope to enum format: knowledge.contentmgr.read -> KNOWLEDGE_CONTENTMGR_READ
                                            String enumScope = convertScopeToEnum(scopeStr);
                                            if (enumScope != null && !enumScope.isEmpty()) {
                                                scopes.add(enumScope);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // If no security found, return default @Actor
        if (actorTypes.isEmpty() && scopes.isEmpty()) {
            return "@Actor\n";
        }

        // Build @Actor annotation
        StringBuilder actorAnnotation = new StringBuilder("@Actor(type = { ");

        // Add actor types
        if (!actorTypes.isEmpty()) {
            List<String> sortedActorTypes = new ArrayList<>(actorTypes);
            java.util.Collections.sort(sortedActorTypes);
            for (int i = 0; i < sortedActorTypes.size(); i++) {
                if (i > 0) actorAnnotation.append(", ");
                actorAnnotation.append("ActorType.").append(sortedActorTypes.get(i));
            }
        } else {
            // Default to CLIENT_APP if no actor types found
            actorAnnotation.append("ActorType.CLIENT_APP");
        }

        actorAnnotation.append(" }, scope = {");

        // Add scopes
        if (!scopes.isEmpty()) {
            List<String> sortedScopes = new ArrayList<>(scopes);
            java.util.Collections.sort(sortedScopes);
            actorAnnotation.append("\n");
            for (int i = 0; i < sortedScopes.size(); i++) {
                if (i > 0) actorAnnotation.append(",\n");
                actorAnnotation.append("\t\tOAuthScope.").append(sortedScopes.get(i));
            }
            actorAnnotation.append("\n\t");
        }

        actorAnnotation.append("})\n");

        return actorAnnotation.toString();
    }

    /**
     * Convert scope string to enum format.
     * Examples:
     * knowledge.contentmgr.read -> KNOWLEDGE_CONTENTMGR_READ
     * knowledge.portalmgr.manage -> KNOWLEDGE_PORTALMGR_MANAGE
     */
    private String convertScopeToEnum(String scope) {
        if (scope == null || scope.isEmpty()) {
            return null;
        }

        // Replace dots and hyphens with underscores, convert to uppercase
        String enumScope = scope.replace(".", "_").replace("-", "_").toUpperCase(Locale.ROOT);

        // Remove any remaining special characters that aren't valid in enum names
        enumScope = enumScope.replaceAll("[^A-Z0-9_]", "");

        return enumScope;
    }

    /**
     * Generate resource for a parent path with all its operations.
     */
    private void generateResourceForParentPath(String parentPath, List<PathOperation> operations,
                                               String outputDir, String packagePath, Map<String, Object> spec) throws IOException, GenerationException {
        String resourceName = generateResourceName(parentPath);

        StringBuilder content = new StringBuilder();
        content.append("package ").append(packagePath).append(".resources;\n\n");
        content.append("import jakarta.ws.rs.*;\n");
        content.append("import jakarta.ws.rs.core.MediaType;\n");
        content.append("import jakarta.ws.rs.core.Response;\n");
        content.append("import ").append(packagePath).append(".service.*;\n");
        content.append("import ").append(packagePath).append(".model.*;\n");
        content.append("import egain.framework.Actor;\n");
        content.append("import egain.framework.ActorType;\n");
        content.append("import egain.framework.OAuthScope;\n\n");

        // Extract API version from path and construct @Path
        String apiPath = extractApiPathWithVersion(parentPath, operations, spec);
        content.append("@Path(\"").append(apiPath).append("\")\n");
        content.append("@Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})\n");
        content.append("@Consumes({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})\n");

        // Generate @Actor annotation with security information
        content.append(generateActorAnnotation(operations));

        content.append("public class ").append(resourceName).append(" {\n\n");

        // Generate methods for each operation
        for (PathOperation pathOp : operations) {
            String relativePath = getRelativePath(parentPath, pathOp.path);
            try {
                generateResourceMethod(pathOp.method, pathOp.operation, relativePath, content);
            } catch (GenerationException e) {
                throw new GenerationException("Failed to generate resource method for " + pathOp.method + " " + pathOp.path + ": " + e.getMessage(), e);
            }
        }

        content.append("}\n");

        JerseyGenerationContext.writeFile(outputDir + "/src/main/java/" + packagePath.replace(".", "/") + "/resources/" + resourceName + ".java", content.toString());
    }

    /**
     * Get relative path from parent path to full path.
     * Examples:
     * parentPath="/users", fullPath="/users" -> ""
     * parentPath="/users", fullPath="/users/{id}" -> "/{id}"
     * parentPath="/users", fullPath="/users/{id}/posts" -> "/{id}/posts"
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
     * Generate resource method.
     */
    private void generateResourceMethod(String method, Map<String, Object> operation, String relativePath, StringBuilder content) throws GenerationException {
        String operationId = (String) operation.get("operationId");
        String summary = (String) operation.get("summary");

        // Get HTTP method annotation
        String httpMethod = method.toUpperCase(Locale.ROOT);

        // Add HTTP method annotation
        content.append("    @").append(httpMethod).append("\n");

        // Add path annotation if relative path is not empty
        if (relativePath != null && !relativePath.isEmpty()) {
            content.append("    @Path(\"").append(relativePath).append("\")\n");
        }

        // Extract parameters and generate method signature
        List<Map<String, Object>> params = Util.asStringObjectMapList(operation.get("parameters"));
        List<String> parameterList = new ArrayList<>();

        // Handle request body for POST/PUT/PATCH
        if ((method.equalsIgnoreCase("post") || method.equalsIgnoreCase("put") || method.equalsIgnoreCase("patch"))) {
            Map<String, Object> requestBody = Util.asStringObjectMap(operation.get("requestBody"));
            if (requestBody != null) {
                // For simplicity, assume JSON request body
                parameterList.add("Object requestBody");
            }
        }

        if (params != null) {
            for (Map<String, Object> param : params) {
                String name = (String) param.get("name");
                String in = (String) param.get("in");
                Map<String, Object> schema = Util.asStringObjectMap(param.get("schema"));

                // Skip header parameters as they are handled by the framework
                if ("header".equals(in)) {
                    continue;
                }

                if (name != null && in != null && schema != null) {
                    // Validate and sanitize parameter name for Java
                    String sanitizedName = JerseyNamingUtils.sanitizeParameterName(name);
                    if (sanitizedName == null) {
                        throw new GenerationException("The endpoint request param contains the invalid variable name: " + name +
                                " in path: " + relativePath + ". Parameter names must be valid Java identifiers.");
                    }

                    String javaType = javaTypeResolver.apply(schema);
                    String annotation = getParameterAnnotation(in);

                    // Build parameter string with parameter annotation (validation annotations removed)
                    String paramBuilder = annotation + "(\"" + name + "\") " +
                            javaType + " " + sanitizedName;

                    parameterList.add(paramBuilder);
                }
            }
        }

        // Generate method signature (normalize operationId to valid Java method name when present)
        String methodName = (operationId != null && !operationId.isEmpty()) ? JerseyNamingUtils.toJavaMethodName(operationId) : method;
        content.append("    public Response ").append(methodName).append("(");
        if (!parameterList.isEmpty()) {
            // Join parameters, handling multi-line annotations
            for (int i = 0; i < parameterList.size(); i++) {
                if (i > 0) {
                    content.append(",\n            ");
                } else {
                    content.append("\n            ");
                }
                content.append(parameterList.get(i));
            }
            content.append("\n        ");
        }
        content.append(") {\n");
        content.append("        // Implementation placeholder for ").append(summary != null ? summary : method).append("\n");
        content.append("        // Replace this with actual business logic implementation\n");
        content.append("        return Response.ok().build();\n");
        content.append("    }\n\n");
    }

    /**
     * Get parameter annotation based on parameter location.
     */
    private String getParameterAnnotation(String in) {
        return switch (in.toLowerCase(Locale.ROOT)) {
            case "path" -> "@PathParam";
            case "header" -> "@HeaderParam";
            default -> "@QueryParam";
        };
    }

    /**
     * Generate resource class name from a path.
     */
    private String generateResourceName(String path) {
        String name = path != null ? path.replaceAll("[^a-zA-Z0-9]", "") : "";
        if (name.isEmpty()) {
            return "RootResource";
        }
        return name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1) + "Resource";
    }

    /**
     * Helper class to store path and operation information.
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
}

package egain.oassdk.generators.java;

import egain.oassdk.Util;
import egain.oassdk.core.Constants;
import egain.oassdk.core.exceptions.GenerationException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Generates BOExecutor classes (Get/PostPut) for each API operation.
 * Each executor corresponds to a single OpenAPI path+method combination.
 */
class JerseyExecutorGenerator {

    private final JerseyGenerationContext ctx;
    /**
     * Function to resolve an OpenAPI schema map to a Java type string.
     * Provided by JerseyGenerator until JerseyTypeUtils is extracted.
     */
    private final Function<Map<String, Object>, String> javaTypeResolver;

    JerseyExecutorGenerator(JerseyGenerationContext ctx, Function<Map<String, Object>, String> javaTypeResolver) {
        this.ctx = ctx;
        this.javaTypeResolver = javaTypeResolver;
    }

    /**
     * Generate executor classes for all operations in the spec.
     */
    public void generateExecutors(Map<String, Object> spec, String outputDir, String packageName) throws IOException, GenerationException {
        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        if (paths == null) return;

        String packagePath = packageName != null ? packageName : "com.example.api";

        // Iterate through all paths and operations
        for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
            String path = pathEntry.getKey();
            Map<String, Object> pathItem = Util.asStringObjectMap(pathEntry.getValue());

            if (pathItem == null) continue;

            String[] methods = Constants.HTTP_METHODS;
            for (String method : methods) {
                if (pathItem.containsKey(method)) {
                    Map<String, Object> operation = Util.asStringObjectMap(pathItem.get(method));
                    if (operation != null) {
                        try {
                            generateExecutorForOperation(path, method, operation, outputDir, packagePath, spec);
                        } catch (GenerationException e) {
                            throw new GenerationException("Failed to generate executor for " + method.toUpperCase(Locale.ROOT) + " " + path + ": " + e.getMessage(), e);
                        }
                    }
                }
            }
        }
    }

    /**
     * Generate executor for a single operation.
     */
    public void generateExecutorForOperation(String path, String method, Map<String, Object> operation,
                                              String outputDir, String packagePath, Map<String, Object> spec) throws IOException, GenerationException {
        String operationId = (String) operation.get("operationId");
        if (operationId == null || operationId.isEmpty()) {
            // Generate operationId from path and method if not provided
            operationId = JerseyNamingUtils.generateOperationIdFromPath(path, method);
        }

        String executorClassName = JerseyNamingUtils.toJavaClassName(operationId) + "BOExecutor";
        String executorPackage = packagePath + ".executor";

        // Determine executor base class and response/request types
        String baseExecutorClass;
        String responseType = null;
        String requestBodyType = null;
        boolean hasResponseBody = false;

        if ("get".equalsIgnoreCase(method)) {
            // GET operations use GetBOExecutor_2<ResponseType>
            responseType = extractResponseType(operation, spec);
            hasResponseBody = responseType != null && !"void".equals(responseType);
            if (hasResponseBody && responseType != null) {
                baseExecutorClass = "GetBOExecutor_2<" + responseType + ">";
            } else {
                baseExecutorClass = "GetBOExecutor_2<Object>";
                responseType = "Object";
                hasResponseBody = true; // Object is still a response body
            }
        } else if ("post".equalsIgnoreCase(method) || "put".equalsIgnoreCase(method) || "patch".equalsIgnoreCase(method)) {
            // POST/PUT/PATCH operations use PostPutBOExecutorNoResponseBody_2 or PostPutBOExecutor_2
            requestBodyType = extractRequestBodyType(operation, spec);
            responseType = extractResponseType(operation, spec);
            hasResponseBody = responseType != null && !"void".equals(responseType);

            if (hasResponseBody) {
                baseExecutorClass = "PostPutBOExecutor_2<" + responseType + ">";
            } else {
                baseExecutorClass = "PostPutBOExecutorNoResponseBody_2";
            }
        } else {
            // DELETE and others - use PostPutBOExecutorNoResponseBody_2 as default
            baseExecutorClass = "PostPutBOExecutorNoResponseBody_2";
        }

        // Extract parameters
        List<Map<String, Object>> params = Util.asStringObjectMapList(operation.get("parameters"));

        // Generate executor class
        StringBuilder content = new StringBuilder();
        content.append("package ").append(executorPackage).append(";\n\n");

        // Add imports
        content.append("import java.util.List;\n");
        content.append("import java.util.Locale;\n");
        content.append("import java.util.Map;\n\n");
        content.append("import ").append(ctx.xmlBindNs).append(".JAXBElement;\n");
        content.append("import javax.xml.namespace.QName;\n\n");
        content.append("import com.egain.platform.common.CallerContext;\n");
        content.append("import com.egain.platform.common.exception.PlatformException;\n");
        content.append("import com.egain.platform.util.logging.Level;\n");
        content.append("import com.egain.platform.util.logging.LogSource;\n");
        content.append("import com.egain.platform.util.logging.Logger;\n\n");
        content.append("import egain.ws.common.authorization.AlwaysAuthorizedAuthorizerPartitionFactory;\n");
        content.append("import egain.ws.common.authorization.Authorizer;\n");
        content.append("import egain.ws.exception.BadRequestException;\n");
        if ("get".equalsIgnoreCase(method)) {
            content.append("import egain.ws.framework.GetBOExecutor_2;\n");
        } else {
            if (hasResponseBody) {
                content.append("import egain.ws.framework.PostPutBOExecutor_2;\n");
            } else {
                content.append("import egain.ws.framework.PostPutBOExecutorNoResponseBody_2;\n");
            }
        }
        content.append("import egain.ws.util.WsUtil;\n");
        content.append("import com.egain.platform.util.Util;\n");
        content.append("import ").append(packagePath).append(".model.*;\n\n");
        content.append("import static ").append(ctx.wsNs).append(".core.Response.Status.BAD_REQUEST;\n\n");

        // Generate class declaration
        content.append("public class ").append(executorClassName).append(" extends ").append(baseExecutorClass);
        content.append("\n{\n");
        content.append("\tprivate static final String CLASS_NAME = \"").append(executorPackage).append(".").append(executorClassName).append("\";\n");
        content.append("\tprivate static final String FILE_NAME = \"").append(executorClassName).append(".java\";\n");
        content.append("\tprivate static final Logger mLogger = Logger.getLogger(CLASS_NAME);\n\n");
        content.append("\tprivate final Map<String, String> solveHeaderMap;\n\n");

        // Generate field declarations for parameters
        if (params != null) {
            for (Map<String, Object> param : params) {
                String name = (String) param.get("name");
                String in = (String) param.get("in");
                Map<String, Object> schema = Util.asStringObjectMap(param.get("schema"));

                // Skip header parameters as they are handled by the framework
                if ("header".equals(in)) {
                    continue;
                }

                if (name != null && schema != null) {
                    String sanitizedName = JerseyNamingUtils.sanitizeParameterName(name);
                    if (sanitizedName != null) {
                        String javaType = getJavaType(schema);
                        content.append("\tprivate ").append(javaType).append(" m").append(JerseyNamingUtils.capitalize(sanitizedName)).append(";\n");
                    }
                }
            }
        }

        // Generate field for request body if present
        if (requestBodyType != null) {
            String requestBodyFieldName = extractRequestBodyFieldName(operation, spec);
            content.append("\tprivate ").append(requestBodyType).append(" m").append(JerseyNamingUtils.capitalize(requestBodyFieldName)).append(";\n");
        }

        // Generate field for response data (for GET operations)
        if ("get".equalsIgnoreCase(method) && hasResponseBody) {
            content.append("\tprivate ").append(responseType).append(" mResponseData;\n");
        }

        content.append("\n");

        // Generate constructor
        content.append("\tpublic ").append(executorClassName).append("(");
        List<String> constructorParams = new ArrayList<>();

        // Add solveHeaderMap parameter
        constructorParams.add("Map<String, String> solveHeaderMap");

        // Add request body parameter if present
        if (requestBodyType != null) {
            String requestBodyFieldName = extractRequestBodyFieldName(operation, spec);
            constructorParams.add(requestBodyType + " " + requestBodyFieldName);
        }

        // Add path parameters
        if (params != null) {
            for (Map<String, Object> param : params) {
                String name = (String) param.get("name");
                String in = (String) param.get("in");
                Map<String, Object> schema = Util.asStringObjectMap(param.get("schema"));

                if ("path".equals(in) && name != null && schema != null) {
                    String sanitizedName = JerseyNamingUtils.sanitizeParameterName(name);
                    if (sanitizedName != null) {
                        String javaType = getJavaType(schema);
                        constructorParams.add(javaType + " " + sanitizedName);
                    }
                }
            }
        }

        // Write constructor parameters
        for (int i = 0; i < constructorParams.size(); i++) {
            if (i > 0) {
                content.append(",\n\t\t\t");
            } else {
                content.append("\n\t\t\t");
            }
            content.append(constructorParams.get(i));
        }
        content.append("\n\t)\n");
        content.append("\t{\n");
        content.append("\t\tthis.solveHeaderMap = solveHeaderMap;\n");

        // Initialize request body
        if (requestBodyType != null) {
            String requestBodyFieldName = extractRequestBodyFieldName(operation, spec);
            content.append("\t\tthis.m").append(JerseyNamingUtils.capitalize(requestBodyFieldName)).append(" = ").append(requestBodyFieldName).append(";\n");
        }

        // Initialize path parameters
        if (params != null) {
            for (Map<String, Object> param : params) {
                String name = (String) param.get("name");
                String in = (String) param.get("in");
                Map<String, Object> schema = Util.asStringObjectMap(param.get("schema"));

                if ("path".equals(in) && name != null && schema != null) {
                    String sanitizedName = JerseyNamingUtils.sanitizeParameterName(name);
                    if (sanitizedName != null) {
                        content.append("\t\tthis.m").append(JerseyNamingUtils.capitalize(sanitizedName)).append(" = ").append(sanitizedName).append(";\n");
                    }
                }
            }
        }

        content.append("\t}\n\n");

        // Generate validateRequestSyntaxImpl method
        content.append("\t@Override\n");
        content.append("\tprotected void validateRequestSyntaxImpl(CallerContext callerContext, Locale locale)\n");
        content.append("\t{\n");
        content.append("\t\t// TODO: Implement request syntax validation\n");
        content.append("\t\t// Validate required query parameters\n");

        if (params != null) {
            for (Map<String, Object> param : params) {
                String name = (String) param.get("name");
                String in = (String) param.get("in");
                Boolean required = (Boolean) param.get("required");
                Map<String, Object> schema = Util.asStringObjectMap(param.get("schema"));

                if ("query".equals(in) && Boolean.TRUE.equals(required) && name != null && schema != null) {
                    String sanitizedName = JerseyNamingUtils.sanitizeParameterName(name);
                    if (sanitizedName != null) {
                        String javaType = getJavaType(schema);
                        content.append("\t\tString ").append(sanitizedName).append("Str = uriInfo.getQueryParameters().getFirst(\"").append(name).append("\");\n");
                        content.append("\t\tif (Util.isEmpty(").append(sanitizedName).append("Str))\n");
                        content.append("\t\t{\n");
                        content.append("\t\t\tthrow new egain.ws.framework.WsApiException(egain.ws.framework.WSConstants.COMMON_ERROR_MSG_FILE_PATH,\n");
                        content.append("\t\t\t\t\"L10N_REQUIRED_QUERY_PARAM_MISSING\", new String[] { \"").append(name).append("\" },\n");
                        content.append("\t\t\t\tBAD_REQUEST);\n");
                        content.append("\t\t}\n\n");

                        if (javaType.equals("long") || javaType.equals("Long")) {
                            content.append("\t\tm").append(JerseyNamingUtils.capitalize(sanitizedName)).append(" = WsUtil.validateId(").append(sanitizedName).append("Str);\n");
                        } else {
                            content.append("\t\tm").append(JerseyNamingUtils.capitalize(sanitizedName)).append(" = ").append(sanitizedName).append("Str;\n");
                        }
                        content.append("\n");
                    }
                }
            }
        }

        content.append("\t\tWsUtil.validateSolveHeaderValues(solveHeaderMap, true, locale, callerContext);\n");
        content.append("\t}\n\n");

        // Generate createAuthorizer method
        content.append("\t@Override\n");
        content.append("\tprotected Authorizer createAuthorizer(CallerContext callerContext)\n");
        content.append("\t{\n");
        content.append("\t\treturn AlwaysAuthorizedAuthorizerPartitionFactory.getInstance().getAuth(callerContext);\n");
        content.append("\t}\n\n");

        // Generate executeBusinessOperationImpl method
        content.append("\t@Override\n");
        content.append("\tprotected void executeBusinessOperationImpl(CallerContext callerContext, Locale locale, LogSource logSource)\n");
        content.append("\t\tthrows Exception\n");
        content.append("\t{\n");
        content.append("\t\tmLogger.log(Level.TRACE, callerContext, logSource, \"Executing business operation\");\n");
        content.append("\t\t// TODO: Implement business logic\n");
        if ("get".equalsIgnoreCase(method) && hasResponseBody) {
            content.append("\t\t// Set mResponseData with the result\n");
        } else if (!"get".equalsIgnoreCase(method)) {
            content.append("\t\t// Perform the create/update/delete operation\n");
        }
        content.append("\t}\n\n");

        // Generate convertDataObjectToJaxbBeanImpl for GET operations
        if ("get".equalsIgnoreCase(method) && hasResponseBody) {
            content.append("\t@Override\n");
            content.append("\tprotected JAXBElement<").append(responseType).append("> convertDataObjectToJaxbBeanImpl(CallerContext callerContext, Locale locale,\n");
            content.append("\t\tLogSource logSource) throws PlatformException\n");
            content.append("\t{\n");
            content.append("\t\tmLogger.log(Level.TRACE, callerContext, logSource, \"Converting data object to JAXB bean\");\n");
            content.append("\n");
            content.append("\t\t// TODO: Convert mResponseData to JAXBElement\n");

            // Handle List types specially (like in ArticleTypeBOExecutor example)
            if (responseType != null && responseType.startsWith("List<")) {
                String innerType = responseType.substring(5, responseType.length() - 1);
                content.append("\t\tif (Util.isEmpty(mResponseData))\n");
                content.append("\t\t{\n");
                content.append("\t\t\tmResponseData = new java.util.ArrayList<>();\n");
                content.append("\t\t}\n");
                content.append("\n");
                content.append("\t\t// Create JAXBElement for the list - GSON will serialize the value (the list) as a direct array in JSON\n");
                content.append("\t\tQName qName = new QName(\"http://bindings.egain.com/ws/model/xsds/common/v4\", \"").append(innerType).append("s\");\n");
                content.append("\t\t@SuppressWarnings(\"unchecked\")\n");
                content.append("\t\tJAXBElement<").append(responseType).append("> element = new JAXBElement<>(qName, (Class<").append(responseType).append(">)(Class<?>)java.util.ArrayList.class, mResponseData);\n");
            } else if (responseType != null) {
                content.append("\t\tQName qName = new QName(\"http://bindings.egain.com/ws/model/xsds/common/v4\", \"").append(responseType).append("\");\n");
                content.append("\t\t@SuppressWarnings(\"unchecked\")\n");
                content.append("\t\tJAXBElement<").append(responseType).append("> element = new JAXBElement<>(qName, ").append(responseType).append(".class, mResponseData);\n");
            } else {
                content.append("\t\t// Response type is null, using Object as fallback\n");
                content.append("\t\tQName qName = new QName(\"http://bindings.egain.com/ws/model/xsds/common/v4\", \"Object\");\n");
                content.append("\t\t@SuppressWarnings(\"unchecked\")\n");
                content.append("\t\tJAXBElement<Object> element = new JAXBElement<>(qName, Object.class, mResponseData);\n");
            }
            content.append("\t\treturn element;\n");
            content.append("\t}\n\n");
        }

        // Generate convertJaxbBeanToDataObject for POST/PUT/PATCH operations
        if (!"get".equalsIgnoreCase(method) && requestBodyType != null) {
            content.append("\t@Override\n");
            content.append("\tprotected void convertJaxbBeanToDataObject(CallerContext callerContext, Locale locale)\n");
            content.append("\t{\n");
            content.append("\t\tLogSource logSource = LogSource.getObject(CLASS_NAME, \"convertJaxbBeanToDataObject()\", FILE_NAME);\n");
            content.append("\t\tmLogger.log(Level.TRACE, callerContext, logSource, \"Converting JAXB bean to data object\");\n");
            content.append("\t\t// TODO: Convert request body to data object\n");
            content.append("\t}\n\n");
        }

        // Generate composeLocationHeader for POST/PUT/PATCH operations without response body
        if (!"get".equalsIgnoreCase(method) && !hasResponseBody) {
            content.append("\t@Override\n");
            content.append("\tprotected String composeLocationHeader()\n");
            content.append("\t{\n");
            content.append("\t\t// TODO: Compose location header for created resource\n");
            content.append("\t\treturn null;\n");
            content.append("\t}\n");

            content.append("}\n");
        } else {
            content.append("}\n");
        }

        // Write executor file
        String executorDir = outputDir + "/src/main/java/" + executorPackage.replace(".", "/");
        JerseyGenerationContext.writeFile(executorDir + "/" + executorClassName + ".java", content.toString());
    }

    /**
     * Extract response type from operation responses.
     */
    public String extractResponseType(Map<String, Object> operation, Map<String, Object> spec) {
        if (operation == null || !operation.containsKey("responses")) {
            return null;
        }

        Map<String, Object> responses = Util.asStringObjectMap(operation.get("responses"));
        if (responses == null) {
            return null;
        }

        // Look for 200 or 201 response
        String[] successCodes = {"200", "201", "202", "204"};
        for (String code : successCodes) {
            Object responseObj = responses.get(code);
            if (responseObj == null) continue;

            Map<String, Object> response = Util.asStringObjectMap(responseObj);
            if (response == null || !response.containsKey("content")) {
                continue;
            }

            Map<String, Object> responseContent = Util.asStringObjectMap(response.get("content"));
            if (responseContent == null) {
                continue;
            }

            // Look for application/json or application/xml
            for (String mediaType : new String[]{"application/json", "application/xml"}) {
                Object mediaTypeObj = responseContent.get(mediaType);
                if (mediaTypeObj == null) continue;

                Map<String, Object> mediaTypeMap = Util.asStringObjectMap(mediaTypeObj);
                if (mediaTypeMap == null) continue;

                Object schemaObj = mediaTypeMap.get("schema");
                if (schemaObj == null) continue;

                Map<String, Object> schema = Util.asStringObjectMap(schemaObj);
                if (schema != null) {
                    String javaType = getJavaType(schema);
                    if (javaType != null && !"Object".equals(javaType)) {
                        return javaType;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Extract request body type from operation.
     */
    public String extractRequestBodyType(Map<String, Object> operation, Map<String, Object> spec) {
        if (operation == null || !operation.containsKey("requestBody")) {
            return null;
        }

        Map<String, Object> requestBody = Util.asStringObjectMap(operation.get("requestBody"));
        if (requestBody == null || !requestBody.containsKey("content")) {
            return null;
        }

        Map<String, Object> bodyContent = Util.asStringObjectMap(requestBody.get("content"));
        if (bodyContent == null) {
            return null;
        }

        // Look for application/json or application/xml
        for (String mediaType : new String[]{"application/json", "application/xml"}) {
            Object mediaTypeObj = bodyContent.get(mediaType);
            if (mediaTypeObj == null) continue;

            Map<String, Object> mediaTypeMap = Util.asStringObjectMap(mediaTypeObj);
            if (mediaTypeMap == null) continue;

            Object schemaObj = mediaTypeMap.get("schema");
            if (schemaObj == null) continue;

            Map<String, Object> schema = Util.asStringObjectMap(schemaObj);
            if (schema != null) {
                String javaType = getJavaType(schema);
                if (javaType != null && !"Object".equals(javaType)) {
                    return javaType;
                }
            }
        }

        return null;
    }

    /**
     * Extract request body field name from operation, deriving it from operationId.
     */
    public String extractRequestBodyFieldName(Map<String, Object> operation, Map<String, Object> spec) {
        String operationId = (String) operation.get("operationId");
        if (operationId != null && !operationId.isEmpty()) {
            // Try to extract a meaningful name from operationId
            // e.g., "createSuggestionComment" -> "comment"
            String lowerId = operationId.toLowerCase(Locale.ROOT);
            if (lowerId.startsWith("create")) {
                return lowerId.substring(6); // Remove "create"
            } else if (lowerId.startsWith("update")) {
                return lowerId.substring(6); // Remove "update"
            } else if (lowerId.endsWith("comment")) {
                return "comment";
            } else if (lowerId.endsWith("suggestion")) {
                return "suggestion";
            }
        }
        return "requestBody";
    }

    /**
     * Delegate to the type resolver function for schema-to-Java-type mapping.
     */
    private String getJavaType(Map<String, Object> schema) {
        return javaTypeResolver.apply(schema);
    }
}

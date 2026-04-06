package egain.oassdk.generators.java;

import egain.oassdk.Util;
import egain.oassdk.core.Constants;
import egain.oassdk.core.exceptions.GenerationException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Generates BOExecutor classes (Get/PostPut) for each API operation.
 * Each executor corresponds to a single OpenAPI path+method combination.
 * <p>See {@link JerseyExecutorSpecValidation} for OpenAPI-driven request validation and notes on
 * {@code PostPutBOExecutorNoResponseBody_2} vs legacy executor hooks.</p>
 */
class JerseyExecutorGenerator {

    static final String X_OAS_SDK_EXECUTOR = "x-oas-sdk-executor";
    static final String X_EGAIN_EXECUTOR = "x-egain-executor";

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
        Map<String, Object> executorExt = mergedExecutorExtension(operation);
        Set<String> extensionImports = extensionImports(executorExt);

        boolean defaultAuthorizer = extensionMethodBody(executorExt, "createAuthorizer") == null;
        if (defaultAuthorizer) {
            content.append("import egain.ws.common.authorization.AlwaysAuthorizedAuthorizerPartitionFactory;\n");
        }
        content.append("import egain.ws.common.authorization.Authorizer;\n");
        content.append("import egain.ws.exception.BadRequestException;\n");
        for (String imp : extensionImports) {
            if (imp != null && !imp.isBlank()) {
                content.append("import ").append(imp.trim()).append(";\n");
            }
        }
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

        String requestBodyFieldName = requestBodyType != null
                ? extractRequestBodyFieldName(requestBodyType, operation, spec)
                : null;

        // Generate field for request body if present
        if (requestBodyType != null && requestBodyFieldName != null) {
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
        if (requestBodyType != null && requestBodyFieldName != null) {
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
        if (requestBodyType != null && requestBodyFieldName != null) {
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

        if (requestBodyType != null && requestBodyFieldName != null) {
            String specVal = JerseyExecutorSpecValidation.generateRequestBodyValidationCode(
                    operation, spec, requestBodyFieldName, requestBodyFieldName);
            if (specVal != null && !specVal.isEmpty()) {
                content.append(specVal);
            }
        }

        String extValidate = indentExecutorFragment(extensionMethodBody(executorExt, "validateRequestSyntaxImpl"));
        if (extValidate != null) {
            content.append(extValidate);
        }

        content.append("\t}\n\n");

        // Generate createAuthorizer method
        content.append("\t@Override\n");
        content.append("\tprotected Authorizer createAuthorizer(CallerContext callerContext)\n");
        content.append("\t{\n");
        String extAuth = indentExecutorFragment(extensionMethodBody(executorExt, "createAuthorizer"));
        if (extAuth != null) {
            content.append(extAuth);
        } else {
            content.append("\t\treturn AlwaysAuthorizedAuthorizerPartitionFactory.getInstance().getAuth(callerContext);\n");
        }
        content.append("\t}\n\n");

        // Generate executeBusinessOperationImpl method
        content.append("\t@Override\n");
        content.append("\tprotected void executeBusinessOperationImpl(CallerContext callerContext, Locale locale, LogSource logSource)\n");
        content.append("\t\tthrows Exception\n");
        content.append("\t{\n");
        String extExec = indentExecutorFragment(extensionMethodBody(executorExt, "executeBusinessOperationImpl"));
        if (extExec != null) {
            content.append(extExec);
        } else {
            content.append("\t\tmLogger.log(Level.TRACE, callerContext, logSource, \"Executing business operation\");\n");
            content.append("\t\t// TODO: Implement business logic\n");
            if ("get".equalsIgnoreCase(method) && hasResponseBody) {
                content.append("\t\t// Set mResponseData with the result\n");
            } else if (!"get".equalsIgnoreCase(method)) {
                content.append("\t\t// Perform the create/update/delete operation\n");
            }
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
            String extConv = indentExecutorFragment(extensionMethodBody(executorExt, "convertJaxbBeanToDataObject"));
            if (extConv != null) {
                content.append(extConv);
            } else {
                content.append("\t\tLogSource logSource = LogSource.getObject(CLASS_NAME, \"convertJaxbBeanToDataObject()\", FILE_NAME);\n");
                content.append("\t\tmLogger.log(Level.TRACE, callerContext, logSource, \"Converting JAXB bean to data object\");\n");
                content.append("\t\t// TODO: Convert request body to data object\n");
            }
            content.append("\t}\n\n");
        }

        // Generate composeLocationHeader for POST/PUT/PATCH operations without response body
        if (!"get".equalsIgnoreCase(method) && !hasResponseBody) {
            content.append("\t@Override\n");
            content.append("\tprotected String composeLocationHeader()\n");
            content.append("\t{\n");
            String extLoc = indentExecutorFragment(extensionMethodBody(executorExt, "composeLocationHeader"));
            if (extLoc != null) {
                content.append(extLoc);
            } else {
                content.append("\t\t// TODO: Compose location header for created resource\n");
                content.append("\t\treturn null;\n");
            }
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
     * Local variable / field name for the request body: lower-camelCase of the Java model class
     * (e.g. {@code Folder} → {@code folder}). Falls back to {@code operationId} heuristics when the type is generic.
     */
    public String extractRequestBodyFieldName(String requestBodyType, Map<String, Object> operation, Map<String, Object> spec) {
        if (requestBodyType != null) {
            String t = requestBodyType.trim();
            if (!t.isEmpty() && !t.contains("<") && !t.contains(">") && !t.contains("[") && !t.contains("]") && !t.contains(",")) {
                if (Character.isLetter(t.charAt(0))) {
                    return t.substring(0, 1).toLowerCase(Locale.ROOT) + t.substring(1);
                }
            }
        }
        if (operation != null) {
            String operationId = (String) operation.get("operationId");
            if (operationId != null && !operationId.isEmpty()) {
                String lowerId = operationId.toLowerCase(Locale.ROOT);
                if (lowerId.startsWith("create") && lowerId.length() > 6) {
                    return lowerId.substring(6);
                }
                if (lowerId.startsWith("update") && lowerId.length() > 6) {
                    return lowerId.substring(6);
                }
                if (lowerId.startsWith("patch") && lowerId.length() > 5) {
                    return lowerId.substring(5);
                }
                if (lowerId.endsWith("comment")) {
                    return "comment";
                }
                if (lowerId.endsWith("suggestion")) {
                    return "suggestion";
                }
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

    /**
     * Merge {@code x-oas-sdk-executor} over {@code x-egain-executor}: imports union (SDK last wins order for duplicates),
     * methods map with SDK overriding eGain for the same key.
     */
    static Map<String, Object> mergedExecutorExtension(Map<String, Object> operation) {
        if (operation == null) {
            return null;
        }
        Map<String, Object> sdk = Util.asStringObjectMap(operation.get(X_OAS_SDK_EXECUTOR));
        Map<String, Object> egain = Util.asStringObjectMap(operation.get(X_EGAIN_EXECUTOR));
        if (sdk == null && egain == null) {
            return null;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        LinkedHashSet<String> imports = new LinkedHashSet<>();
        if (egain != null) {
            List<String> li = Util.asStringList(egain.get("imports"));
            if (li != null) {
                imports.addAll(li);
            }
        }
        if (sdk != null) {
            List<String> li = Util.asStringList(sdk.get("imports"));
            if (li != null) {
                imports.addAll(li);
            }
        }
        if (!imports.isEmpty()) {
            out.put("imports", new ArrayList<>(imports));
        }
        Map<String, Object> methods = new LinkedHashMap<>();
        if (egain != null) {
            Map<String, Object> m = Util.asStringObjectMap(egain.get("methods"));
            if (m != null) {
                methods.putAll(m);
            }
        }
        if (sdk != null) {
            Map<String, Object> m = Util.asStringObjectMap(sdk.get("methods"));
            if (m != null) {
                methods.putAll(m);
            }
        }
        if (!methods.isEmpty()) {
            out.put("methods", methods);
        }
        return out.isEmpty() ? null : out;
    }

    private static Set<String> extensionImports(Map<String, Object> ext) {
        if (ext == null) {
            return new LinkedHashSet<>();
        }
        List<String> li = Util.asStringList(ext.get("imports"));
        if (li == null || li.isEmpty()) {
            return new LinkedHashSet<>();
        }
        return new LinkedHashSet<>(li);
    }

    private static String extensionMethodBody(Map<String, Object> ext, String methodKey) {
        if (ext == null || methodKey == null) {
            return null;
        }
        Map<String, Object> methods = Util.asStringObjectMap(ext.get("methods"));
        if (methods == null) {
            return null;
        }
        Object v = methods.get(methodKey);
        if (v instanceof String s) {
            String t = s.trim();
            return t.isEmpty() ? null : t;
        }
        return null;
    }

    /**
     * Prefix each line with two tabs for injection into generated executor methods.
     */
    static String indentExecutorFragment(String fragment) {
        if (fragment == null || fragment.isBlank()) {
            return null;
        }
        String[] lines = fragment.split("\r?\n", -1);
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            sb.append("\t\t").append(line).append("\n");
        }
        return sb.toString();
    }
}

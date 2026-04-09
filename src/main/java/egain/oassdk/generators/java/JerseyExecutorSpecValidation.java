package egain.oassdk.generators.java;

import egain.oassdk.Util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * OpenAPI-driven validation code fragments for generated {@code *BOExecutor} classes.
 * <p>
 * <b>Framework note:</b> generated executors extend {@code PostPutBOExecutorNoResponseBody_2} /
 * {@code PostPutBOExecutor_2} / {@code GetBOExecutor_2}. The legacy {@code PostPutBOExecutorNoResponseBody}
 * base also exposed {@code validateRequestBodyData} and {@code createLimitValidator}; those hooks are not
 * generated here because they may not exist on the {@code _2} types. Use {@code x-oas-sdk-executor} method
 * fragments (e.g. prepend logic to {@code convertJaxbBeanToDataObject} or {@code executeBusinessOperationImpl})
 * or extend the platform base class if you need limit / post-authorizer worksheet steps.
 * </p>
 * <p>
 * <b>Read-only semantics:</b> {@code readOnly} flags that appear only inside {@code oneOf}/{@code anyOf}
 * branches are contextual (e.g. one branch marks {@code parent} read-only). This helper does not treat those
 * as globally disallowed; it only collects read-only properties from schema nodes that are not entered via
 * {@code oneOf}/{@code anyOf} recursion, except that when a node has both inline {@code properties} and
 * {@code oneOf}, inline properties are still scanned.
 * </p>
 */
final class JerseyExecutorSpecValidation {

    private JerseyExecutorSpecValidation() {
    }

    /**
     * Extract the JSON request body schema map (application/json), resolving a top-level {@code $ref} chain.
     */
    static Map<String, Object> extractJsonRequestBodySchema(Map<String, Object> operation, Map<String, Object> spec) {
        if (operation == null || spec == null) {
            return null;
        }
        Map<String, Object> requestBody = Util.asStringObjectMap(operation.get("requestBody"));
        if (requestBody == null) {
            return null;
        }
        Map<String, Object> bodyContent = Util.asStringObjectMap(requestBody.get("content"));
        if (bodyContent == null) {
            return null;
        }
        Map<String, Object> media = Util.asStringObjectMap(bodyContent.get("application/json"));
        if (media == null) {
            media = Util.asStringObjectMap(bodyContent.get("application/vnd.api+json"));
        }
        if (media == null) {
            return null;
        }
        Map<String, Object> schema = Util.asStringObjectMap(media.get("schema"));
        if (schema == null) {
            return null;
        }
        return resolveSchemaDeref(schema, spec, 0);
    }

    static boolean isRequestBodyRequired(Map<String, Object> operation) {
        if (operation == null) {
            return false;
        }
        Map<String, Object> requestBody = Util.asStringObjectMap(operation.get("requestBody"));
        return requestBody != null && Boolean.TRUE.equals(requestBody.get("required"));
    }

    /**
     * Build Java statements (indented with two tabs) for validateRequestSyntaxImpl: required body and read-only
     * attributes. Simple two-branch {@code oneOf} XOR is enforced on the model via {@code @AssertTrue}.
     */
    static String generateRequestBodyValidationCode(
            Map<String, Object> operation,
            Map<String, Object> spec,
            String bodyFieldName,
            String resourceLabelForMessages) {
        if (operation == null || spec == null || bodyFieldName == null || bodyFieldName.isEmpty()) {
            return "";
        }
        Map<String, Object> bodySchema = extractJsonRequestBodySchema(operation, spec);
        boolean required = isRequestBodyRequired(operation);
        String modelRef = "m" + JerseyNamingUtils.capitalize(bodyFieldName);
        String label = resourceLabelForMessages != null && !resourceLabelForMessages.isEmpty()
                ? resourceLabelForMessages
                : "resource";

        StringBuilder sb = new StringBuilder();
        if (required) {
            sb.append("\t\tif (").append(modelRef).append(" == null)\n");
            sb.append("\t\t{\n");
            sb.append("\t\t\tthrow new egain.ws.framework.WsApiException(egain.ws.framework.WSConstants.COMMON_ERROR_MSG_FILE_PATH,\n");
            sb.append("\t\t\t\t\"L10N_REQUEST_BODY_NOT_PROVIDED\", BAD_REQUEST);\n");
            sb.append("\t\t}\n\n");
        }

        if (bodySchema == null) {
            return sb.toString();
        }

        StringBuilder bodyChecks = new StringBuilder();
        // XOR mutual exclusion for simple two-branch oneOf is enforced on the request model via
        // @AssertTrue(requiredMutuallyExclusiveFail); avoid duplicating checks here so error handling
        // stays aligned with Bean Validation / ConstraintViolation mapping.

        Set<String> readOnly = new TreeSet<>();
        collectGloballyReadOnlyProperties(bodySchema, spec, readOnly, new IdentityHashMap<>(), 0);
        readOnly.removeIf(p -> p == null || p.isEmpty());

        if (!readOnly.isEmpty()) {
            List<String> conditions = new ArrayList<>();
            StringBuilder nameList = new StringBuilder();
            for (String p : readOnly) {
                conditions.add(modelRef + ".isSet" + accessorSuffixForProperty(p) + "()");
                if (!nameList.isEmpty()) {
                    nameList.append(", ");
                }
                nameList.append(p);
            }
            bodyChecks.append("\t\t\tif (");
            bodyChecks.append(String.join(" ||\n\t\t\t\t", conditions));
            bodyChecks.append(")\n");
            bodyChecks.append("\t\t\t{\n");
            bodyChecks.append("\t\t\t\tthrow new egain.ws.framework.WsApiException(\n");
            bodyChecks.append("\t\t\t\t\t\"L10N_UNALLOWED_ATTRIBUTE_PROVIDED_FOR_OPERATION\", new String[] {\n");
            bodyChecks.append("\t\t\t\t\t\t\"").append(JerseyNamingUtils.escapeJavaString(nameList.toString())).append("\", \"")
                    .append(JerseyNamingUtils.escapeJavaString(label)).append("\" },\n");
            bodyChecks.append("\t\t\t\t\tBAD_REQUEST);\n");
            bodyChecks.append("\t\t\t}\n\n");
        }

        if (!bodyChecks.isEmpty()) {
            sb.append("\t\tif (").append(modelRef).append(" != null)\n");
            sb.append("\t\t{\n");
            sb.append(bodyChecks);
            sb.append("\t\t}\n\n");
        }

        return sb.toString();
    }

    static String accessorSuffixForProperty(String jsonName) {
        String field = JerseyNamingUtils.toModelFieldName(jsonName);
        return JerseyNamingUtils.getCapitalizedPropertyNameForAccessor(field);
    }

    private static Map<String, Object> resolveSchemaDeref(Map<String, Object> schema, Map<String, Object> spec, int depth) {
        if (schema == null || depth > JerseySchemaUtils.MAX_COMPOSITION_RESOLVE_DEPTH) {
            return schema;
        }
        Map<String, Object> cur = schema;
        int guard = 0;
        while (cur != null && cur.containsKey("$ref") && !cur.containsKey("properties")
                && !cur.containsKey("allOf") && !cur.containsKey("oneOf") && !cur.containsKey("anyOf")
                && guard++ < 20) {
            cur = JerseySchemaUtils.resolveRefInSchema(cur, spec);
            if (cur == schema) {
                break;
            }
        }
        return cur;
    }

    /**
     * Collect read-only property names, skipping recursion into {@code oneOf}/{@code anyOf} branches except
     * inline {@code properties} on the same node as {@code oneOf}/{@code anyOf}.
     */
    static void collectGloballyReadOnlyProperties(
            Map<String, Object> schema,
            Map<String, Object> spec,
            Set<String> out,
            Map<Object, Boolean> visited,
            int depth) {
        if (schema == null || depth > JerseySchemaUtils.MAX_MERGE_SCHEMA_DEPTH) {
            return;
        }
        if (visited.containsKey(schema)) {
            return;
        }
        visited.put(schema, Boolean.TRUE);

        Map<String, Object> node = resolveSchemaDeref(schema, spec, depth);

        if (node.containsKey("allOf")) {
            List<Map<String, Object>> parts = Util.asStringObjectMapList(node.get("allOf"));
            if (parts != null) {
                for (Map<String, Object> sub : parts) {
                    collectGloballyReadOnlyProperties(sub, spec, out, visited, depth + 1);
                }
            }
            return;
        }

        boolean hasOneOf = node.containsKey("oneOf") || node.containsKey("anyOf");
        if (hasOneOf) {
            Map<String, Object> props = Util.asStringObjectMap(node.get("properties"));
            if (props != null) {
                for (Map.Entry<String, Object> e : props.entrySet()) {
                    if (e.getKey() == null) {
                        continue;
                    }
                    Map<String, Object> propSchema = Util.asStringObjectMap(e.getValue());
                    if (propSchema == null) {
                        continue;
                    }
                    Map<String, Object> resolved = JerseySchemaUtils.resolveRefInSchema(propSchema, spec);
                    resolved = JerseySchemaUtils.resolveCompositionToEffectiveSchema(resolved, spec);
                    if (JerseySchemaUtils.isSchemaFlagTrue(resolved, "readOnly")) {
                        out.add(e.getKey());
                    }
                }
            }
            return;
        }

        Map<String, Object> props = Util.asStringObjectMap(node.get("properties"));
        if (props != null) {
            for (Map.Entry<String, Object> e : props.entrySet()) {
                if (e.getKey() == null) {
                    continue;
                }
                Map<String, Object> propSchema = Util.asStringObjectMap(e.getValue());
                if (propSchema == null) {
                    continue;
                }
                Map<String, Object> resolved = JerseySchemaUtils.resolveRefInSchema(propSchema, spec);
                resolved = JerseySchemaUtils.resolveCompositionToEffectiveSchema(resolved, spec);
                if (JerseySchemaUtils.isSchemaFlagTrue(resolved, "readOnly")) {
                    out.add(e.getKey());
                }
            }
        }
    }

    /**
     * Metadata for a two-branch {@code oneOf} where each branch declares a single distinct {@code required}
     * property (enforced as XOR on the Java model). When {@code nestedIdRequiredForSorted*} is true, that
     * branch's sole required property is an object schema with {@code required: [id]} and a defined
     * {@code id} property (e.g. {@code createFolder}: {@code department} vs {@code parent}).
     */
    record SimpleOneOfXorInfo(
            String sortedJson0,
            String sortedJson1,
            boolean nestedIdRequiredForSorted0,
            boolean nestedIdRequiredForSorted1) {
    }

    /**
     * Find {@code oneOf} with exactly two object branches, each with a single {@code required} property name,
     * and the two names differ. Returns {@code [a,b]} in lexicographic order for stable output.
     */
    static String[] findSimpleOneOfXorPair(
            Map<String, Object> schema,
            Map<String, Object> spec,
            Map<Object, Boolean> visited,
            int depth) {
        SimpleOneOfXorInfo info = findSimpleOneOfXorInfo(schema, spec, visited, depth);
        return info == null ? null : new String[] { info.sortedJson0(), info.sortedJson1() };
    }

    /**
     * Same discovery as {@link #findSimpleOneOfXorPair}, plus whether each branch requires a nested {@code id}
     * on its sole required object property.
     */
    static SimpleOneOfXorInfo findSimpleOneOfXorInfo(
            Map<String, Object> schema,
            Map<String, Object> spec,
            Map<Object, Boolean> visited,
            int depth) {
        if (schema == null || depth > JerseySchemaUtils.MAX_MERGE_SCHEMA_DEPTH) {
            return null;
        }
        if (visited.containsKey(schema)) {
            return null;
        }
        visited.put(schema, Boolean.TRUE);

        Map<String, Object> node = resolveSchemaDeref(schema, spec, depth);

        if (node.containsKey("allOf")) {
            List<Map<String, Object>> parts = Util.asStringObjectMapList(node.get("allOf"));
            if (parts != null) {
                for (Map<String, Object> sub : parts) {
                    SimpleOneOfXorInfo found = findSimpleOneOfXorInfo(sub, spec, visited, depth + 1);
                    if (found != null) {
                        return found;
                    }
                }
            }
            return null;
        }

        if (node.containsKey("oneOf")) {
            List<Map<String, Object>> variants = Util.asStringObjectMapList(node.get("oneOf"));
            if (variants != null && variants.size() == 2) {
                String r0 = singleRequiredPropertyName(variants.get(0), spec);
                String r1 = singleRequiredPropertyName(variants.get(1), spec);
                if (r0 != null && r1 != null && !r0.equals(r1)) {
                    boolean n0 = nestedObjectRequiresId(variants.get(0), r0, spec);
                    boolean n1 = nestedObjectRequiresId(variants.get(1), r1, spec);
                    List<String> pair = new ArrayList<>(2);
                    pair.add(r0);
                    pair.add(r1);
                    Collections.sort(pair);
                    String s0 = pair.get(0);
                    String s1 = pair.get(1);
                    boolean ns0 = s0.equals(r0) ? n0 : n1;
                    boolean ns1 = s1.equals(r0) ? n0 : n1;
                    return new SimpleOneOfXorInfo(s0, s1, ns0, ns1);
                }
            }
            return null;
        }

        if (node.containsKey("anyOf")) {
            return null;
        }

        return null;
    }

    /**
     * True when {@code branch}'s {@code soleRequiredProp} schema (resolved) lists {@code id} in {@code required}
     * and defines a {@code properties.id} sub-schema.
     */
    private static boolean nestedObjectRequiresId(
            Map<String, Object> branch,
            String soleRequiredProp,
            Map<String, Object> spec) {
        if (branch == null || soleRequiredProp == null || spec == null) {
            return false;
        }
        Map<String, Object> node = resolveSchemaDeref(branch, spec, 0);
        Map<String, Object> props = Util.asStringObjectMap(node.get("properties"));
        if (props == null) {
            return false;
        }
        Map<String, Object> propSchema = Util.asStringObjectMap(props.get(soleRequiredProp));
        if (propSchema == null) {
            return false;
        }
        Map<String, Object> resolved = JerseySchemaUtils.resolveRefInSchema(propSchema, spec);
        Map<String, Object> effective = JerseySchemaUtils.resolveCompositionToEffectiveSchema(resolved, spec);
        if (effective == null) {
            return false;
        }
        List<String> req = Util.asStringList(effective.get("required"));
        if (req == null || !req.contains("id")) {
            return false;
        }
        Map<String, Object> innerProps = Util.asStringObjectMap(effective.get("properties"));
        return innerProps != null && innerProps.containsKey("id");
    }

    private static String singleRequiredPropertyName(Map<String, Object> branch, Map<String, Object> spec) {
        if (branch == null) {
            return null;
        }
        Map<String, Object> node = resolveSchemaDeref(branch, spec, 0);
        List<String> req = Util.asStringList(node.get("required"));
        if (req == null || req.size() != 1) {
            return null;
        }
        Map<String, Object> props = Util.asStringObjectMap(node.get("properties"));
        if (props == null || props.isEmpty()) {
            // allow required-only branch that references merged props from allOf parent — still need props for validation
            return null;
        }
        String name = req.get(0);
        if (!props.containsKey(name)) {
            return null;
        }
        return name;
    }
}

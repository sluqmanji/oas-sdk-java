package egain.oassdk.generators.java;

import egain.oassdk.Util;
import egain.oassdk.config.GeneratorConfig;
import egain.oassdk.core.exceptions.GenerationException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Generates immutable Java &quot;authorization data&quot; POJOs from OpenAPI vendor metadata.
 *
 * <h2>Extension: {@code x-egain-authorization-data}</h2>
 * Place on a schema under {@code components.schemas.&lt;name&gt;} (alongside {@code type},
 * {@code properties}, etc.). Generation runs only when {@link GeneratorConfig#isAuthorizationDataGenerationEnabled()}
 * is true.
 *
 * <h3>Schema shape (YAML)</h3>
 * <pre>{@code
 * x-egain-authorization-data:
 *   className: FolderAuthorizationData
 *   package: egain.ws.common.authorization.authoring.folder.data
 *   extends: egain.ws.common.authorization.KnowledgeAuthorizationData
 *   imports:
 *     - egain.ws.common.authorization.authoring.folder.FolderAuthorizer
 *   fields:
 *     - name: folderId
 *       type: long
 *     - name: ids
 *       type: java.util.List<Long>
 *     - name: operation
 *       type: FolderAuthorizer.Operation
 *     - name: mDepartmentId
 *       type: long
 *       default: -1
 *     - name: mParentFolderId
 *       type: long
 *       default: -1
 *   constructors:
 *     - parameters: [folderId, ids, operation]
 *     - parameters:
 *         - folderId
 *         - ids
 *         - name: departmentId
 *           assignsTo: mDepartmentId
 *         - name: parentFolderId
 *           assignsTo: mParentFolderId
 *         - operation
 * }</pre>
 *
 * <ul>
 *   <li>{@code className}, {@code package}, and {@code fields} are required.</li>
 *   <li>{@code extends} may be omitted if {@link GeneratorConfig#getDefaultAuthorizationDataExtends()} or
 *       {@code info.x-egain-authorization-data-defaults.extends} is set.</li>
 *   <li>Each field needs {@code name} and {@code type} (Java source type as you would write it).</li>
 *   <li>{@code default} is used when a field is not supplied by a constructor parameter; required for such fields.</li>
 *   <li>{@code constructors} is a list of constructor definitions. Each has {@code parameters}: strings (param and
 *       target field share the same name) or maps with {@code name} (parameter name) and optional {@code assignsTo}
 *       (target field name).</li>
 *   <li>Getters use standard JavaBean names; a leading {@code m} before an uppercase letter is stripped
 *       ({@code mDepartmentId} &rarr; {@code getDepartmentId}).</li>
 * </ul>
 *
 * <h3>Shared defaults</h3>
 * Optional {@code info.x-egain-authorization-data-defaults} map (e.g. {@code extends}) is merged into each
 * schema extension; per-schema keys override.
 */
class JerseyAuthorizationDataGenerator {

    static final String EXTENSION_KEY = "x-egain-authorization-data";
    static final String INFO_DEFAULTS_KEY = "x-egain-authorization-data-defaults";

    private static final Set<String> JAVA_LANG_TYPES = Set.of(
            "boolean", "byte", "short", "int", "long", "float", "double", "char", "void");

    void generate(Map<String, Object> spec, String outputDir, GeneratorConfig config) throws IOException, GenerationException {
        if (spec == null || outputDir == null || config == null || !config.isAuthorizationDataGenerationEnabled()) {
            return;
        }
        Map<String, Object> infoDefaults = loadInfoDefaults(spec);
        Map<String, Object> components = Util.asStringObjectMap(spec.get("components"));
        if (components == null) {
            return;
        }
        Map<String, Object> schemas = Util.asStringObjectMap(components.get("schemas"));
        if (schemas == null) {
            return;
        }
        for (Map.Entry<String, Object> entry : schemas.entrySet()) {
            Map<String, Object> schema = Util.asStringObjectMap(entry.getValue());
            if (schema == null || !schema.containsKey(EXTENSION_KEY)) {
                continue;
            }
            Map<String, Object> rawExt = Util.asStringObjectMap(schema.get(EXTENSION_KEY));
            if (rawExt == null) {
                continue;
            }
            Map<String, Object> ext = mergeExtension(infoDefaults, rawExt, config);
            GeneratedClass gc = parseAndValidate(entry.getKey(), ext);
            String java = render(gc);
            String relPath = gc.packageName.replace('.', '/') + "/" + gc.className + ".java";
            JerseyGenerationContext.writeFile(outputDir + "/src/main/java/" + relPath, java);
        }
    }

    private static Map<String, Object> loadInfoDefaults(Map<String, Object> spec) {
        Map<String, Object> info = Util.asStringObjectMap(spec.get("info"));
        if (info == null) {
            return Map.of();
        }
        Map<String, Object> d = Util.asStringObjectMap(info.get(INFO_DEFAULTS_KEY));
        return d != null ? d : Map.of();
    }

    private static Map<String, Object> mergeExtension(Map<String, Object> infoDefaults,
                                                      Map<String, Object> schemaExt,
                                                      GeneratorConfig config) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (infoDefaults != null) {
            merged.putAll(infoDefaults);
        }
        merged.putAll(schemaExt);
        if (!merged.containsKey("extends") || Objects.toString(merged.get("extends"), "").isBlank()) {
            String def = config.getDefaultAuthorizationDataExtends();
            if (def != null && !def.isBlank()) {
                merged.put("extends", def);
            }
        }
        return merged;
    }

    private static GeneratedClass parseAndValidate(String schemaName, Map<String, Object> ext) throws GenerationException {
        String className = stringOrEmpty(ext.get("className"));
        String packageName = stringOrEmpty(ext.get("package"));
        String extendsFqn = stringOrEmpty(ext.get("extends"));
        if (className.isEmpty()) {
            throw new GenerationException("x-egain-authorization-data on schema '" + schemaName + "': className is required");
        }
        if (packageName.isEmpty()) {
            throw new GenerationException("x-egain-authorization-data on schema '" + schemaName + "': package is required");
        }
        if (extendsFqn.isEmpty()) {
            throw new GenerationException("x-egain-authorization-data on schema '" + schemaName
                    + "': extends is required (or set GeneratorConfig.defaultAuthorizationDataExtends / info."
                    + INFO_DEFAULTS_KEY + ")");
        }
        List<Map<String, Object>> fieldMaps = Util.asStringObjectMapList(ext.get("fields"));
        if (fieldMaps == null || fieldMaps.isEmpty()) {
            throw new GenerationException("x-egain-authorization-data on schema '" + schemaName + "': fields must be non-empty");
        }
        List<FieldSpec> fields = new ArrayList<>();
        for (Map<String, Object> fm : fieldMaps) {
            if (fm == null) {
                continue;
            }
            String fn = stringOrEmpty(fm.get("name"));
            String ft = normalizeUtilListType(stringOrEmpty(fm.get("type")));
            if (fn.isEmpty() || ft.isEmpty()) {
                throw new GenerationException("x-egain-authorization-data on schema '" + schemaName + "': each field needs name and type");
            }
            fields.add(new FieldSpec(fn, ft, fm.get("default")));
        }
        Map<String, FieldSpec> fieldByName = fields.stream().collect(Collectors.toMap(f -> f.name, f -> f, (a, b) -> a, LinkedHashMap::new));

        List<String> importList = Util.asStringList(ext.get("imports"));

        List<Map<String, Object>> ctorMaps = Util.asStringObjectMapList(ext.get("constructors"));
        if (ctorMaps == null || ctorMaps.isEmpty()) {
            throw new GenerationException("x-egain-authorization-data on schema '" + schemaName + "': constructors must be non-empty");
        }
        List<ConstructorSpec> constructors = new ArrayList<>();
        for (Map<String, Object> cm : ctorMaps) {
            if (cm == null) {
                continue;
            }
            List<ParamSpec> params = parseParameters(schemaName, cm.get("parameters"), fieldByName);
            constructors.add(new ConstructorSpec(params));
        }

        for (int i = 0; i < constructors.size(); i++) {
            validateConstructor(schemaName, constructors.get(i), fieldByName, i);
        }

        return new GeneratedClass(packageName, className, extendsFqn, importList, fields, constructors);
    }

    private static List<ParamSpec> parseParameters(String schemaName, Object parametersObj,
                                                   Map<String, FieldSpec> fieldByName) throws GenerationException {
        if (!(parametersObj instanceof List)) {
            throw new GenerationException("x-egain-authorization-data on schema '" + schemaName + "': constructor parameters must be a list");
        }
        List<?> raw = (List<?>) parametersObj;
        List<ParamSpec> out = new ArrayList<>();
        for (Object item : raw) {
            if (item instanceof String s) {
                if (!fieldByName.containsKey(s)) {
                    throw new GenerationException("x-egain-authorization-data on schema '" + schemaName + "': unknown field '" + s + "' in constructor");
                }
                out.add(new ParamSpec(s, s));
            } else if (item instanceof Map) {
                Map<String, Object> m = Util.asStringObjectMap(item);
                if (m == null) {
                    continue;
                }
                String paramName = stringOrEmpty(m.get("name"));
                String assignsTo = stringOrEmpty(m.get("assignsTo"));
                if (paramName.isEmpty()) {
                    throw new GenerationException("x-egain-authorization-data on schema '" + schemaName + "': parameter entry needs name");
                }
                String fieldName = assignsTo.isEmpty() ? paramName : assignsTo;
                if (!fieldByName.containsKey(fieldName)) {
                    throw new GenerationException("x-egain-authorization-data on schema '" + schemaName + "': assignsTo field '" + fieldName + "' not found");
                }
                out.add(new ParamSpec(paramName, fieldName));
            } else {
                throw new GenerationException("x-egain-authorization-data on schema '" + schemaName + "': constructor parameter must be string or map");
            }
        }
        return out;
    }

    private static void validateConstructor(String schemaName, ConstructorSpec ctor,
                                            Map<String, FieldSpec> fieldByName, int ctorIndex) throws GenerationException {
        Set<String> covered = new LinkedHashSet<>();
        for (ParamSpec p : ctor.parameters) {
            covered.add(p.targetField);
        }
        for (FieldSpec f : fieldByName.values()) {
            if (covered.contains(f.name)) {
                continue;
            }
            if (f.defaultValue == null) {
                throw new GenerationException("x-egain-authorization-data on schema '" + schemaName + "': constructor #" + (ctorIndex + 1)
                        + " does not set field '" + f.name + "' and field has no default");
            }
        }
    }

    private static String render(GeneratedClass gc) {
        Set<String> imports = new LinkedHashSet<>();
        if (gc.explicitImports != null) {
            imports.addAll(gc.explicitImports);
        }
        for (FieldSpec f : gc.fields) {
            collectTypeImports(f.type, imports);
        }
        List<String> sortedImports = new ArrayList<>(imports);
        sortedImports.sort(String::compareTo);

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(gc.packageName).append(";\n\n");
        for (String imp : sortedImports) {
            sb.append("import ").append(imp).append(";\n");
        }
        sb.append("\npublic class ").append(gc.className).append(" extends ").append(gc.extendsFqn).append("\n");
        sb.append("{\n\n");

        for (FieldSpec f : gc.fields) {
            sb.append("\tprivate final ").append(f.type).append(" ").append(f.name).append(";\n");
        }
        sb.append("\n");

        for (ConstructorSpec ctor : gc.constructors) {
            sb.append("\tpublic ").append(gc.className).append("(");
            for (int i = 0; i < ctor.parameters.size(); i++) {
                ParamSpec p = ctor.parameters.get(i);
                FieldSpec fs = gc.fieldByName().get(p.targetField);
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(fs.type).append(" ").append(p.paramName);
            }
            sb.append(")\n\t{\n");
            for (ParamSpec p : ctor.parameters) {
                sb.append("\t\tthis.").append(p.targetField).append(" = ").append(p.paramName).append(";\n");
            }
            for (FieldSpec f : gc.fields) {
                boolean fromParam = ctor.parameters.stream().anyMatch(pr -> pr.targetField.equals(f.name));
                if (!fromParam) {
                    sb.append("\t\tthis.").append(f.name).append(" = ").append(renderJavaLiteral(f.defaultValue, f.type)).append(";\n");
                }
            }
            sb.append("\t}\n\n");
        }

        for (FieldSpec f : gc.fields) {
            sb.append("\tpublic ").append(f.type).append(" ").append(getterName(f.name)).append("()\n\t{\n");
            sb.append("\t\treturn ").append(f.name).append(";\n\t}\n\n");
        }

        sb.append("\t@Override\n");
        sb.append("\tpublic String toString()\n\t{\n");
        sb.append("\t\treturn \"").append(gc.className).append("{\" +\n");
        for (int i = 0; i < gc.fields.size(); i++) {
            FieldSpec f = gc.fields.get(i);
            if (i > 0) {
                sb.append("\t\t\t\t\", \" +\n");
            }
            sb.append("\t\t\t\t\"").append(f.name).append("=\" + ").append(f.name).append(" +\n");
        }
        sb.append("\t\t\t\t'}';\n");
        sb.append("\t}\n");
        sb.append("}\n");
        return sb.toString();
    }

    static String getterName(String fieldName) {
        String base = fieldName;
        if (fieldName.length() > 2 && fieldName.startsWith("m") && Character.isUpperCase(fieldName.charAt(1))) {
            base = fieldName.substring(1);
        }
        return "get" + base.substring(0, 1).toUpperCase(Locale.ROOT) + base.substring(1);
    }

    private static void collectTypeImports(String type, Set<String> imports) {
        String t = type.trim();
        if (t.contains("<")) {
            imports.add("java.util.List");
            int lt = t.indexOf('<');
            int gt = t.lastIndexOf('>');
            if (lt >= 0 && gt > lt) {
                String inner = t.substring(lt + 1, gt).trim();
                for (String part : splitTopLevelComma(inner)) {
                    collectSingleTypeImport(part.trim(), imports);
                }
            }
            return;
        }
        collectSingleTypeImport(t, imports);
    }

    private static void collectSingleTypeImport(String t, Set<String> imports) {
        if (t.isEmpty() || JAVA_LANG_TYPES.contains(t)) {
            return;
        }
        if (!t.contains(".")) {
            return;
        }
        imports.add(trimArrayDimensions(t));
    }

    private static String trimArrayDimensions(String t) {
        int bracket = t.indexOf('[');
        return bracket < 0 ? t : t.substring(0, bracket).trim();
    }

    private static List<String> splitTopLevelComma(String s) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '<') {
                depth++;
            } else if (c == '>') {
                depth--;
            } else if (c == ',' && depth == 0) {
                parts.add(s.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(s.substring(start));
        return parts;
    }

    private static String renderJavaLiteral(Object value, String javaType) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof Number) {
            String t = javaType.trim();
            if ("long".equals(t) || "Long".equals(t) || t.endsWith("long")) {
                return value + "L";
            }
            return value.toString();
        }
        if (value instanceof String str) {
            return "\"" + escapeJavaString(str) + "\"";
        }
        return String.valueOf(value);
    }

    private static String escapeJavaString(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String stringOrEmpty(Object o) {
        return o == null ? "" : Objects.toString(o, "").trim();
    }

    /** Use {@code List<...>} with import instead of fully qualified {@code java.util.List<...>}. */
    private static String normalizeUtilListType(String type) {
        String t = type.trim();
        if (t.startsWith("java.util.List<") && t.endsWith(">")) {
            return t.substring("java.util.".length());
        }
        return t;
    }

    private record FieldSpec(String name, String type, Object defaultValue) {}

    private record ParamSpec(String paramName, String targetField) {}

    private record ConstructorSpec(List<ParamSpec> parameters) {}

    private record GeneratedClass(String packageName, String className, String extendsFqn, List<String> explicitImports,
                                  List<FieldSpec> fields, List<ConstructorSpec> constructors) {
        Map<String, FieldSpec> fieldByName() {
            return fields.stream().collect(Collectors.toMap(f -> f.name, f -> f, (a, b) -> a, LinkedHashMap::new));
        }
    }
}

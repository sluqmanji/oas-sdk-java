package egain.oassdk.generators.java;

import java.util.Locale;

/**
 * Pure naming / string-conversion utilities extracted from JerseyGenerator.
 * All methods are stateless and static.
 */
public final class JerseyNamingUtils {

    private JerseyNamingUtils() {
        // utility class – no instances
    }

    /**
     * Convert schema name to valid Java class name
     */
    public static String toJavaClassName(String schemaName) {
        if (schemaName == null || schemaName.isEmpty()) {
            return "Unknown";
        }

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

        if (result.isEmpty() || !Character.isLetter(result.charAt(0))) {
            return "Schema" + result;
        }

        return result.toString();
    }

    /**
     * Convert operationId to valid Java method name (camelCase, first letter lower).
     * Handles hyphens, underscores, spaces, dots as word breaks (e.g. get-jobId-status -> getJobIdStatus).
     */
    public static String toJavaMethodName(String operationId) {
        if (operationId == null || operationId.isEmpty()) {
            return "op";
        }
        String className = toJavaClassName(operationId);
        if (className.isEmpty() || !Character.isLetter(className.charAt(0))) {
            return "op";
        }
        return Character.toLowerCase(className.charAt(0)) + className.substring(1);
    }

    /**
     * Convert snake_case to camelCase
     */
    public static String toCamelCase(String snakeCase) {
        if (snakeCase == null || snakeCase.isEmpty()) {
            return snakeCase;
        }
        String[] parts = snakeCase.split("_");
        StringBuilder result = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            result.append(capitalize(parts[i]));
        }
        return result.toString();
    }

    /**
     * Capitalize first letter
     */
    public static String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase(Locale.ROOT) + str.substring(1);
    }

    /**
     * Convert OpenAPI property name to a valid Java field name for model classes.
     * Prepends underscore if the camelCase form is a Java keyword (e.g. "case" -> "_case").
     */
    public static String toModelFieldName(String openApiPropertyName) {
        String camel = toCamelCase(openApiPropertyName);
        return (camel != null && isJavaKeyword(camel)) ? "_" + camel : camel;
    }

    /**
     * Return the capitalized bean property name for getter/setter method names.
     * Strips leading underscore so getCase/setCase are used instead of get_Case/set_Case.
     */
    public static String getCapitalizedPropertyNameForAccessor(String javaFieldName) {
        if (javaFieldName == null || javaFieldName.isEmpty()) {
            return javaFieldName;
        }
        String base = javaFieldName.startsWith("_") ? javaFieldName.substring(1) : javaFieldName;
        return capitalize(base);
    }

    /**
     * Return the name to use in JAXB propOrder (bean property name without leading underscore).
     */
    public static String toPropOrderName(String javaFieldName) {
        if (javaFieldName != null && javaFieldName.startsWith("_")) {
            return javaFieldName.substring(1);
        }
        return javaFieldName;
    }

    /**
     * Sanitize parameter name to be a valid Java identifier
     */
    public static String sanitizeParameterName(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }

        if (isValidJavaIdentifier(name)) {
            return name;
        }

        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = false;

        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);

            if (i == 0) {
                if (Character.isLetter(c) || c == '_' || c == '$') {
                    result.append(c);
                } else if (Character.isDigit(c)) {
                    result.append('_').append(c);
                } else {
                    capitalizeNext = true;
                }
            } else {
                if (Character.isLetterOrDigit(c) || c == '_' || c == '$') {
                    if (capitalizeNext) {
                        result.append(Character.toUpperCase(c));
                        capitalizeNext = false;
                    } else {
                        result.append(c);
                    }
                } else if (c == '-' || c == '.' || c == ' ') {
                    capitalizeNext = true;
                }
            }
        }

        String sanitized = result.toString();

        if (sanitized.isEmpty() || !isValidJavaIdentifier(sanitized)) {
            return null;
        }

        return sanitized;
    }

    /**
     * Sanitize a package name segment: lower-case, and prefix with underscore if it is a Java keyword.
     */
    public static String sanitizePackageName(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        name = name.toLowerCase(Locale.ENGLISH);

        if (!isJavaKeyword(name)) {
            return name;
        }

        return "_" + name;
    }

    /**
     * Check if a string is a valid Java identifier
     */
    public static boolean isValidJavaIdentifier(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }

        char first = name.charAt(0);
        if (!Character.isLetter(first) && first != '_' && first != '$') {
            return false;
        }

        for (int i = 1; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_' && c != '$') {
                return false;
            }
        }

        return !isJavaKeyword(name);
    }

    /**
     * Check if a string is a Java keyword
     */
    public static boolean isJavaKeyword(String name) {
        String[] keywords = {
                "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
                "class", "const", "continue", "default", "do", "double", "else", "enum",
                "extends", "final", "finally", "float", "for", "goto", "if", "implements",
                "import", "instanceof", "int", "interface", "long", "native", "new", "package",
                "private", "protected", "public", "return", "short", "static", "strictfp",
                "super", "switch", "synchronized", "this", "throw", "throws", "transient",
                "try", "void", "volatile", "while", "true", "false", "null"
        };

        for (String keyword : keywords) {
            if (keyword.equals(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Escape Java string for use in generated code
     */
    public static String escapeJavaString(String str) {
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Escape a regex pattern for use inside a Java string literal (e.g. in @Pattern(regexp = "...")).
     * Escapes only backslashes and double quotes for the Java string; parentheses are left as-is
     * so they remain regex grouping metacharacters (e.g. (\.\d{3})? for optional milliseconds).
     */
    public static String escapePatternForJavaStringLiteral(String pattern) {
        if (pattern == null) return "";
        StringBuilder sb = new StringBuilder();
        boolean afterBackslash = false;
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '\\') {
                sb.append("\\\\");
                afterBackslash = !afterBackslash;
            } else if (c == '"') {
                sb.append("\\\"");
                afterBackslash = false;
            } else {
                sb.append(c);
                afterBackslash = false;
            }
        }
        return sb.toString();
    }

    /**
     * Generate operationId from path and method if not provided
     */
    public static String generateOperationIdFromPath(String path, String method) {
        // Clean path and convert to camelCase
        String cleanPath = path.replaceAll("[^a-zA-Z0-9]", "");
        if (cleanPath.isEmpty()) {
            cleanPath = "root";
        }
        return method.toLowerCase(Locale.ROOT) + capitalize(cleanPath);
    }
}

package egain.oassdk.generators.java;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JerseyNamingUtils static utility methods.
 */
class JerseyNamingUtilsTest {

    // -----------------------------------------------------------------------
    //  toJavaClassName
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("toJavaClassName converts hyphenated name to PascalCase")
    void toJavaClassName_hyphenated() {
        assertEquals("UserProfile", JerseyNamingUtils.toJavaClassName("user-profile"));
    }

    @Test
    @DisplayName("toJavaClassName converts underscored name to PascalCase")
    void toJavaClassName_underscored() {
        assertEquals("MyApiModel", JerseyNamingUtils.toJavaClassName("my_api_model"));
    }

    @Test
    @DisplayName("toJavaClassName preserves already-PascalCase name")
    void toJavaClassName_alreadyPascal() {
        assertEquals("User", JerseyNamingUtils.toJavaClassName("User"));
    }

    @Test
    @DisplayName("toJavaClassName returns Unknown for null input")
    void toJavaClassName_null() {
        assertEquals("Unknown", JerseyNamingUtils.toJavaClassName(null));
    }

    @Test
    @DisplayName("toJavaClassName returns Unknown for empty input")
    void toJavaClassName_empty() {
        assertEquals("Unknown", JerseyNamingUtils.toJavaClassName(""));
    }

    @Test
    @DisplayName("toJavaClassName prepends Schema when first char is digit")
    void toJavaClassName_leadingDigit() {
        String result = JerseyNamingUtils.toJavaClassName("123model");
        assertTrue(result.startsWith("Schema"));
    }

    // -----------------------------------------------------------------------
    //  toJavaMethodName
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("toJavaMethodName preserves camelCase operationId")
    void toJavaMethodName_camelCase() {
        assertEquals("getUsers", JerseyNamingUtils.toJavaMethodName("getUsers"));
    }

    @Test
    @DisplayName("toJavaMethodName converts PascalCase to camelCase")
    void toJavaMethodName_pascalCase() {
        assertEquals("getUsers", JerseyNamingUtils.toJavaMethodName("GetUsers"));
    }

    @Test
    @DisplayName("toJavaMethodName returns op for null input")
    void toJavaMethodName_null() {
        assertEquals("op", JerseyNamingUtils.toJavaMethodName(null));
    }

    @Test
    @DisplayName("toJavaMethodName converts hyphenated to camelCase")
    void toJavaMethodName_hyphenated() {
        assertEquals("getJobIdStatus", JerseyNamingUtils.toJavaMethodName("get-jobId-status"));
    }

    // -----------------------------------------------------------------------
    //  toCamelCase
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("toCamelCase converts snake_case to camelCase")
    void toCamelCase_snakeCase() {
        assertEquals("helloWorld", JerseyNamingUtils.toCamelCase("hello_world"));
    }

    @Test
    @DisplayName("toCamelCase handles single word")
    void toCamelCase_singleWord() {
        assertEquals("hello", JerseyNamingUtils.toCamelCase("hello"));
    }

    @Test
    @DisplayName("toCamelCase returns null for null input")
    void toCamelCase_null() {
        assertNull(JerseyNamingUtils.toCamelCase(null));
    }

    // -----------------------------------------------------------------------
    //  capitalize
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("capitalize upper-cases first letter")
    void capitalize_normal() {
        assertEquals("Hello", JerseyNamingUtils.capitalize("hello"));
    }

    @Test
    @DisplayName("capitalize returns null for null input")
    void capitalize_null() {
        assertNull(JerseyNamingUtils.capitalize(null));
    }

    @Test
    @DisplayName("capitalize returns empty for empty input")
    void capitalize_empty() {
        assertEquals("", JerseyNamingUtils.capitalize(""));
    }

    // -----------------------------------------------------------------------
    //  toModelFieldName
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("toModelFieldName converts PascalCase to camelCase via toCamelCase")
    void toModelFieldName_pascalCase() {
        // "UserName" has no underscores so toCamelCase returns it unchanged,
        // but it's not a keyword so no underscore prefix
        assertEquals("UserName", JerseyNamingUtils.toModelFieldName("UserName"));
    }

    @Test
    @DisplayName("toModelFieldName prepends underscore for Java keyword")
    void toModelFieldName_keyword() {
        assertEquals("_case", JerseyNamingUtils.toModelFieldName("case"));
    }

    // -----------------------------------------------------------------------
    //  sanitizeParameterName
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("sanitizeParameterName converts hyphens to camelCase")
    void sanitizeParameterName_hyphen() {
        assertEquals("myParam", JerseyNamingUtils.sanitizeParameterName("my-param"));
    }

    @Test
    @DisplayName("sanitizeParameterName passes through valid identifier")
    void sanitizeParameterName_valid() {
        assertEquals("myParam", JerseyNamingUtils.sanitizeParameterName("myParam"));
    }

    @Test
    @DisplayName("sanitizeParameterName returns null for null input")
    void sanitizeParameterName_null() {
        assertNull(JerseyNamingUtils.sanitizeParameterName(null));
    }

    // -----------------------------------------------------------------------
    //  isJavaKeyword
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("isJavaKeyword returns true for 'class'")
    void isJavaKeyword_class() {
        assertTrue(JerseyNamingUtils.isJavaKeyword("class"));
    }

    @Test
    @DisplayName("isJavaKeyword returns false for regular identifier")
    void isJavaKeyword_regular() {
        assertFalse(JerseyNamingUtils.isJavaKeyword("myVar"));
    }

    @Test
    @DisplayName("isJavaKeyword recognises null literal as keyword")
    void isJavaKeyword_nullLiteral() {
        assertTrue(JerseyNamingUtils.isJavaKeyword("null"));
    }

    // -----------------------------------------------------------------------
    //  escapeJavaString
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("escapeJavaString escapes double quotes")
    void escapeJavaString_quotes() {
        assertEquals("hello\\\"world", JerseyNamingUtils.escapeJavaString("hello\"world"));
    }

    @Test
    @DisplayName("escapeJavaString escapes backslashes")
    void escapeJavaString_backslash() {
        assertEquals("a\\\\b", JerseyNamingUtils.escapeJavaString("a\\b"));
    }

    @Test
    @DisplayName("escapeJavaString escapes newlines")
    void escapeJavaString_newline() {
        assertEquals("a\\nb", JerseyNamingUtils.escapeJavaString("a\nb"));
    }

    // -----------------------------------------------------------------------
    //  escapePatternForJavaStringLiteral
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("escapePatternForJavaStringLiteral escapes backslashes and quotes")
    void escapePattern_backslashAndQuote() {
        // Input: \d+"
        // Expected: \\d+\"
        assertEquals("\\\\d+\\\"", JerseyNamingUtils.escapePatternForJavaStringLiteral("\\d+\""));
    }

    @Test
    @DisplayName("escapePatternForJavaStringLiteral preserves regex parentheses")
    void escapePattern_parentheses() {
        // Regex grouping parentheses should NOT be escaped
        String input = "(\\d{3})?";
        String result = JerseyNamingUtils.escapePatternForJavaStringLiteral(input);
        assertTrue(result.contains("("));
        assertTrue(result.contains(")"));
        assertTrue(result.contains("\\\\d"));
    }

    @Test
    @DisplayName("escapePatternForJavaStringLiteral returns empty for null")
    void escapePattern_null() {
        assertEquals("", JerseyNamingUtils.escapePatternForJavaStringLiteral(null));
    }

    // -----------------------------------------------------------------------
    //  generateOperationIdFromPath
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("generateOperationIdFromPath generates id from path and method")
    void generateOperationIdFromPath_normal() {
        assertEquals("getUsersid", JerseyNamingUtils.generateOperationIdFromPath("/users/{id}", "get"));
    }

    @Test
    @DisplayName("generateOperationIdFromPath uses 'root' for empty cleaned path")
    void generateOperationIdFromPath_root() {
        assertEquals("getRoot", JerseyNamingUtils.generateOperationIdFromPath("/", "get"));
    }

    // -----------------------------------------------------------------------
    //  isValidJavaIdentifier
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("isValidJavaIdentifier returns true for valid identifier")
    void isValidJavaIdentifier_valid() {
        assertTrue(JerseyNamingUtils.isValidJavaIdentifier("myVar"));
    }

    @Test
    @DisplayName("isValidJavaIdentifier returns false for keyword")
    void isValidJavaIdentifier_keyword() {
        assertFalse(JerseyNamingUtils.isValidJavaIdentifier("class"));
    }

    @Test
    @DisplayName("isValidJavaIdentifier returns false for identifier starting with digit")
    void isValidJavaIdentifier_startsWithDigit() {
        assertFalse(JerseyNamingUtils.isValidJavaIdentifier("1abc"));
    }

    // -----------------------------------------------------------------------
    //  sanitizePackageName
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("sanitizePackageName lower-cases and prefixes keyword")
    void sanitizePackageName_keyword() {
        assertEquals("_new", JerseyNamingUtils.sanitizePackageName("new"));
    }

    @Test
    @DisplayName("sanitizePackageName lower-cases normal name")
    void sanitizePackageName_normal() {
        assertEquals("mypackage", JerseyNamingUtils.sanitizePackageName("MyPackage"));
    }

    // -----------------------------------------------------------------------
    //  getCapitalizedPropertyNameForAccessor
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("getCapitalizedPropertyNameForAccessor strips leading underscore and capitalizes")
    void getCapitalizedPropertyNameForAccessor_underscore() {
        assertEquals("Case", JerseyNamingUtils.getCapitalizedPropertyNameForAccessor("_case"));
    }

    @Test
    @DisplayName("getCapitalizedPropertyNameForAccessor capitalizes normal field")
    void getCapitalizedPropertyNameForAccessor_normal() {
        assertEquals("Name", JerseyNamingUtils.getCapitalizedPropertyNameForAccessor("name"));
    }

    // -----------------------------------------------------------------------
    //  toPropOrderName
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("toPropOrderName strips leading underscore")
    void toPropOrderName_underscore() {
        assertEquals("case", JerseyNamingUtils.toPropOrderName("_case"));
    }

    @Test
    @DisplayName("toPropOrderName passes through normal field")
    void toPropOrderName_normal() {
        assertEquals("name", JerseyNamingUtils.toPropOrderName("name"));
    }
}

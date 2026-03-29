package egain.oassdk.generators.java;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Unit tests for JerseySchemaUtils static utility methods.
 */
class JerseySchemaUtilsTest {

    // -----------------------------------------------------------------------
    //  isSchemaFlagTrue
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("isSchemaFlagTrue returns true for Boolean TRUE value")
    void isSchemaFlagTrue_booleanTrue() {
        Map<String, Object> schema = Map.of("readOnly", Boolean.TRUE);
        assertTrue(JerseySchemaUtils.isSchemaFlagTrue(schema, "readOnly"));
    }

    @Test
    @DisplayName("isSchemaFlagTrue returns true for string 'true'")
    void isSchemaFlagTrue_stringTrue() {
        Map<String, Object> schema = Map.of("readOnly", "true");
        assertTrue(JerseySchemaUtils.isSchemaFlagTrue(schema, "readOnly"));
    }

    @Test
    @DisplayName("isSchemaFlagTrue returns false for Boolean FALSE value")
    void isSchemaFlagTrue_booleanFalse() {
        Map<String, Object> schema = Map.of("readOnly", Boolean.FALSE);
        assertFalse(JerseySchemaUtils.isSchemaFlagTrue(schema, "readOnly"));
    }

    @Test
    @DisplayName("isSchemaFlagTrue returns false when key is absent")
    void isSchemaFlagTrue_absent() {
        Map<String, Object> schema = Map.of("type", "string");
        assertFalse(JerseySchemaUtils.isSchemaFlagTrue(schema, "readOnly"));
    }

    @Test
    @DisplayName("isSchemaFlagTrue returns false for null schema")
    void isSchemaFlagTrue_nullSchema() {
        assertFalse(JerseySchemaUtils.isSchemaFlagTrue(null, "readOnly"));
    }

    // -----------------------------------------------------------------------
    //  getSchemaNameFromRef
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("getSchemaNameFromRef extracts name from $ref")
    void getSchemaNameFromRef_dollarRef() {
        Map<String, Object> schema = Map.of("$ref", "#/components/schemas/User");
        assertEquals("User", JerseySchemaUtils.getSchemaNameFromRef(schema));
    }

    @Test
    @DisplayName("getSchemaNameFromRef extracts name from x-resolved-ref")
    void getSchemaNameFromRef_resolvedRef() {
        Map<String, Object> schema = Map.of("x-resolved-ref", "#/components/schemas/UserView");
        assertEquals("UserView", JerseySchemaUtils.getSchemaNameFromRef(schema));
    }

    @Test
    @DisplayName("getSchemaNameFromRef returns null when no $ref")
    void getSchemaNameFromRef_noRef() {
        Map<String, Object> schema = Map.of("type", "string");
        assertNull(JerseySchemaUtils.getSchemaNameFromRef(schema));
    }

    @Test
    @DisplayName("getSchemaNameFromRef returns null for null schema")
    void getSchemaNameFromRef_nullSchema() {
        assertNull(JerseySchemaUtils.getSchemaNameFromRef(null));
    }

    @Test
    @DisplayName("getSchemaNameFromRef returns null for non-component ref")
    void getSchemaNameFromRef_nonComponentRef() {
        Map<String, Object> schema = Map.of("$ref", "#/definitions/User");
        assertNull(JerseySchemaUtils.getSchemaNameFromRef(schema));
    }

    // -----------------------------------------------------------------------
    //  deriveSchemaNameFromExternalRef
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("deriveSchemaNameFromExternalRef extracts name from yaml file ref")
    void deriveSchemaNameFromExternalRef_yaml() {
        assertEquals("User", JerseySchemaUtils.deriveSchemaNameFromExternalRef("./User.yaml"));
    }

    @Test
    @DisplayName("deriveSchemaNameFromExternalRef handles path with directories")
    void deriveSchemaNameFromExternalRef_withPath() {
        assertEquals("User", JerseySchemaUtils.deriveSchemaNameFromExternalRef("models/v3/User.yaml"));
    }

    @Test
    @DisplayName("deriveSchemaNameFromExternalRef handles yml extension")
    void deriveSchemaNameFromExternalRef_yml() {
        assertEquals("Order", JerseySchemaUtils.deriveSchemaNameFromExternalRef("Order.yml"));
    }

    @Test
    @DisplayName("deriveSchemaNameFromExternalRef handles json extension")
    void deriveSchemaNameFromExternalRef_json() {
        assertEquals("Product", JerseySchemaUtils.deriveSchemaNameFromExternalRef("Product.json"));
    }

    @Test
    @DisplayName("deriveSchemaNameFromExternalRef returns null for null input")
    void deriveSchemaNameFromExternalRef_null() {
        assertNull(JerseySchemaUtils.deriveSchemaNameFromExternalRef(null));
    }

    @Test
    @DisplayName("deriveSchemaNameFromExternalRef returns null for empty input")
    void deriveSchemaNameFromExternalRef_empty() {
        assertNull(JerseySchemaUtils.deriveSchemaNameFromExternalRef(""));
    }

    // -----------------------------------------------------------------------
    //  resolveRefInSchema
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("resolveRefInSchema resolves valid $ref to component schema")
    void resolveRefInSchema_valid() {
        Map<String, Object> userSchema = new LinkedHashMap<>();
        userSchema.put("type", "object");
        userSchema.put("properties", Map.of("name", Map.of("type", "string")));

        // Build spec with mutable maps so Util.asStringObjectMap returns the original objects
        Map<String, Object> schemas = new LinkedHashMap<>();
        schemas.put("User", userSchema);
        Map<String, Object> components = new LinkedHashMap<>();
        components.put("schemas", schemas);
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("components", components);

        Map<String, Object> refSchema = new LinkedHashMap<>();
        refSchema.put("$ref", "#/components/schemas/User");

        Map<String, Object> resolved = JerseySchemaUtils.resolveRefInSchema(refSchema, spec);
        assertEquals("object", resolved.get("type"));
        assertNotNull(resolved.get("properties"));
    }

    @Test
    @DisplayName("resolveRefInSchema returns original when $ref target not found")
    void resolveRefInSchema_notFound() {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("components", Map.of("schemas", Map.of()));

        Map<String, Object> refSchema = new LinkedHashMap<>();
        refSchema.put("$ref", "#/components/schemas/Missing");

        Map<String, Object> resolved = JerseySchemaUtils.resolveRefInSchema(refSchema, spec);
        assertSame(refSchema, resolved);
    }

    @Test
    @DisplayName("resolveRefInSchema returns original when spec is null")
    void resolveRefInSchema_nullSpec() {
        Map<String, Object> refSchema = Map.of("$ref", "#/components/schemas/User");
        assertSame(refSchema, JerseySchemaUtils.resolveRefInSchema(refSchema, null));
    }

    @Test
    @DisplayName("resolveRefInSchema returns original when no $ref")
    void resolveRefInSchema_noRef() {
        Map<String, Object> schema = Map.of("type", "string");
        Map<String, Object> spec = Map.of();
        assertSame(schema, JerseySchemaUtils.resolveRefInSchema(schema, spec));
    }

    // -----------------------------------------------------------------------
    //  resolveCompositionToEffectiveSchema
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("resolveCompositionToEffectiveSchema merges allOf schemas")
    void resolveComposition_allOf() {
        Map<String, Object> sub1 = new LinkedHashMap<>();
        sub1.put("type", "object");
        sub1.put("properties", Map.of("name", Map.of("type", "string")));

        Map<String, Object> sub2 = new LinkedHashMap<>();
        sub2.put("properties", Map.of("age", Map.of("type", "integer")));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("allOf", List.of(sub1, sub2));

        Map<String, Object> effective = JerseySchemaUtils.resolveCompositionToEffectiveSchema(schema, null);
        assertNotNull(effective);
        assertEquals("object", effective.get("type"));
    }

    @Test
    @DisplayName("resolveCompositionToEffectiveSchema picks first oneOf branch")
    void resolveComposition_oneOf() {
        Map<String, Object> branch1 = new LinkedHashMap<>();
        branch1.put("type", "string");

        Map<String, Object> branch2 = new LinkedHashMap<>();
        branch2.put("type", "integer");

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("oneOf", List.of(branch1, branch2));

        Map<String, Object> effective = JerseySchemaUtils.resolveCompositionToEffectiveSchema(schema, null);
        assertEquals("string", effective.get("type"));
    }

    @Test
    @DisplayName("resolveCompositionToEffectiveSchema returns original when no composition")
    void resolveComposition_noComposition() {
        Map<String, Object> schema = Map.of("type", "string");
        assertSame(schema, JerseySchemaUtils.resolveCompositionToEffectiveSchema(schema, null));
    }

    // -----------------------------------------------------------------------
    //  mergeSchemaProperties
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("mergeSchemaProperties merges direct properties")
    void mergeSchemaProperties_direct() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("properties", Map.of("name", Map.of("type", "string")));
        schema.put("required", List.of("name"));

        Map<String, Object> allProps = new LinkedHashMap<>();
        java.util.List<String> allRequired = new java.util.ArrayList<>();

        JerseySchemaUtils.mergeSchemaProperties(schema, allProps, allRequired, Map.of());

        assertTrue(allProps.containsKey("name"));
        assertTrue(allRequired.contains("name"));
    }

    // -----------------------------------------------------------------------
    //  isSchemaReference
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("isSchemaReference matches by schema name")
    void isSchemaReference_matches() {
        Map<String, Object> schema = Map.of("$ref", "#/components/schemas/User");
        assertTrue(JerseySchemaUtils.isSchemaReference(schema, "User"));
    }

    @Test
    @DisplayName("isSchemaReference returns false for different name")
    void isSchemaReference_different() {
        Map<String, Object> schema = Map.of("$ref", "#/components/schemas/User");
        assertFalse(JerseySchemaUtils.isSchemaReference(schema, "Order"));
    }

    @Test
    @DisplayName("isSchemaReference returns false for null schema")
    void isSchemaReference_null() {
        assertFalse(JerseySchemaUtils.isSchemaReference(null, "User"));
    }
}

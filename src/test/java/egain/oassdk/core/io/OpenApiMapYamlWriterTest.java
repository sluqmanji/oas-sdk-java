package egain.oassdk.core.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiMapYamlWriterTest {

    @Test
    void copyForSerialization_duplicatesSharedMapsAndLists() throws IOException {
        Map<String, Object> sharedProps = new LinkedHashMap<>();
        sharedProps.put("code", Map.of("type", "string"));
        List<String> sharedRequired = new ArrayList<>(List.of("code"));

        Map<String, Object> schemaA = new LinkedHashMap<>();
        schemaA.put("type", "object");
        schemaA.put("properties", sharedProps);
        schemaA.put("required", sharedRequired);

        Map<String, Object> schemaB = new LinkedHashMap<>();
        schemaB.put("type", "object");
        schemaB.put("properties", sharedProps);
        schemaB.put("required", sharedRequired);

        Map<String, Object> components = new LinkedHashMap<>();
        Map<String, Object> schemas = new LinkedHashMap<>();
        schemas.put("A", schemaA);
        schemas.put("B", schemaB);
        components.put("schemas", schemas);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("openapi", "3.0.0");
        root.put("components", components);

        Map<String, Object> copy = OpenApiMapYamlWriter.copyForSerialization(root);
        @SuppressWarnings("unchecked")
        Map<String, Object> schemasOut = (Map<String, Object>)
                ((Map<String, Object>) copy.get("components")).get("schemas");
        @SuppressWarnings("unchecked")
        Map<String, Object> propsA = (Map<String, Object>)
                ((Map<String, Object>) schemasOut.get("A")).get("properties");
        @SuppressWarnings("unchecked")
        Map<String, Object> propsB = (Map<String, Object>)
                ((Map<String, Object>) schemasOut.get("B")).get("properties");
        assertTrue(propsA != propsB && propsA.equals(propsB), "shared subgraph should be duplicated, not aliased");

        Path tmp = Files.createTempFile("openapi-writer-", ".yaml");
        try {
            new OpenApiMapYamlWriter().write(root, tmp);
            String yaml = Files.readString(tmp);
            assertFalse(yaml.contains("<circular-reference>"), yaml);
            assertTrue(yaml.contains("code"));
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void copyForSerialization_breaksTrueCycleWithEmptyMap(@TempDir Path tempDir) throws IOException {
        Map<String, Object> cyclic = new LinkedHashMap<>();
        cyclic.put("nested", cyclic);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("openapi", "3.0.0");
        root.put("xCyclic", cyclic);

        Map<String, Object> copy = OpenApiMapYamlWriter.copyForSerialization(root);
        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) ((Map<String, Object>) copy.get("xCyclic")).get("nested");
        assertTrue(nested.isEmpty());

        Path out = tempDir.resolve("cycle.yaml");
        new OpenApiMapYamlWriter().write(root, out);
        assertFalse(Files.readString(out).contains("<circular-reference>"));
    }

    @Test
    void copyForSerialization_breaksTrueCycleInList() {
        List<Object> list = new ArrayList<>();
        list.add(list);

        Map<String, Object> root = Map.of("items", list);
        Map<String, Object> copy = OpenApiMapYamlWriter.copyForSerialization(root);
        assertInstanceOf(List.class, copy.get("items"));
        @SuppressWarnings("unchecked")
        List<Object> out = (List<Object>) copy.get("items");
        assertEquals(1, out.size());
        assertInstanceOf(List.class, out.get(0));
        assertTrue(((List<?>) out.get(0)).isEmpty());
    }
}

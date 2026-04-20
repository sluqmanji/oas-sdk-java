package egain.oassdk.core.io;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import egain.oassdk.Util;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Writes a parsed OpenAPI document (root map) to UTF-8 YAML using a serialization-safe deep copy.
 */
public final class OpenApiMapYamlWriter {

    private final ObjectMapper yamlMapper;

    public OpenApiMapYamlWriter() {
        YAMLFactory yamlFactory = new YAMLFactory();
        yamlFactory.configure(YAMLGenerator.Feature.WRITE_DOC_START_MARKER, false);
        yamlFactory.configure(YAMLGenerator.Feature.MINIMIZE_QUOTES, true);
        yamlFactory.configure(YAMLGenerator.Feature.INDENT_ARRAYS, true);
        this.yamlMapper = new ObjectMapper(yamlFactory);
        this.yamlMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Deep copy for YAML/JSON serialization. The same {@link Map} or {@link List} instance may appear
     * from multiple parents (a DAG); each reference is copied independently. Only true cycles
     * (re-entering the same collection while it is still being copied) are replaced with an empty
     * map or list so output stays valid.
     *
     * @param root parsed OpenAPI root map
     * @return a tree-shaped copy safe for Jackson serialization
     */
    public static Map<String, Object> copyForSerialization(Map<String, Object> root) {
        Object copied = deepCopyValue(root, Collections.newSetFromMap(new IdentityHashMap<>()));
        return Util.asStringObjectMap(copied);
    }

    /**
     * @param spec   root specification map
     * @param target path to write (e.g. openapi.yaml)
     */
    public void write(Map<String, Object> spec, Path target) throws java.io.IOException {
        Map<String, Object> safe = copyForSerialization(spec);
        String yaml = yamlMapper.writeValueAsString(safe);
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(target, yaml, StandardCharsets.UTF_8);
    }

    private static Object deepCopyValue(Object value, Set<Object> visiting) {
        if (value == null) {
            return null;
        }
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof com.fasterxml.jackson.databind.JsonNode) {
            return value;
        }
        if (value instanceof Map<?, ?> rawMap) {
            if (visiting.contains(value)) {
                return new LinkedHashMap<>();
            }
            visiting.add(value);
            try {
                Map<String, Object> copied = new LinkedHashMap<>();
                for (Map.Entry<?, ?> e : rawMap.entrySet()) {
                    Object k = e.getKey();
                    String key = k == null ? "null" : String.valueOf(k);
                    copied.put(key, deepCopyValue(e.getValue(), visiting));
                }
                return copied;
            } finally {
                visiting.remove(value);
            }
        }
        if (value instanceof Iterable<?> iterable) {
            if (visiting.contains(value)) {
                return new ArrayList<>();
            }
            visiting.add(value);
            try {
                List<Object> copied = new ArrayList<>();
                for (Object item : iterable) {
                    copied.add(deepCopyValue(item, visiting));
                }
                return copied;
            } finally {
                visiting.remove(value);
            }
        }
        return String.valueOf(value);
    }
}

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
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
     * @param spec   root specification map
     * @param target path to write (e.g. openapi.yaml)
     */
    public void write(Map<String, Object> spec, Path target) throws java.io.IOException {
        Map<String, Object> safe = prepareForSerialization(spec);
        String yaml = yamlMapper.writeValueAsString(safe);
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(target, yaml, StandardCharsets.UTF_8);
    }

    private Map<String, Object> prepareForSerialization(Map<String, Object> root) {
        Object copied = deepCopy(root, new IdentityHashMap<>());
        return Util.asStringObjectMap(copied);
    }

    private Object deepCopy(Object value, Map<Object, Object> seen) {
        if (value == null) {
            return null;
        }
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof com.fasterxml.jackson.databind.JsonNode) {
            return value;
        }
        if (seen.containsKey(value)) {
            return "<circular-reference>";
        }
        seen.put(value, Boolean.TRUE);

        if (value instanceof Map<?, ?> rawMap) {
            Map<String, Object> copied = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : rawMap.entrySet()) {
                Object k = e.getKey();
                String key = k == null ? "null" : String.valueOf(k);
                copied.put(key, deepCopy(e.getValue(), seen));
            }
            return copied;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> copied = new ArrayList<>();
            for (Object item : iterable) {
                copied.add(deepCopy(item, seen));
            }
            return copied;
        }
        return String.valueOf(value);
    }
}

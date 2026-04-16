package egain.oassdk.testgenerators.schemathesis;

import egain.oassdk.config.TestConfig;
import egain.oassdk.core.exceptions.GenerationException;
import egain.oassdk.testgenerators.TestGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SchemathesisTestGeneratorTest {

    @Test
    void generatesBundleUnderSchemathesisSubdir(@TempDir Path tempDir) throws GenerationException, IOException {
        Map<String, Object> spec = minimalSpec();
        TestGenerator gen = new SchemathesisTestGenerator();
        gen.generate(spec, tempDir.toString(), TestConfig.builder().build(), null);

        Path bundle = tempDir.resolve("schemathesis");
        assertTrue(Files.isDirectory(bundle));
        assertTrue(Files.isRegularFile(bundle.resolve("openapi.yaml")));
        assertTrue(Files.isRegularFile(bundle.resolve("schemathesis.properties")));
        assertTrue(Files.isRegularFile(bundle.resolve("run-schemathesis.sh")));
        assertTrue(Files.isRegularFile(bundle.resolve("README-schemathesis.md")));

        String props = Files.readString(bundle.resolve("schemathesis.properties"));
        assertTrue(props.contains("%HUB%"));
        assertTrue(props.contains("JUNIT_REPORT="));
        assertTrue(props.contains("%TOKEN%"));

        String script = Files.readString(bundle.resolve("run-schemathesis.sh"));
        assertTrue(script.contains("st run"));
        assertTrue(script.contains("--phases="));
    }

    @Test
    void respectsBundleDirDot(@TempDir Path tempDir) throws GenerationException, IOException {
        Map<String, Object> spec = minimalSpec();
        Map<String, Object> extra = new HashMap<>();
        extra.put("schemathesis.bundleDir", ".");
        TestConfig config = TestConfig.builder().additionalProperties(extra).build();
        new SchemathesisTestGenerator().generate(spec, tempDir.toString(), config, null);

        assertTrue(Files.isRegularFile(tempDir.resolve("openapi.yaml")));
        assertEquals(tempDir, SchemathesisTestGenerator.resolveBundleDirectory(tempDir.toString(), config));
    }

    private static Map<String, Object> minimalSpec() {
        Map<String, Object> spec = new HashMap<>();
        spec.put("openapi", "3.0.0");
        spec.put("info", Map.of("title", "T", "version", "1.0.0"));
        spec.put("paths", Map.of());
        return spec;
    }
}

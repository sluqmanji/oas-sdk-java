package egain.oassdk.testgenerators.nodejs;

import egain.oassdk.config.TestConfig;
import egain.oassdk.core.exceptions.GenerationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for JestUnitTestGenerator
 */
public class JestUnitTestGeneratorTest {

    private JestUnitTestGenerator generator;
    private Map<String, Object> spec;
    private TestConfig testConfig;

    @BeforeEach
    public void setUp() {
        generator = new JestUnitTestGenerator();
        spec = createValidOpenAPISpec();
        testConfig = new TestConfig();
    }

    @Test
    public void testGeneratorInitialization() {
        assertNotNull(generator);
    }

    @Test
    public void testGetName() {
        assertEquals("Jest Unit Test Generator", generator.getName());
    }

    @Test
    public void testGetVersion() {
        assertEquals("1.0.0", generator.getVersion());
    }

    @Test
    public void testGetTestType() {
        assertEquals("unit", generator.getTestType());
    }

    @Test
    public void testGenerate_Success(@TempDir Path tempDir) throws GenerationException {
        testConfig.setTestFramework("jest");

        generator.generate(spec, tempDir.toString(), testConfig, "jest");

        Path unitDir = tempDir.resolve("unit");
        assertTrue(Files.exists(unitDir));
    }

    @Test
    public void testGenerate_WithEmptySpec(@TempDir Path tempDir) throws GenerationException {
        Map<String, Object> emptySpec = new HashMap<>();
        emptySpec.put("openapi", "3.0.0");
        emptySpec.put("info", Map.of("title", "Test", "version", "1.0.0"));
        emptySpec.put("paths", Map.of());

        generator.generate(emptySpec, tempDir.toString(), testConfig, "jest");

        assertTrue(Files.exists(tempDir.resolve("unit")));
    }

    @Test
    public void testGenerate_WithPaths(@TempDir Path tempDir) throws GenerationException {
        Map<String, Object> specWithPaths = new HashMap<>(spec);
        Map<String, Object> paths = new HashMap<>();
        Map<String, Object> pathItem = new HashMap<>();
        Map<String, Object> get = new HashMap<>();
        get.put("operationId", "getUsers");
        get.put("tags", java.util.List.of("users"));
        get.put("responses", Map.of("200", Map.of("description", "OK")));
        pathItem.put("get", get);
        paths.put("/users", pathItem);
        specWithPaths.put("paths", paths);

        generator.generate(specWithPaths, tempDir.toString(), testConfig, "jest");

        assertTrue(Files.exists(tempDir.resolve("unit")));
    }

    @Test
    public void testGenerate_CreatesJestConfigAndPackageJson(@TempDir Path tempDir) throws GenerationException {
        generator.generate(spec, tempDir.toString(), testConfig, "jest");

        Path unitDir = tempDir.resolve("unit");
        assertTrue(Files.exists(unitDir.resolve("jest.config.js")));
        assertTrue(Files.exists(unitDir.resolve("package.json")));
    }

    @Test
    public void testSetConfig() {
        TestConfig config = new TestConfig();
        config.setTestFramework("jest");

        generator.setConfig(config);

        assertEquals(config, generator.getConfig());
    }

    private Map<String, Object> createValidOpenAPISpec() {
        Map<String, Object> spec = new HashMap<>();
        spec.put("openapi", "3.0.0");
        spec.put("info", Map.of("title", "Test API", "version", "1.0.0", "description", "Test API Description"));

        Map<String, Object> paths = new HashMap<>();
        Map<String, Object> pathItem = new HashMap<>();
        Map<String, Object> get = new HashMap<>();
        get.put("operationId", "getTest");
        get.put("summary", "Get test data");
        get.put("tags", java.util.List.of("test"));
        get.put("responses", Map.of("200", Map.of("description", "OK")));
        pathItem.put("get", get);
        paths.put("/test", pathItem);
        spec.put("paths", paths);

        return spec;
    }
}

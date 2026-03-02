package egain.oassdk.test.schemathesis;

import egain.oassdk.core.exceptions.GenerationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for SchemathesisTestRunner
 */
public class SchemathesisTestRunnerTest {

    private SchemathesisTestRunner runner;
    private Map<String, Object> spec;

    @BeforeEach
    public void setUp() {
        runner = new SchemathesisTestRunner();
        spec = new HashMap<>();
        spec.put("openapi", "3.0.0");
        spec.put("info", Map.of("title", "Test API", "version", "1.0.0"));
        spec.put("paths", Map.of());
    }

    @Test
    public void testExecuteTests_CreatesOutputDirectory(@TempDir Path tempDir) throws GenerationException {
        String outputDir = tempDir.toString();
        String baseUrl = "http://localhost:8080";

        runner.executeTests(spec, outputDir, baseUrl);

        assertTrue(Files.exists(tempDir));
    }

    @Test
    public void testExecuteTests_GeneratesSchemathesisConfig(@TempDir Path tempDir) throws GenerationException, IOException {
        String outputDir = tempDir.toString();
        String baseUrl = "https://api.example.com";

        runner.executeTests(spec, outputDir, baseUrl);

        Path configPath = tempDir.resolve("schemathesis.yaml");
        assertTrue(Files.exists(configPath));
        String content = new String(Files.readAllBytes(configPath));
        assertTrue(content.contains("base_url: " + baseUrl));
        assertTrue(content.contains("schema: openapi.yaml"));
    }

    @Test
    public void testExecuteTests_GeneratesTestExecutionScript(@TempDir Path tempDir) throws GenerationException, IOException {
        String outputDir = tempDir.toString();
        String baseUrl = "http://localhost:3000";

        runner.executeTests(spec, outputDir, baseUrl);

        Path scriptPath = tempDir.resolve("run-schemathesis-tests.sh");
        assertTrue(Files.exists(scriptPath));
        String content = new String(Files.readAllBytes(scriptPath));
        assertTrue(content.contains("Base URL: " + baseUrl));
        assertTrue(content.contains("schemathesis run"));
    }

    @Test
    public void testExecuteTests_GeneratesDockerConfig(@TempDir Path tempDir) throws GenerationException, IOException {
        String outputDir = tempDir.toString();
        String baseUrl = "http://api:8080";

        runner.executeTests(spec, outputDir, baseUrl);

        Path dockerfilePath = tempDir.resolve("Dockerfile");
        assertTrue(Files.exists(dockerfilePath));
        String dockerfileContent = new String(Files.readAllBytes(dockerfilePath));
        assertTrue(dockerfileContent.contains("ENV BASE_URL=" + baseUrl));

        Path dockerComposePath = tempDir.resolve("docker-compose.yml");
        assertTrue(Files.exists(dockerComposePath));
        String composeContent = new String(Files.readAllBytes(dockerComposePath));
        assertTrue(composeContent.contains("BASE_URL=" + baseUrl));
    }

    @Test
    public void testExecuteTests_GeneratesCICDIntegration(@TempDir Path tempDir) throws GenerationException, IOException {
        String outputDir = tempDir.toString();
        String baseUrl = "https://staging.example.com";

        runner.executeTests(spec, outputDir, baseUrl);

        Path githubActionsPath = tempDir.resolve("github-actions.yml");
        assertTrue(Files.exists(githubActionsPath));
        String githubContent = new String(Files.readAllBytes(githubActionsPath));
        assertTrue(githubContent.contains("Schemathesis API Testing"));
        assertTrue(githubContent.contains(baseUrl));

        Path jenkinsPath = tempDir.resolve("Jenkinsfile");
        assertTrue(Files.exists(jenkinsPath));
        String jenkinsContent = new String(Files.readAllBytes(jenkinsPath));
        assertTrue(jenkinsContent.contains("Run Schemathesis Tests"));
    }

    @Test
    public void testExecuteTests_WithNullOutputDir() {
        // NPE from Paths.get(null) is caught and wrapped in GenerationException
        assertThrows(GenerationException.class, () ->
                runner.executeTests(spec, null, "http://localhost:8080"));
    }

    @Test
    public void testExecuteTests_ThrowsGenerationExceptionOnFailure(@TempDir Path tempDir) throws Exception {
        // Pass a path where parent is a file (not a directory), so createDirectories fails with IOException
        Path filePath = tempDir.resolve("file.txt");
        Files.writeString(filePath, "not a directory");
        String invalidOutputDir = filePath.resolve("subdir").toString();

        assertThrows(GenerationException.class, () ->
                runner.executeTests(spec, invalidOutputDir, "http://localhost:8080"));
    }
}

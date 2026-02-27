package egain.oassdk.docs;

import egain.oassdk.core.exceptions.GenerationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RedoclyConfigGenerator.
 */
public class RedoclyConfigGeneratorTest {

    @Test
    public void testGenerateRedoclyConfigCreatesFiles(@TempDir Path tempDir) throws GenerationException {
        RedoclyConfigGenerator generator = new RedoclyConfigGenerator();
        Map<String, Object> spec = Map.of(
                "info", Map.of("title", "Test API", "version", "1.0.0")
        );
        RedoclyConfigGenerator.RedoclyThemeConfig theme = new RedoclyConfigGenerator.RedoclyThemeConfig();
        RedoclyConfigGenerator.RedoclyApiConfig apiConfig = new RedoclyConfigGenerator.RedoclyApiConfig();

        generator.generateRedoclyConfig(spec, tempDir.toString(), theme, apiConfig);

        assertTrue(Files.exists(tempDir.resolve("redocly.yaml")));
        assertTrue(Files.exists(tempDir.resolve("redocly.json")));
        assertTrue(Files.exists(tempDir.resolve("custom.css")));
        assertTrue(Files.exists(tempDir.resolve("package.json")));
        assertTrue(Files.exists(tempDir.resolve("theme").resolve("custom.css")));
    }

    @Test
    public void testGenerateRedoclyConfigNullOutputDirThrows() {
        RedoclyConfigGenerator generator = new RedoclyConfigGenerator();
        Map<String, Object> spec = Map.of("info", Map.of("title", "API", "version", "1.0"));
        RedoclyConfigGenerator.RedoclyThemeConfig theme = new RedoclyConfigGenerator.RedoclyThemeConfig();
        RedoclyConfigGenerator.RedoclyApiConfig apiConfig = new RedoclyConfigGenerator.RedoclyApiConfig();

        assertThrows(IllegalArgumentException.class, () ->
                generator.generateRedoclyConfig(spec, null, theme, apiConfig)
        );
    }

    @Test
    public void testRedoclyYamlContainsApiTitle(@TempDir Path tempDir) throws Exception {
        RedoclyConfigGenerator generator = new RedoclyConfigGenerator();
        Map<String, Object> spec = Map.of(
                "info", Map.of("title", "My Custom API", "version", "2.0.0")
        );
        RedoclyConfigGenerator.RedoclyThemeConfig theme = new RedoclyConfigGenerator.RedoclyThemeConfig();
        RedoclyConfigGenerator.RedoclyApiConfig apiConfig = new RedoclyConfigGenerator.RedoclyApiConfig();

        generator.generateRedoclyConfig(spec, tempDir.toString(), theme, apiConfig);

        String yaml = Files.readString(tempDir.resolve("redocly.yaml"));
        assertTrue(yaml.contains("My Custom API"));
        assertTrue(yaml.contains("2.0.0"));
    }
}

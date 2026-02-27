package egain.oassdk.dev.docs;

import egain.oassdk.core.exceptions.GenerationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RedoclyGenerator (dev docs).
 */
public class RedoclyGeneratorTest {

    @Test
    public void testGenerateDocumentationNullOutputDirThrows() {
        RedoclyGenerator generator = new RedoclyGenerator();
        assertThrows(IllegalArgumentException.class, () ->
                generator.generateDocumentation(Map.of(), null)
        );
    }

    @Test
    public void testGenerateDocumentationCreatesFiles(@TempDir Path tempDir) throws GenerationException {
        RedoclyGenerator generator = new RedoclyGenerator();
        Map<String, Object> spec = Map.of("info", Map.of("title", "Test API", "version", "1.0.0"));
        generator.generateDocumentation(spec, tempDir.toString());
        assertTrue(Files.exists(tempDir.resolve("index.html")));
        assertTrue(Files.exists(tempDir.resolve("package.json")));
    }
}

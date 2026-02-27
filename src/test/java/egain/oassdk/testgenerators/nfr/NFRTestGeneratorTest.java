package egain.oassdk.testgenerators.nfr;

import egain.oassdk.config.TestConfig;
import egain.oassdk.core.exceptions.GenerationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for NFRTestGenerator.
 */
public class NFRTestGeneratorTest {

    @Test
    public void testGetName() {
        NFRTestGenerator generator = new NFRTestGenerator();
        assertNotNull(generator.getName());
        assertFalse(generator.getName().isEmpty());
    }

    @Test
    public void testGenerateCreatesOutput(@TempDir Path tempDir) throws GenerationException {
        NFRTestGenerator generator = new NFRTestGenerator();
        Map<String, Object> spec = Map.of(
                "info", Map.of("title", "API", "version", "1.0"),
                "paths", Map.of()
        );
        generator.generate(spec, tempDir.toString(), new TestConfig(), "pytest");
        assertTrue(Files.exists(tempDir));
    }
}

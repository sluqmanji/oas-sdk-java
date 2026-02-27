package egain.oassdk.dev.validators;

import egain.oassdk.core.exceptions.GenerationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for APIValidator (dev validators generator).
 */
public class APIValidatorTest {

    @Test
    public void testGenerateValidatorsNullOutputDirThrows() {
        APIValidator generator = new APIValidator();
        assertThrows(IllegalArgumentException.class, () ->
                generator.generateValidators(Map.of(), null)
        );
    }

    @Test
    public void testGenerateValidatorsCreatesFiles(@TempDir Path tempDir) throws GenerationException {
        APIValidator generator = new APIValidator();
        generator.generateValidators(Map.of(), tempDir.toString());
        assertTrue(Files.exists(tempDir.resolve("RequestValidator.java")));
        assertTrue(Files.exists(tempDir.resolve("ResponseValidator.java")));
        assertTrue(Files.exists(tempDir.resolve("SchemaValidator.java")));
        assertTrue(Files.exists(tempDir.resolve("ValidationConfig.java")));
    }
}

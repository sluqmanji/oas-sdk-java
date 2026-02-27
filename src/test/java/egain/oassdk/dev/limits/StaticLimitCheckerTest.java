package egain.oassdk.dev.limits;

import egain.oassdk.core.exceptions.GenerationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for StaticLimitChecker (dev limits generator).
 */
public class StaticLimitCheckerTest {

    @Test
    public void testGenerateStaticLimitCheckersNullOutputDirThrows() {
        StaticLimitChecker generator = new StaticLimitChecker();
        assertThrows(IllegalArgumentException.class, () ->
                generator.generateStaticLimitCheckers(Map.of(), null)
        );
    }

    @Test
    public void testGenerateStaticLimitCheckersCreatesFiles(@TempDir Path tempDir) throws GenerationException {
        StaticLimitChecker generator = new StaticLimitChecker();
        generator.generateStaticLimitCheckers(Map.of(), tempDir.toString());
        assertTrue(Files.exists(tempDir.resolve("RequestSizeLimitChecker.java")));
        assertTrue(Files.exists(tempDir.resolve("ResponseSizeLimitChecker.java")));
        assertTrue(Files.exists(tempDir.resolve("StaticLimitConfig.java")));
    }
}

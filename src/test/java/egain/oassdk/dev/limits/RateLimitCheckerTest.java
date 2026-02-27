package egain.oassdk.dev.limits;

import egain.oassdk.core.exceptions.GenerationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RateLimitChecker (dev).
 */
public class RateLimitCheckerTest {

    @Test
    public void testGenerateRateLimitCheckersNullOutputDirThrows() {
        RateLimitChecker generator = new RateLimitChecker();
        assertThrows(IllegalArgumentException.class, () ->
                generator.generateRateLimitCheckers(Map.of(), null)
        );
    }

    @Test
    public void testGenerateRateLimitCheckersCreatesFiles(@TempDir Path tempDir) throws GenerationException {
        RateLimitChecker generator = new RateLimitChecker();
        generator.generateRateLimitCheckers(Map.of(), tempDir.toString());
        assertTrue(Files.exists(tempDir.resolve("RateLimiter.java")));
        assertTrue(Files.exists(tempDir.resolve("RateLimitInterceptor.java")));
        assertTrue(Files.exists(tempDir.resolve("RateLimitConfig.java")));
        assertTrue(Files.exists(tempDir.resolve("RateLimitService.java")));
    }
}

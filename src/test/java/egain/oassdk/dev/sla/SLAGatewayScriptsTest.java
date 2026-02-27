package egain.oassdk.dev.sla;

import egain.oassdk.core.exceptions.GenerationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SLAGatewayScripts.
 */
public class SLAGatewayScriptsTest {

    @Test
    public void testGenerateScriptsNullOutputDirThrows() {
        SLAGatewayScripts generator = new SLAGatewayScripts();
        assertThrows(IllegalArgumentException.class, () ->
                generator.generateScripts(Map.of(), Map.of(), null)
        );
    }

    @Test
    public void testGenerateScriptsCreatesDirsAndFiles(@TempDir Path tempDir) throws GenerationException {
        SLAGatewayScripts generator = new SLAGatewayScripts();
        generator.generateScripts(Map.of(), Map.of(), tempDir.toString());
        Path awsDir = tempDir.resolve("aws");
        assertTrue(Files.exists(awsDir));
        assertTrue(Files.exists(awsDir.resolve("api-gateway-policy.json")));
        assertTrue(Files.exists(tempDir.resolve("kong")));
        assertTrue(Files.exists(tempDir.resolve("nginx")));
    }
}

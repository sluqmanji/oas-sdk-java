package egain.oassdk.generators.java;

import egain.oassdk.config.GeneratorConfig;
import egain.oassdk.config.ObservabilityConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Unit tests for JerseyGenerationContext.
 */
class JerseyGenerationContextTest {

    // -----------------------------------------------------------------------
    //  Constructor / field access
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Constructor stores all parameters correctly")
    void constructor_storesParams() {
        Map<String, Object> spec = Map.of("openapi", "3.0.0");
        GeneratorConfig cfg = new GeneratorConfig();
        JerseyGenerationContext ctx = new JerseyGenerationContext(spec, "/out", cfg, "com.acme");

        assertSame(spec, ctx.spec);
        assertEquals("/out", ctx.outputDir);
        assertSame(cfg, ctx.config);
        assertEquals("com.acme", ctx.packageName);
    }

    // -----------------------------------------------------------------------
    //  getPackageOrDefault
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("getPackageOrDefault returns configured package when non-null")
    void getPackageOrDefault_configured() {
        JerseyGenerationContext ctx = new JerseyGenerationContext(Map.of(), "/out", null, "com.acme.api");
        assertEquals("com.acme.api", ctx.getPackageOrDefault());
    }

    @Test
    @DisplayName("getPackageOrDefault returns default when package is null")
    void getPackageOrDefault_null() {
        JerseyGenerationContext ctx = new JerseyGenerationContext(Map.of(), "/out", null, null);
        assertEquals("com.example.api", ctx.getPackageOrDefault());
    }

    // -----------------------------------------------------------------------
    //  getPackagePath
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("getPackagePath converts dots to slashes")
    void getPackagePath_conversion() {
        JerseyGenerationContext ctx = new JerseyGenerationContext(Map.of(), "/out", null, "com.acme.api");
        assertEquals("com/acme/api", ctx.getPackagePath());
    }

    // -----------------------------------------------------------------------
    //  isObservabilityEnabled
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("isObservabilityEnabled returns false when config is null")
    void isObservabilityEnabled_nullConfig() {
        JerseyGenerationContext ctx = new JerseyGenerationContext(Map.of(), "/out", null, "com.acme");
        assertFalse(ctx.isObservabilityEnabled());
    }

    @Test
    @DisplayName("isObservabilityEnabled returns false when observability config is explicitly null")
    void isObservabilityEnabled_noObsConfig() {
        GeneratorConfig cfg = new GeneratorConfig();
        cfg.setObservabilityConfig(null);
        JerseyGenerationContext ctx = new JerseyGenerationContext(Map.of(), "/out", cfg, "com.acme");
        assertFalse(ctx.isObservabilityEnabled());
    }

    @Test
    @DisplayName("isObservabilityEnabled returns true when enabled")
    void isObservabilityEnabled_enabled() {
        GeneratorConfig cfg = new GeneratorConfig();
        ObservabilityConfig obs = new ObservabilityConfig();
        obs.setEnabled(true);
        cfg.setObservabilityConfig(obs);

        JerseyGenerationContext ctx = new JerseyGenerationContext(Map.of(), "/out", cfg, "com.acme");
        assertTrue(ctx.isObservabilityEnabled());
    }

    // -----------------------------------------------------------------------
    //  getAPITitle
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("getAPITitle extracts title from spec info")
    void getAPITitle_fromSpec() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("title", "My Cool API");
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("info", info);

        assertEquals("My Cool API", JerseyGenerationContext.getAPITitle(spec));
    }

    @Test
    @DisplayName("getAPITitle returns API when spec is null")
    void getAPITitle_nullSpec() {
        assertEquals("API", JerseyGenerationContext.getAPITitle(null));
    }

    @Test
    @DisplayName("getAPITitle returns API when info is missing")
    void getAPITitle_noInfo() {
        assertEquals("API", JerseyGenerationContext.getAPITitle(Map.of()));
    }

    // -----------------------------------------------------------------------
    //  getAPIDescription
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("getAPIDescription extracts description from spec info")
    void getAPIDescription_fromSpec() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("description", "A cool description");
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("info", info);

        assertEquals("A cool description", JerseyGenerationContext.getAPIDescription(spec));
    }

    // -----------------------------------------------------------------------
    //  getAPIVersion
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("getAPIVersion extracts version from spec info")
    void getAPIVersion_fromSpec() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("version", "2.0.1");
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("info", info);

        assertEquals("2.0.1", JerseyGenerationContext.getAPIVersion(spec));
    }

    @Test
    @DisplayName("getAPIVersion returns 1.0.0 when info missing")
    void getAPIVersion_default() {
        assertEquals("1.0.0", JerseyGenerationContext.getAPIVersion(Map.of()));
    }

    // -----------------------------------------------------------------------
    //  writeFile
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("writeFile writes content to specified path, creating parent dirs")
    void writeFile_createsAndWrites(@TempDir Path tempDir) throws IOException {
        String filePath = tempDir.resolve("sub/dir/test.txt").toString();
        JerseyGenerationContext.writeFile(filePath, "hello world");

        String content = Files.readString(Path.of(filePath));
        assertEquals("hello world", content);
    }

    @Test
    @DisplayName("writeFile overwrites existing file")
    void writeFile_overwrites(@TempDir Path tempDir) throws IOException {
        String filePath = tempDir.resolve("test.txt").toString();
        JerseyGenerationContext.writeFile(filePath, "first");
        JerseyGenerationContext.writeFile(filePath, "second");

        assertEquals("second", Files.readString(Path.of(filePath)));
    }

    // -----------------------------------------------------------------------
    //  extractServerBasePath
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("extractServerBasePath extracts path from server URL")
    void extractServerBasePath_withPath() {
        Map<String, Object> server = new LinkedHashMap<>();
        server.put("url", "https://api.example.com/knowledge/contentmgr/v4");
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("servers", java.util.List.of(server));

        assertEquals("/knowledge/contentmgr/v4", JerseyGenerationContext.extractServerBasePath(spec));
    }

    @Test
    @DisplayName("extractServerBasePath returns null when no servers")
    void extractServerBasePath_noServers() {
        assertNull(JerseyGenerationContext.extractServerBasePath(Map.of()));
    }

    @Test
    @DisplayName("extractServerBasePath returns null for root-only URL")
    void extractServerBasePath_rootOnly() {
        Map<String, Object> server = new LinkedHashMap<>();
        server.put("url", "http://localhost:8080");
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("servers", java.util.List.of(server));

        assertNull(JerseyGenerationContext.extractServerBasePath(spec));
    }
}

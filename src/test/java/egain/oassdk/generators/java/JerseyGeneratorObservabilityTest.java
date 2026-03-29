package egain.oassdk.generators.java;

import egain.oassdk.config.GeneratorConfig;
import egain.oassdk.config.ObservabilityConfig;
import egain.oassdk.core.parser.OASParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that JerseyGenerator generates (or skips) observability artifacts
 * based on the ObservabilityConfig in GeneratorConfig.
 */
@DisplayName("JerseyGenerator Observability Generation Tests")
public class JerseyGeneratorObservabilityTest {

    private static final String TEST_YAML = "src/test/resources/openapi3.yaml";
    private static final String PACKAGE_NAME = "com.test.api";
    private static final String PACKAGE_PATH = "com/test/api";

    @TempDir
    Path tempDir;

    private JerseyGenerator generator;
    private Map<String, Object> resolvedSpec;

    @BeforeEach
    void setUp() throws Exception {
        generator = new JerseyGenerator();
        OASParser parser = new OASParser();
        Map<String, Object> spec = parser.parse(TEST_YAML);
        resolvedSpec = parser.resolveReferences(spec, TEST_YAML);
    }

    @Test
    @DisplayName("Observability enabled generates all observability files")
    public void testObservabilityEnabledGeneratesFiles() throws Exception {
        Path outputDir = tempDir.resolve("obs-enabled");
        GeneratorConfig config = GeneratorConfig.builder()
                .observabilityEnabled(true)
                .build();

        generator.generate(resolvedSpec, outputDir.toString(), config, PACKAGE_NAME);

        Path obsDir = outputDir.resolve("src/main/java/" + PACKAGE_PATH + "/observability");
        assertTrue(Files.exists(obsDir.resolve("MetricsFilter.java")),
                "MetricsFilter.java should be generated when observability is enabled");
        assertTrue(Files.exists(obsDir.resolve("TracingFilter.java")),
                "TracingFilter.java should be generated when observability is enabled");
        assertTrue(Files.exists(obsDir.resolve("MetricsEndpoint.java")),
                "MetricsEndpoint.java should be generated when observability is enabled");
        assertTrue(Files.exists(obsDir.resolve("ObservabilityBootstrap.java")),
                "ObservabilityBootstrap.java should be generated when observability is enabled");
    }

    @Test
    @DisplayName("Generated MetricsFilter contains expected instrumentation code")
    public void testMetricsFilterContent() throws Exception {
        Path outputDir = tempDir.resolve("obs-metrics-content");
        GeneratorConfig config = GeneratorConfig.builder()
                .observabilityEnabled(true)
                .build();

        generator.generate(resolvedSpec, outputDir.toString(), config, PACKAGE_NAME);

        Path metricsFilter = outputDir.resolve("src/main/java/" + PACKAGE_PATH + "/observability/MetricsFilter.java");
        assertTrue(Files.exists(metricsFilter), "MetricsFilter.java should exist");

        String content = Files.readString(metricsFilter);
        assertTrue(content.contains("PrometheusMeterRegistry"),
                "MetricsFilter should reference PrometheusMeterRegistry");
        assertTrue(content.contains("http.server.requests"),
                "MetricsFilter should instrument http.server.requests");
    }

    @Test
    @DisplayName("Generated TracingFilter contains expected tracing code")
    public void testTracingFilterContent() throws Exception {
        Path outputDir = tempDir.resolve("obs-tracing-content");
        GeneratorConfig config = GeneratorConfig.builder()
                .observabilityEnabled(true)
                .build();

        generator.generate(resolvedSpec, outputDir.toString(), config, PACKAGE_NAME);

        Path tracingFilter = outputDir.resolve("src/main/java/" + PACKAGE_PATH + "/observability/TracingFilter.java");
        assertTrue(Files.exists(tracingFilter), "TracingFilter.java should exist");

        String content = Files.readString(tracingFilter);
        assertTrue(content.contains("Tracer"),
                "TracingFilter should reference Tracer");
        assertTrue(content.contains("SpanKind.SERVER"),
                "TracingFilter should use SpanKind.SERVER");
    }

    @Test
    @DisplayName("Generated pom.xml contains observability dependencies")
    public void testPomXmlContainsObservabilityDependencies() throws Exception {
        Path outputDir = tempDir.resolve("obs-pom");
        GeneratorConfig config = GeneratorConfig.builder()
                .observabilityEnabled(true)
                .build();

        generator.generate(resolvedSpec, outputDir.toString(), config, PACKAGE_NAME);

        Path pomFile = outputDir.resolve("pom.xml");
        assertTrue(Files.exists(pomFile), "pom.xml should be generated");

        String pomContent = Files.readString(pomFile);
        assertTrue(pomContent.contains("micrometer-registry-prometheus"),
                "pom.xml should include micrometer-registry-prometheus dependency");
        assertTrue(pomContent.contains("opentelemetry-api"),
                "pom.xml should include opentelemetry-api dependency");
    }

    @Test
    @DisplayName("Observability disabled does NOT generate observability files")
    public void testObservabilityDisabledSkipsFiles() throws Exception {
        Path outputDir = tempDir.resolve("obs-disabled");
        GeneratorConfig config = GeneratorConfig.builder()
                .observabilityEnabled(false)
                .build();

        generator.generate(resolvedSpec, outputDir.toString(), config, PACKAGE_NAME);

        Path obsDir = outputDir.resolve("src/main/java/" + PACKAGE_PATH + "/observability");
        assertFalse(Files.exists(obsDir.resolve("MetricsFilter.java")),
                "MetricsFilter.java should NOT be generated when observability is disabled");
        assertFalse(Files.exists(obsDir.resolve("TracingFilter.java")),
                "TracingFilter.java should NOT be generated when observability is disabled");
        assertFalse(Files.exists(obsDir.resolve("MetricsEndpoint.java")),
                "MetricsEndpoint.java should NOT be generated when observability is disabled");
        assertFalse(Files.exists(obsDir.resolve("ObservabilityBootstrap.java")),
                "ObservabilityBootstrap.java should NOT be generated when observability is disabled");
    }

    @Test
    @DisplayName("Null config does not generate observability files")
    public void testNullConfigSkipsObservability() throws Exception {
        Path outputDir = tempDir.resolve("obs-null-config");

        generator.generate(resolvedSpec, outputDir.toString(), null, PACKAGE_NAME);

        Path obsDir = outputDir.resolve("src/main/java/" + PACKAGE_PATH + "/observability");
        assertFalse(Files.exists(obsDir.resolve("MetricsFilter.java")),
                "MetricsFilter.java should NOT be generated when config is null");
    }

    @Test
    @DisplayName("Default GeneratorConfig (observability enabled) generates observability files")
    public void testDefaultConfigGeneratesObservability() throws Exception {
        Path outputDir = tempDir.resolve("obs-default-config");
        GeneratorConfig config = new GeneratorConfig();

        generator.generate(resolvedSpec, outputDir.toString(), config, PACKAGE_NAME);

        Path obsDir = outputDir.resolve("src/main/java/" + PACKAGE_PATH + "/observability");
        assertTrue(Files.exists(obsDir.resolve("MetricsFilter.java")),
                "MetricsFilter.java should be generated with default GeneratorConfig");
        assertTrue(Files.exists(obsDir.resolve("TracingFilter.java")),
                "TracingFilter.java should be generated with default GeneratorConfig");
    }

    @Test
    @DisplayName("Observability disabled pom.xml omits observability dependencies")
    public void testDisabledPomOmitsObservabilityDeps() throws Exception {
        Path outputDir = tempDir.resolve("obs-disabled-pom");
        GeneratorConfig config = GeneratorConfig.builder()
                .observabilityEnabled(false)
                .build();

        generator.generate(resolvedSpec, outputDir.toString(), config, PACKAGE_NAME);

        Path pomFile = outputDir.resolve("pom.xml");
        assertTrue(Files.exists(pomFile), "pom.xml should still be generated");

        String pomContent = Files.readString(pomFile);
        assertFalse(pomContent.contains("micrometer-registry-prometheus"),
                "pom.xml should NOT include micrometer-registry-prometheus when observability is disabled");
        assertFalse(pomContent.contains("opentelemetry-api"),
                "pom.xml should NOT include opentelemetry-api when observability is disabled");
    }
}

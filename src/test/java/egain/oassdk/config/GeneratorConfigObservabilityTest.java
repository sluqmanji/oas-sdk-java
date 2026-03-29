package egain.oassdk.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the observability extension on GeneratorConfig.
 */
@DisplayName("GeneratorConfig Observability Extension Tests")
public class GeneratorConfigObservabilityTest {

    @Test
    @DisplayName("Default GeneratorConfig has observability enabled")
    public void testDefaultConfigHasObservabilityEnabled() {
        GeneratorConfig config = new GeneratorConfig();

        assertNotNull(config.getObservabilityConfig(),
                "Default GeneratorConfig should have a non-null ObservabilityConfig");
        assertTrue(config.getObservabilityConfig().isEnabled(),
                "Observability should be enabled by default");
    }

    @Test
    @DisplayName("Builder can set a custom ObservabilityConfig")
    public void testBuilderCanSetObservabilityConfig() {
        ObservabilityConfig obsConfig = ObservabilityConfig.builder()
                .enabled(true)
                .enableMetrics(false)
                .tracingExporter("jaeger")
                .serviceName("custom-svc")
                .build();

        GeneratorConfig config = GeneratorConfig.builder()
                .observabilityConfig(obsConfig)
                .build();

        assertNotNull(config.getObservabilityConfig());
        assertTrue(config.getObservabilityConfig().isEnabled());
        assertFalse(config.getObservabilityConfig().isEnableMetrics());
        assertEquals("jaeger", config.getObservabilityConfig().getTracingExporter());
        assertEquals("custom-svc", config.getObservabilityConfig().getServiceName());
    }

    @Test
    @DisplayName("Builder shorthand observabilityEnabled(false) disables observability")
    public void testBuilderShorthandObservabilityDisabled() {
        GeneratorConfig config = GeneratorConfig.builder()
                .observabilityEnabled(false)
                .build();

        assertNotNull(config.getObservabilityConfig());
        assertFalse(config.getObservabilityConfig().isEnabled(),
                "observabilityEnabled(false) should disable observability");
    }

    @Test
    @DisplayName("Builder shorthand observabilityEnabled(true) keeps observability enabled")
    public void testBuilderShorthandObservabilityEnabled() {
        GeneratorConfig config = GeneratorConfig.builder()
                .observabilityEnabled(true)
                .build();

        assertNotNull(config.getObservabilityConfig());
        assertTrue(config.getObservabilityConfig().isEnabled());
    }

    @Test
    @DisplayName("getObservabilityConfig returns non-null by default")
    public void testGetObservabilityConfigNonNull() {
        GeneratorConfig config = new GeneratorConfig();
        assertNotNull(config.getObservabilityConfig());
    }

    @Test
    @DisplayName("Parameterized constructor also initializes ObservabilityConfig")
    public void testParameterizedConstructorHasObservability() {
        GeneratorConfig config = new GeneratorConfig(
                "java", "jersey", "com.test", "1.0.0",
                "./out", null, false, null);

        assertNotNull(config.getObservabilityConfig(),
                "Parameterized constructor should also initialize ObservabilityConfig");
        assertTrue(config.getObservabilityConfig().isEnabled());
    }

    @Test
    @DisplayName("Setter can replace ObservabilityConfig")
    public void testSetObservabilityConfig() {
        GeneratorConfig config = new GeneratorConfig();
        assertTrue(config.getObservabilityConfig().isEnabled());

        ObservabilityConfig disabled = ObservabilityConfig.builder()
                .enabled(false)
                .build();
        config.setObservabilityConfig(disabled);

        assertFalse(config.getObservabilityConfig().isEnabled());
    }

    @Test
    @DisplayName("toString includes observabilityConfig")
    public void testToStringIncludesObservability() {
        GeneratorConfig config = new GeneratorConfig();
        String str = config.toString();
        assertTrue(str.contains("observabilityConfig"),
                "toString should include observabilityConfig field");
    }
}

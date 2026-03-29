package egain.oassdk.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ObservabilityConfig default values, builder pattern, and copy semantics.
 */
@DisplayName("ObservabilityConfig Tests")
public class ObservabilityConfigTest {

    @Test
    @DisplayName("Default constructor sets expected defaults")
    public void testDefaultConstructorValues() {
        ObservabilityConfig config = new ObservabilityConfig();

        assertTrue(config.isEnabled(), "enabled should default to true");
        assertTrue(config.isEnableMetrics(), "enableMetrics should default to true");
        assertTrue(config.isEnableTracing(), "enableTracing should default to true");
        assertTrue(config.isEnableLogging(), "enableLogging should default to true");
        assertEquals("prometheus", config.getMetricsExporter(), "metricsExporter should default to prometheus");
        assertEquals("otlp", config.getTracingExporter(), "tracingExporter should default to otlp");
        assertEquals("http://localhost:4318", config.getOtlpEndpoint(), "otlpEndpoint should default to http://localhost:4318");
        assertNull(config.getServiceName(), "serviceName should default to null");
        assertNotNull(config.getResourceAttributes(), "resourceAttributes should not be null");
        assertTrue(config.getResourceAttributes().isEmpty(), "resourceAttributes should be empty by default");
    }

    @Test
    @DisplayName("Builder with defaults matches default constructor")
    public void testBuilderDefaults() {
        ObservabilityConfig config = ObservabilityConfig.builder().build();

        assertTrue(config.isEnabled());
        assertTrue(config.isEnableMetrics());
        assertTrue(config.isEnableTracing());
        assertTrue(config.isEnableLogging());
        assertEquals("prometheus", config.getMetricsExporter());
        assertEquals("otlp", config.getTracingExporter());
        assertEquals("http://localhost:4318", config.getOtlpEndpoint());
        assertNull(config.getServiceName());
        assertTrue(config.getResourceAttributes().isEmpty());
    }

    @Test
    @DisplayName("Builder with custom values")
    public void testBuilderWithCustomValues() {
        Map<String, String> attrs = new HashMap<>();
        attrs.put("env", "production");
        attrs.put("region", "us-east-1");

        ObservabilityConfig config = ObservabilityConfig.builder()
                .enabled(true)
                .enableMetrics(false)
                .enableTracing(true)
                .enableLogging(false)
                .metricsExporter("otlp")
                .tracingExporter("jaeger")
                .serviceName("my-service")
                .otlpEndpoint("http://collector:4318")
                .resourceAttributes(attrs)
                .build();

        assertTrue(config.isEnabled());
        assertFalse(config.isEnableMetrics());
        assertTrue(config.isEnableTracing());
        assertFalse(config.isEnableLogging());
        assertEquals("otlp", config.getMetricsExporter());
        assertEquals("jaeger", config.getTracingExporter());
        assertEquals("my-service", config.getServiceName());
        assertEquals("http://collector:4318", config.getOtlpEndpoint());
        assertEquals(2, config.getResourceAttributes().size());
        assertEquals("production", config.getResourceAttributes().get("env"));
    }

    @Test
    @DisplayName("Disabled config via builder")
    public void testDisabledConfig() {
        ObservabilityConfig config = ObservabilityConfig.builder()
                .enabled(false)
                .build();

        assertFalse(config.isEnabled());
        // Other defaults still hold
        assertTrue(config.isEnableMetrics());
        assertTrue(config.isEnableTracing());
    }

    @Test
    @DisplayName("toString does not throw")
    public void testToStringDoesNotThrow() {
        ObservabilityConfig config = new ObservabilityConfig();
        assertDoesNotThrow(() -> config.toString());

        String str = config.toString();
        assertNotNull(str);
        assertTrue(str.contains("ObservabilityConfig"));
        assertTrue(str.contains("enabled=true"));
        assertTrue(str.contains("metricsExporter='prometheus'"));
    }

    @Test
    @DisplayName("resourceAttributes copy semantics - getter returns defensive copy")
    public void testResourceAttributesCopySemantics() {
        Map<String, String> original = new HashMap<>();
        original.put("key", "value");

        ObservabilityConfig config = ObservabilityConfig.builder()
                .resourceAttributes(original)
                .build();

        // Mutating the original map should not affect config
        original.put("extra", "should-not-appear");
        assertFalse(config.getResourceAttributes().containsKey("extra"),
                "Builder should copy the map, not hold a reference");

        // Mutating the returned map should not affect config
        Map<String, String> returned = config.getResourceAttributes();
        returned.put("mutated", "yes");
        assertFalse(config.getResourceAttributes().containsKey("mutated"),
                "getResourceAttributes should return a defensive copy");
    }

    @Test
    @DisplayName("resourceAttributes setter with null produces empty map")
    public void testResourceAttributesNullSafety() {
        ObservabilityConfig config = new ObservabilityConfig();
        config.setResourceAttributes(null);

        assertNotNull(config.getResourceAttributes());
        assertTrue(config.getResourceAttributes().isEmpty());
    }

    @Test
    @DisplayName("Setters update values correctly")
    public void testSetters() {
        ObservabilityConfig config = new ObservabilityConfig();

        config.setEnabled(false);
        assertFalse(config.isEnabled());

        config.setEnableMetrics(false);
        assertFalse(config.isEnableMetrics());

        config.setEnableTracing(false);
        assertFalse(config.isEnableTracing());

        config.setEnableLogging(false);
        assertFalse(config.isEnableLogging());

        config.setMetricsExporter("otlp");
        assertEquals("otlp", config.getMetricsExporter());

        config.setTracingExporter("zipkin");
        assertEquals("zipkin", config.getTracingExporter());

        config.setServiceName("test-svc");
        assertEquals("test-svc", config.getServiceName());

        config.setOtlpEndpoint("http://remote:4318");
        assertEquals("http://remote:4318", config.getOtlpEndpoint());
    }

    @Test
    @DisplayName("Parameterized constructor sets all fields")
    public void testParameterizedConstructor() {
        Map<String, String> attrs = new HashMap<>();
        attrs.put("k", "v");

        ObservabilityConfig config = new ObservabilityConfig(
                false, true, false, true,
                "otlp", "jaeger", "svc", "http://host:4318", attrs);

        assertFalse(config.isEnabled());
        assertTrue(config.isEnableMetrics());
        assertFalse(config.isEnableTracing());
        assertTrue(config.isEnableLogging());
        assertEquals("otlp", config.getMetricsExporter());
        assertEquals("jaeger", config.getTracingExporter());
        assertEquals("svc", config.getServiceName());
        assertEquals("http://host:4318", config.getOtlpEndpoint());
        assertEquals("v", config.getResourceAttributes().get("k"));
    }
}

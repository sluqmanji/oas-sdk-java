package egain.oassdk.config;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for observability generation (OpenTelemetry + Micrometer).
 * When enabled, generated applications include built-in metrics collection,
 * distributed tracing, and a Prometheus-compatible /metrics endpoint.
 */
public class ObservabilityConfig {

    private boolean enabled;
    private boolean enableMetrics;
    private boolean enableTracing;
    private boolean enableLogging;
    private String metricsExporter;       // "prometheus" | "otlp"
    private String tracingExporter;       // "otlp" | "jaeger" | "zipkin"
    private String serviceName;           // defaults to OAS info.title
    private String otlpEndpoint;
    private Map<String, String> resourceAttributes;

    /**
     * Default constructor — observability enabled with Prometheus metrics + OTLP tracing
     */
    public ObservabilityConfig() {
        this.enabled = true;
        this.enableMetrics = true;
        this.enableTracing = true;
        this.enableLogging = true;
        this.metricsExporter = "prometheus";
        this.tracingExporter = "otlp";
        this.serviceName = null;
        this.otlpEndpoint = "http://localhost:4318";
        this.resourceAttributes = new HashMap<>();
    }

    public ObservabilityConfig(boolean enabled, boolean enableMetrics, boolean enableTracing,
                               boolean enableLogging, String metricsExporter, String tracingExporter,
                               String serviceName, String otlpEndpoint,
                               Map<String, String> resourceAttributes) {
        this.enabled = enabled;
        this.enableMetrics = enableMetrics;
        this.enableTracing = enableTracing;
        this.enableLogging = enableLogging;
        this.metricsExporter = metricsExporter;
        this.tracingExporter = tracingExporter;
        this.serviceName = serviceName;
        this.otlpEndpoint = otlpEndpoint;
        this.resourceAttributes = resourceAttributes != null ? new HashMap<>(resourceAttributes) : new HashMap<>();
    }

    // Getters and Setters

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnableMetrics() {
        return enableMetrics;
    }

    public void setEnableMetrics(boolean enableMetrics) {
        this.enableMetrics = enableMetrics;
    }

    public boolean isEnableTracing() {
        return enableTracing;
    }

    public void setEnableTracing(boolean enableTracing) {
        this.enableTracing = enableTracing;
    }

    public boolean isEnableLogging() {
        return enableLogging;
    }

    public void setEnableLogging(boolean enableLogging) {
        this.enableLogging = enableLogging;
    }

    public String getMetricsExporter() {
        return metricsExporter;
    }

    public void setMetricsExporter(String metricsExporter) {
        this.metricsExporter = metricsExporter;
    }

    public String getTracingExporter() {
        return tracingExporter;
    }

    public void setTracingExporter(String tracingExporter) {
        this.tracingExporter = tracingExporter;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getOtlpEndpoint() {
        return otlpEndpoint;
    }

    public void setOtlpEndpoint(String otlpEndpoint) {
        this.otlpEndpoint = otlpEndpoint;
    }

    public Map<String, String> getResourceAttributes() {
        return resourceAttributes != null ? new HashMap<>(resourceAttributes) : new HashMap<>();
    }

    public void setResourceAttributes(Map<String, String> resourceAttributes) {
        this.resourceAttributes = resourceAttributes != null ? new HashMap<>(resourceAttributes) : new HashMap<>();
    }

    /**
     * Builder for ObservabilityConfig
     */
    public static class Builder {
        private boolean enabled = true;
        private boolean enableMetrics = true;
        private boolean enableTracing = true;
        private boolean enableLogging = true;
        private String metricsExporter = "prometheus";
        private String tracingExporter = "otlp";
        private String serviceName;
        private String otlpEndpoint = "http://localhost:4318";
        private Map<String, String> resourceAttributes = new HashMap<>();

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder enableMetrics(boolean enableMetrics) {
            this.enableMetrics = enableMetrics;
            return this;
        }

        public Builder enableTracing(boolean enableTracing) {
            this.enableTracing = enableTracing;
            return this;
        }

        public Builder enableLogging(boolean enableLogging) {
            this.enableLogging = enableLogging;
            return this;
        }

        public Builder metricsExporter(String metricsExporter) {
            this.metricsExporter = metricsExporter;
            return this;
        }

        public Builder tracingExporter(String tracingExporter) {
            this.tracingExporter = tracingExporter;
            return this;
        }

        public Builder serviceName(String serviceName) {
            this.serviceName = serviceName;
            return this;
        }

        public Builder otlpEndpoint(String otlpEndpoint) {
            this.otlpEndpoint = otlpEndpoint;
            return this;
        }

        public Builder resourceAttributes(Map<String, String> resourceAttributes) {
            this.resourceAttributes = resourceAttributes != null ? new HashMap<>(resourceAttributes) : new HashMap<>();
            return this;
        }

        public ObservabilityConfig build() {
            return new ObservabilityConfig(enabled, enableMetrics, enableTracing, enableLogging,
                    metricsExporter, tracingExporter, serviceName, otlpEndpoint, resourceAttributes);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return "ObservabilityConfig{" +
                "enabled=" + enabled +
                ", enableMetrics=" + enableMetrics +
                ", enableTracing=" + enableTracing +
                ", enableLogging=" + enableLogging +
                ", metricsExporter='" + metricsExporter + '\'' +
                ", tracingExporter='" + tracingExporter + '\'' +
                ", serviceName='" + serviceName + '\'' +
                ", otlpEndpoint='" + otlpEndpoint + '\'' +
                ", resourceAttributes=" + resourceAttributes +
                '}';
    }
}

package egain.oassdk.config;

import java.util.*;

/**
 * Configuration for SLA integration
 */
public class SLAConfig {

    private String slaFile;
    private boolean monitoring;
    private boolean alerting;
    private List<String> compliance;
    private String apiGateway;
    private List<String> monitoringStack;
    private Map<String, Object> additionalProperties;

    /**
     * Default constructor
     */
    public SLAConfig() {
        this.slaFile = null;
        this.monitoring = true;
        this.alerting = true;
        this.compliance = Arrays.asList("gdpr", "iso27001");
        this.apiGateway = "aws";
        this.monitoringStack = Arrays.asList("prometheus", "grafana");
        this.additionalProperties = new HashMap<>();
    }

    /**
     * Constructor with parameters
     */
    public SLAConfig(String slaFile, boolean monitoring, boolean alerting,
                     List<String> compliance, String apiGateway, List<String> monitoringStack,
                     Map<String, Object> additionalProperties) {
        this.slaFile = slaFile;
        this.monitoring = monitoring;
        this.alerting = alerting;
        this.compliance = compliance != null ? new ArrayList<>(compliance) : new ArrayList<>(Arrays.asList("gdpr", "iso27001"));
        this.apiGateway = apiGateway;
        this.monitoringStack = monitoringStack != null ? new ArrayList<>(monitoringStack) : new ArrayList<>(Arrays.asList("prometheus", "grafana"));
        this.additionalProperties = additionalProperties != null ? new HashMap<>(additionalProperties) : new HashMap<>();
    }

    // Getters and Setters
    public String getSlaFile() {
        return slaFile;
    }

    public void setSlaFile(String slaFile) {
        this.slaFile = slaFile;
    }

    public boolean isMonitoring() {
        return monitoring;
    }

    public void setMonitoring(boolean monitoring) {
        this.monitoring = monitoring;
    }

    public boolean isAlerting() {
        return alerting;
    }

    public void setAlerting(boolean alerting) {
        this.alerting = alerting;
    }

    public List<String> getCompliance() {
        return compliance != null ? new ArrayList<>(compliance) : null;
    }

    public void setCompliance(List<String> compliance) {
        this.compliance = compliance != null ? new ArrayList<>(compliance) : null;
    }

    public String getApiGateway() {
        return apiGateway;
    }

    public void setApiGateway(String apiGateway) {
        this.apiGateway = apiGateway;
    }

    public List<String> getMonitoringStack() {
        return monitoringStack != null ? new ArrayList<>(monitoringStack) : null;
    }

    public void setMonitoringStack(List<String> monitoringStack) {
        this.monitoringStack = monitoringStack != null ? new ArrayList<>(monitoringStack) : null;
    }

    public Map<String, Object> getAdditionalProperties() {
        return additionalProperties != null ? new HashMap<>(additionalProperties) : null;
    }

    public void setAdditionalProperties(Map<String, Object> additionalProperties) {
        this.additionalProperties = additionalProperties != null ? new HashMap<>(additionalProperties) : null;
    }

    /**
     * Builder class for SLAConfig
     */
    public static class Builder {
        private String slaFile = null;
        private boolean monitoring = true;
        private boolean alerting = true;
        private List<String> compliance = Arrays.asList("gdpr", "iso27001");
        private String apiGateway = "aws";
        private List<String> monitoringStack = Arrays.asList("prometheus", "grafana");
        private Map<String, Object> additionalProperties = new HashMap<>();

        public Builder slaFile(String slaFile) {
            this.slaFile = slaFile;
            return this;
        }

        public Builder monitoring(boolean monitoring) {
            this.monitoring = monitoring;
            return this;
        }

        public Builder alerting(boolean alerting) {
            this.alerting = alerting;
            return this;
        }

        public Builder compliance(List<String> compliance) {
            this.compliance = compliance != null ? new ArrayList<>(compliance) : null;
            return this;
        }

        public Builder apiGateway(String apiGateway) {
            this.apiGateway = apiGateway;
            return this;
        }

        public Builder monitoringStack(List<String> monitoringStack) {
            this.monitoringStack = monitoringStack != null ? new ArrayList<>(monitoringStack) : null;
            return this;
        }

        public Builder additionalProperties(Map<String, Object> additionalProperties) {
            this.additionalProperties = additionalProperties != null ? new HashMap<>(additionalProperties) : null;
            return this;
        }

        public SLAConfig build() {
            return new SLAConfig(slaFile, monitoring, alerting, compliance, apiGateway,
                    monitoringStack, additionalProperties);
        }
    }

    /**
     * Create a new builder
     *
     * @return Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return "SLAConfig{" +
                "slaFile='" + slaFile + '\'' +
                ", monitoring=" + monitoring +
                ", alerting=" + alerting +
                ", compliance=" + compliance +
                ", apiGateway='" + apiGateway + '\'' +
                ", monitoringStack=" + monitoringStack +
                ", additionalProperties=" + additionalProperties +
                '}';
    }
}

package egain.oassdk.generators.java;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;

/**
 * Generates build-related artifacts for the Jersey application:
 * <ul>
 *   <li>Main Application class (JAX-RS with Grizzly)</li>
 *   <li>ApiService stub</li>
 *   <li>CorsFilter configuration</li>
 *   <li>GenericExceptionMapper</li>
 *   <li>pom.xml and web.xml</li>
 * </ul>
 */
class JerseyBuildGenerator {

    private final JerseyGenerationContext ctx;

    JerseyBuildGenerator(JerseyGenerationContext ctx) {
        this.ctx = ctx;
    }

    /**
     * Generate the main JAX-RS Application class with Grizzly server support.
     */
    public void generateMainApplicationClass(Map<String, Object> spec, String outputDir, String packageName) throws IOException {
        String packagePath = packageName != null ? packageName : "com.example.api";
        String className = JerseyGenerationContext.getAPITitle(spec).replaceAll("[^a-zA-Z0-9]", "") + "Application";

        String content = String.format("""
                package %s;

                import org.glassfish.grizzly.http.server.HttpServer;
                import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
                import org.glassfish.jersey.server.ResourceConfig;
                import jakarta.ws.rs.ApplicationPath;
                import jakarta.ws.rs.core.Application;
                import jakarta.ws.rs.ext.ContextResolver;
                import jakarta.ws.rs.ext.Provider;
                import java.io.IOException;
                import java.net.URI;
                import java.util.HashSet;
                import java.util.Set;
                import com.fasterxml.jackson.databind.ObjectMapper;
                import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
                import org.glassfish.jersey.jackson.JacksonFeature;
                import java.util.logging.Logger;

                @ApplicationPath("/api")
                public class %s extends ResourceConfig {

                    private static final Logger logger = Logger.getLogger(%s.class.getName());

                    public %s() {
                        // Register packages containing JAX-RS resources
                        packages("%s.resources");

                        // Register Jackson for JSON with JSR310 support
                        register(JacksonFeature.class);
                        register(ObjectMapperContextResolver.class);

                        // Register exception mappers
                        register(%s.exception.GenericExceptionMapper.class);
                %s
                    }

                    @Provider
                    public static class ObjectMapperContextResolver implements ContextResolver<ObjectMapper> {
                        private final ObjectMapper objectMapper;

                        public ObjectMapperContextResolver() {
                            this.objectMapper = new ObjectMapper();
                            this.objectMapper.registerModule(new JavaTimeModule());
                        }

                        @Override
                        public ObjectMapper getContext(Class<?> type) {
                            return objectMapper;
                        }
                    }

                    public static HttpServer startServer() {
                        final String baseUri = "http://localhost:8080/";
                        final ResourceConfig config = new %s();
                        return GrizzlyHttpServerFactory.createHttpServer(URI.create(baseUri), config);
                    }

                    public static void main(String[] args) throws IOException {
                        final HttpServer server = startServer();
                        logger.info("Jersey app started with endpoints available at http://localhost:8080/api/");
                        logger.info("Hit Ctrl-C to stop it...");
                        System.in.read();
                        server.shutdown();
                    }
                }
                """, packagePath, className, className, className, packagePath, packagePath, getObservabilityRegistration(packagePath), className);

        JerseyGenerationContext.writeFile(outputDir + "/src/main/java/" + packagePath.replace(".", "/") + "/" + className + ".java", content);
    }

    /**
     * Returns observability class registration lines for the Application class constructor,
     * or an empty string if observability is not enabled.
     */
    public String getObservabilityRegistration(String packagePath) {
        if (ctx.isObservabilityEnabled()) {
            return String.format("""
                        // Observability
                        register(%s.observability.MetricsFilter.class);
                        register(%s.observability.TracingFilter.class);
                        register(%s.observability.MetricsEndpoint.class);""", packagePath, packagePath, packagePath);
        }
        return "";
    }

    /**
     * Generate ApiService stub class.
     */
    public void generateServices(String outputDir, String packageName) throws IOException {
        String packagePath = packageName != null ? packageName : "com.example.api";

        String serviceContent = String.format("""
                package %s.service;

                import jakarta.inject.Singleton;

                @Singleton
                public class ApiService {

                    // Business logic implementation placeholder
                    // This service should contain the core business logic for the API
                    // Implement methods that correspond to the operations defined in the OpenAPI specification

                }
                """, packagePath);

        JerseyGenerationContext.writeFile(outputDir + "/src/main/java/" + packagePath.replace(".", "/") + "/service/ApiService.java", serviceContent);
    }

    /**
     * Generate CorsFilter configuration class.
     */
    public void generateConfiguration(String outputDir, String packageName) throws IOException {
        String packagePath = packageName != null ? packageName : "com.example.api";

        String configContent = String.format("""
                package %s.config;

                import jakarta.ws.rs.container.ContainerRequestContext;
                import jakarta.ws.rs.container.ContainerResponseContext;
                import jakarta.ws.rs.container.ContainerResponseFilter;
                import jakarta.ws.rs.ext.Provider;
                import java.io.IOException;

                @Provider
                public class CorsFilter implements ContainerResponseFilter {

                    @Override
                    public void filter(ContainerRequestContext requestContext,
                                       ContainerResponseContext responseContext) throws IOException {
                        responseContext.getHeaders().add("Access-Control-Allow-Origin", "*");
                        responseContext.getHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
                        responseContext.getHeaders().add("Access-Control-Allow-Headers", "*");
                    }
                }
                """, packagePath);

        JerseyGenerationContext.writeFile(outputDir + "/src/main/java/" + packagePath.replace(".", "/") + "/config/CorsFilter.java", configContent);
    }

    /**
     * Generate GenericExceptionMapper class.
     */
    public void generateExceptionMappers(String outputDir, String packageName) throws IOException {
        String packagePath = packageName != null ? packageName : "com.example.api";

        String exceptionContent = String.format("""
                package %s.exception;

                import jakarta.ws.rs.core.Response;
                import jakarta.ws.rs.ext.ExceptionMapper;
                import jakarta.ws.rs.ext.Provider;

                @Provider
                public class GenericExceptionMapper implements ExceptionMapper<Exception> {

                    @Override
                    public Response toResponse(Exception exception) {
                        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                                .entity("An error occurred: " + exception.getMessage())
                                .build();
                    }
                }
                """, packagePath);

        JerseyGenerationContext.writeFile(outputDir + "/src/main/java/" + packagePath.replace(".", "/") + "/exception/GenericExceptionMapper.java", exceptionContent);
    }

    /**
     * Orchestrate generation of pom.xml and web.xml.
     */
    public void generateBuildFiles(Map<String, Object> spec, String outputDir, String packageName) throws IOException {
        // Generate pom.xml
        String pomContent = generatePomXml(spec, packageName);
        JerseyGenerationContext.writeFile(outputDir + "/pom.xml", pomContent);

        // Generate web.xml for servlet container deployment
        String webXmlContent = generateWebXml(packageName);
        JerseyGenerationContext.writeFile(outputDir + "/src/main/webapp/WEB-INF/web.xml", webXmlContent);
    }

    /**
     * Generate Maven pom.xml content.
     */
    public String generatePomXml(Map<String, Object> spec, String packageName) {
        return String.format("""
                        <?xml version="1.0" encoding="UTF-8"?>
                        <project xmlns="http://maven.apache.org/POM/4.0.0"
                                 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                                 xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                                 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                            <modelVersion>4.0.0</modelVersion>

                            <groupId>%s</groupId>
                            <artifactId>%s</artifactId>
                            <version>%s</version>
                            <packaging>war</packaging>

                            <name>%s</name>
                            <description>%s</description>

                            <properties>
                                <java.version>21</java.version>
                                <jersey.version>3.1.3</jersey.version>
                                <jackson.version>2.15.2</jackson.version>
                                <maven.compiler.source>21</maven.compiler.source>
                                <maven.compiler.target>21</maven.compiler.target>
                                <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                            </properties>

                            <dependencies>
                                <!-- Jersey -->
                                <dependency>
                                    <groupId>org.glassfish.jersey.core</groupId>
                                    <artifactId>jersey-server</artifactId>
                                    <version>${jersey.version}</version>
                                </dependency>
                                <dependency>
                                    <groupId>org.glassfish.jersey.containers</groupId>
                                    <artifactId>jersey-container-servlet</artifactId>
                                    <version>${jersey.version}</version>
                                </dependency>
                                <dependency>
                                    <groupId>org.glassfish.jersey.containers</groupId>
                                    <artifactId>jersey-container-grizzly2-http</artifactId>
                                    <version>${jersey.version}</version>
                                </dependency>
                                <dependency>
                                    <groupId>org.glassfish.jersey.inject</groupId>
                                    <artifactId>jersey-hk2</artifactId>
                                    <version>${jersey.version}</version>
                                </dependency>
                                <dependency>
                                    <groupId>org.glassfish.jersey.media</groupId>
                                    <artifactId>jersey-media-json-jackson</artifactId>
                                    <version>${jersey.version}</version>
                                </dependency>
                                <dependency>
                                    <groupId>org.glassfish.jersey.media</groupId>
                                    <artifactId>jersey-media-jaxb</artifactId>
                                    <version>${jersey.version}</version>
                                </dependency>

                                <!-- JAXB -->
                                <dependency>
                                    <groupId>javax.xml.bind</groupId>
                                    <artifactId>javax.xml.bind-api</artifactId>
                                    <version>2.3.1</version>
                                </dependency>
                                <dependency>
                                    <groupId>org.glassfish.jaxb</groupId>
                                    <artifactId>jaxb-runtime</artifactId>
                                    <version>2.3.11</version>
                                </dependency>

                                <!-- Jackson -->
                                <dependency>
                                    <groupId>com.fasterxml.jackson.core</groupId>
                                    <artifactId>jackson-databind</artifactId>
                                    <version>${jackson.version}</version>
                                </dependency>
                                <dependency>
                                    <groupId>com.fasterxml.jackson.datatype</groupId>
                                    <artifactId>jackson-datatype-jsr310</artifactId>
                                    <version>${jackson.version}</version>
                                </dependency>

                                <!-- Jakarta APIs -->
                                <dependency>
                                    <groupId>jakarta.ws.rs</groupId>
                                    <artifactId>jakarta.ws.rs-api</artifactId>
                                    <version>3.1.0</version>
                                </dependency>
                                <dependency>
                                    <groupId>jakarta.servlet</groupId>
                                    <artifactId>jakarta.servlet-api</artifactId>
                                    <version>6.0.0</version>
                                    <scope>provided</scope>
                                </dependency>
                                <dependency>
                                    <groupId>jakarta.validation</groupId>
                                    <artifactId>jakarta.validation-api</artifactId>
                                    <version>3.0.2</version>
                                </dependency>
                                <dependency>
                                    <groupId>org.hibernate.validator</groupId>
                                    <artifactId>hibernate-validator</artifactId>
                                    <version>8.0.1.Final</version>
                                </dependency>
                %s
                            </dependencies>

                            <build>
                                <plugins>
                                    <plugin>
                                        <groupId>org.apache.maven.plugins</groupId>
                                        <artifactId>maven-compiler-plugin</artifactId>
                                        <version>3.11.0</version>
                                        <configuration>
                                            <source>21</source>
                                            <target>21</target>
                                        </configuration>
                                    </plugin>
                                    <plugin>
                                        <groupId>org.apache.maven.plugins</groupId>
                                        <artifactId>maven-war-plugin</artifactId>
                                        <version>3.3.2</version>
                                    </plugin>
                                </plugins>
                            </build>
                        </project>
                        """,
                packageName != null ? packageName : "com.example.api",
                JerseyGenerationContext.getAPITitle(spec).toLowerCase(Locale.ROOT).replaceAll("[^a-zA-Z0-9]", "-"),
                JerseyGenerationContext.getAPIVersion(spec),
                JerseyGenerationContext.getAPITitle(spec),
                JerseyGenerationContext.getAPIDescription(spec),
                getObservabilityDependencies());
    }

    /**
     * Returns observability Maven dependencies XML block, or empty string if not enabled.
     */
    public String getObservabilityDependencies() {
        if (!ctx.isObservabilityEnabled()) {
            return "";
        }
        return """
                        <!-- Observability: Micrometer + Prometheus -->
                        <dependency>
                            <groupId>io.micrometer</groupId>
                            <artifactId>micrometer-registry-prometheus</artifactId>
                            <version>1.12.2</version>
                        </dependency>

                        <!-- Observability: OpenTelemetry -->
                        <dependency>
                            <groupId>io.opentelemetry</groupId>
                            <artifactId>opentelemetry-api</artifactId>
                            <version>1.34.1</version>
                        </dependency>
                        <dependency>
                            <groupId>io.opentelemetry</groupId>
                            <artifactId>opentelemetry-sdk</artifactId>
                            <version>1.34.1</version>
                        </dependency>
                        <dependency>
                            <groupId>io.opentelemetry</groupId>
                            <artifactId>opentelemetry-exporter-otlp</artifactId>
                            <version>1.34.1</version>
                        </dependency>""";
    }

    /**
     * Generate web.xml content for servlet container deployment.
     */
    public String generateWebXml(String packageName) {
        String packagePath = packageName != null ? packageName : "com.example.api";
        return String.format("""
                <?xml version="1.0" encoding="UTF-8"?>
                <web-app xmlns="https://jakarta.ee/xml/ns/jakartaee"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="https://jakarta.ee/xml/ns/jakartaee
                         https://jakarta.ee/xml/ns/jakartaee/web-app_6_0.xsd"
                         version="6.0">
                    <servlet>
                        <servlet-name>Jersey Servlet</servlet-name>
                        <servlet-class>org.glassfish.jersey.servlet.ServletContainer</servlet-class>
                        <init-param>
                            <param-name>jakarta.ws.rs.Application</param-name>
                            <param-value>%s.%sApplication</param-value>
                        </init-param>
                        <load-on-startup>1</load-on-startup>
                    </servlet>
                    <servlet-mapping>
                        <servlet-name>Jersey Servlet</servlet-name>
                        <url-pattern>/api/*</url-pattern>
                    </servlet-mapping>
                </web-app>
                """, packagePath, JerseyGenerationContext.getAPITitle(ctx.spec).replaceAll("[^a-zA-Z0-9]", ""));
    }
}

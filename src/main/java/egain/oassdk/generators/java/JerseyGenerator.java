package egain.oassdk.generators.java;

import egain.oassdk.Util;
import egain.oassdk.config.GeneratorConfig;
import egain.oassdk.core.exceptions.GenerationException;
import egain.oassdk.core.logging.LoggerConfig;
import egain.oassdk.generators.CodeGenerator;
import egain.oassdk.generators.ConfigurableGenerator;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;

/**
 * Jersey (JAX-RS) code generator.
 * Acts as the top-level coordinator, delegating to specialised sub-generators.
 */
public class JerseyGenerator implements CodeGenerator, ConfigurableGenerator {

    private static final Logger logger = LoggerConfig.getLogger(JerseyGenerator.class);

    private GeneratorConfig config;
    private boolean isModelsOnly = false;
    private final Map<Object, String> inlinedSchemas = new IdentityHashMap<>();
    private final ThreadLocal<Map<String, Object>> currentSpecForResolution = new ThreadLocal<>();

    // -----------------------------------------------------------------------
    //  CodeGenerator / ConfigurableGenerator interface
    // -----------------------------------------------------------------------

    @Override public String getName()      { return "Jersey Generator"; }
    @Override public String getVersion()   { return "1.0.0"; }
    @Override public String getLanguage()  { return "java"; }
    @Override public String getFramework() { return "jersey"; }

    @Override public void setConfig(GeneratorConfig config) { this.config = config; }
    @Override public GeneratorConfig getConfig()            { return this.config; }

    // -----------------------------------------------------------------------
    //  Main generation entry-point
    // -----------------------------------------------------------------------

    @Override
    public void generate(Map<String, Object> spec, String outputDir, GeneratorConfig config, String packageName) throws GenerationException {
        if (outputDir == null) {
            throw new IllegalArgumentException("Output directory cannot be null");
        }
        this.config = config;

        try {
            currentSpecForResolution.set(spec);
            this.isModelsOnly = config != null && config.isModelsOnly();

            if (!isModelsOnly) {
                createDirectoryStructure(outputDir, packageName);
            }

            JerseyGenerationContext ctx = new JerseyGenerationContext(spec, outputDir, config, packageName);
            JerseyTypeUtils typeUtils = new JerseyTypeUtils(ctx);
            JerseySchemaCollector schemaCollector = new JerseySchemaCollector(ctx);
            JerseyModelGenerator modelGenerator = new JerseyModelGenerator(ctx, typeUtils, schemaCollector);

            schemaCollector.collectInlinedSchemas(spec);
            this.inlinedSchemas.putAll(ctx.inlinedSchemas);

            modelGenerator.generateModels(spec, outputDir, packageName);
            this.inlinedSchemas.putAll(ctx.inlinedSchemas);

            if (config != null && config.isAuthorizationDataGenerationEnabled()) {
                new JerseyAuthorizationDataGenerator().generate(spec, outputDir, config);
            }

            new JerseyQueryParamValidatorGenerator(ctx).generate();

            if (!isModelsOnly) {
                generateMainApplicationClass(spec, outputDir, packageName);

                new JerseyResourceGenerator(ctx, this::getJavaType).generate();
                new JerseyValidationGenerator(ctx).generate();

                generateServices(outputDir, packageName);
                generateConfiguration(outputDir, packageName);
                generateExceptionMappers(outputDir, packageName);
                generateBuildFiles(spec, outputDir, packageName);

                new JerseyExecutorGenerator(ctx, this::getJavaType)
                        .generateExecutors(spec, outputDir, packageName);

                new JerseyObservabilityGenerator(ctx).generate();
            }

        } catch (Exception e) {
            logger.log(java.util.logging.Level.SEVERE, "Failed to generate Jersey application: " + e.getMessage(), e);
            throw new GenerationException("Failed to generate Jersey application: " + e.getMessage(), e);
        } finally {
            currentSpecForResolution.remove();
        }
    }

    // -----------------------------------------------------------------------
    //  Directory structure
    // -----------------------------------------------------------------------

    private void createDirectoryStructure(String outputDir, String packageName) throws IOException {
        if (outputDir == null) {
            throw new IllegalArgumentException("Output directory cannot be null");
        }
        String packagePath = packageName != null ? packageName.replace(".", "/") : "com/example/api";

        for (String dir : new String[]{
                outputDir + "/src/main/java/" + packagePath,
                outputDir + "/src/main/java/" + packagePath + "/resources",
                outputDir + "/src/main/java/" + packagePath + "/model",
                outputDir + "/src/main/java/" + packagePath + "/service",
                outputDir + "/src/main/java/" + packagePath + "/executor",
                outputDir + "/src/main/java/" + packagePath + "/config",
                outputDir + "/src/main/java/" + packagePath + "/exception",
                outputDir + "/src/main/resources",
                outputDir + "/src/test/java/" + packagePath,
                outputDir + "/src/test/java/" + packagePath + "/resources",
                outputDir + "/src/test/java/" + packagePath + "/service"
        }) {
            Files.createDirectories(Paths.get(dir));
        }
    }

    // -----------------------------------------------------------------------
    //  Type resolution delegate (passed as this::getJavaType to sub-generators)
    // -----------------------------------------------------------------------

    private String getJavaType(Map<String, Object> schema) {
        if (schema == null) {
            return "Object";
        }
        Map<String, Object> spec = currentSpecForResolution.get();
        JerseyGenerationContext tempCtx = new JerseyGenerationContext(spec, null, config, null);
        tempCtx.inlinedSchemas.putAll(this.inlinedSchemas);
        return new JerseyTypeUtils(tempCtx).getJavaType(schema);
    }

    private boolean isObservabilityEnabled() {
        return config != null && config.getObservabilityConfig() != null && config.getObservabilityConfig().isEnabled();
    }

    // -----------------------------------------------------------------------
    //  Inline generation helpers (application, services, config, build files)
    // -----------------------------------------------------------------------

    private void generateMainApplicationClass(Map<String, Object> spec, String outputDir, String packageName) throws IOException {
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

                @ApplicationPath("/api")
                public class %s extends ResourceConfig {

                    public %s() {
                        packages("%s.resources");
                        register(JacksonFeature.class);
                        register(ObjectMapperContextResolver.class);
                        register(egain.oassdk.exception.GenericExceptionMapper.class);
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
                """, packagePath, className, className, packagePath, className);

        JerseyGenerationContext.writeFile(
                outputDir + "/src/main/java/" + packagePath.replace(".", "/") + "/" + className + ".java", content);
    }

    private void generateServices(String outputDir, String packageName) throws IOException {
        String packagePath = packageName != null ? packageName : "com.example.api";
        JerseyGenerationContext.writeFile(
                outputDir + "/src/main/java/" + packagePath.replace(".", "/") + "/service/ApiService.java",
                String.format("""
                        package %s.service;

                        import jakarta.inject.Singleton;

                        @Singleton
                        public class ApiService {
                            // Business logic implementation placeholder
                        }
                        """, packagePath));
    }

    private void generateConfiguration(String outputDir, String packageName) throws IOException {
        String packagePath = packageName != null ? packageName : "com.example.api";
        JerseyGenerationContext.writeFile(
                outputDir + "/src/main/java/" + packagePath.replace(".", "/") + "/config/CorsFilter.java",
                String.format("""
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
                        """, packagePath));
    }

    private void generateExceptionMappers(String outputDir, String packageName) throws IOException {
        String packagePath = packageName != null ? packageName : "com.example.api";
        JerseyGenerationContext.writeFile(
                outputDir + "/src/main/java/" + packagePath.replace(".", "/") + "/exception/GenericExceptionMapper.java",
                String.format("""
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
                        """, packagePath));
    }

    private void generateBuildFiles(Map<String, Object> spec, String outputDir, String packageName) throws IOException {
        JerseyGenerationContext.writeFile(outputDir + "/pom.xml", generatePomXml(spec, packageName));
        JerseyGenerationContext.writeFile(outputDir + "/src/main/webapp/WEB-INF/web.xml", generateWebXml(spec, packageName));
    }

    private String generatePomXml(Map<String, Object> spec, String packageName) {
        String pom = String.format("""
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
                        <dependency><groupId>org.glassfish.jersey.core</groupId><artifactId>jersey-server</artifactId><version>${jersey.version}</version></dependency>
                        <dependency><groupId>org.glassfish.jersey.containers</groupId><artifactId>jersey-container-servlet</artifactId><version>${jersey.version}</version></dependency>
                        <dependency><groupId>org.glassfish.jersey.containers</groupId><artifactId>jersey-container-grizzly2-http</artifactId><version>${jersey.version}</version></dependency>
                        <dependency><groupId>org.glassfish.jersey.inject</groupId><artifactId>jersey-hk2</artifactId><version>${jersey.version}</version></dependency>
                        <dependency><groupId>org.glassfish.jersey.media</groupId><artifactId>jersey-media-json-jackson</artifactId><version>${jersey.version}</version></dependency>
                        <dependency><groupId>org.glassfish.jersey.media</groupId><artifactId>jersey-media-jaxb</artifactId><version>${jersey.version}</version></dependency>
                        <dependency><groupId>javax.xml.bind</groupId><artifactId>javax.xml.bind-api</artifactId><version>2.3.1</version></dependency>
                        <dependency><groupId>org.glassfish.jaxb</groupId><artifactId>jaxb-runtime</artifactId><version>2.3.11</version></dependency>
                        <dependency><groupId>com.fasterxml.jackson.core</groupId><artifactId>jackson-databind</artifactId><version>${jackson.version}</version></dependency>
                        <dependency><groupId>com.fasterxml.jackson.datatype</groupId><artifactId>jackson-datatype-jsr310</artifactId><version>${jackson.version}</version></dependency>
                        <dependency><groupId>jakarta.ws.rs</groupId><artifactId>jakarta.ws.rs-api</artifactId><version>3.1.0</version></dependency>
                        <dependency><groupId>jakarta.servlet</groupId><artifactId>jakarta.servlet-api</artifactId><version>6.0.0</version><scope>provided</scope></dependency>
                        <dependency><groupId>jakarta.validation</groupId><artifactId>jakarta.validation-api</artifactId><version>3.0.2</version></dependency>
                        <dependency><groupId>org.hibernate.validator</groupId><artifactId>hibernate-validator</artifactId><version>8.0.1.Final</version></dependency>
                    </dependencies>
                    <build>
                        <plugins>
                            <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-compiler-plugin</artifactId><version>3.11.0</version>
                                <configuration><source>21</source><target>21</target></configuration></plugin>
                            <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-war-plugin</artifactId><version>3.3.2</version></plugin>
                        </plugins>
                    </build>
                </project>
                """,
                packageName != null ? packageName : "com.example.api",
                JerseyGenerationContext.getAPITitle(spec).toLowerCase(Locale.ROOT).replaceAll("[^a-zA-Z0-9]", "-"),
                JerseyGenerationContext.getAPIVersion(spec),
                JerseyGenerationContext.getAPITitle(spec),
                JerseyGenerationContext.getAPIDescription(spec));

        if (isObservabilityEnabled()) {
            String obsDeps = """
                        <dependency><groupId>io.micrometer</groupId><artifactId>micrometer-registry-prometheus</artifactId><version>1.11.5</version></dependency>
                        <dependency><groupId>io.opentelemetry</groupId><artifactId>opentelemetry-api</artifactId><version>1.31.0</version></dependency>
                        <dependency><groupId>io.opentelemetry</groupId><artifactId>opentelemetry-sdk</artifactId><version>1.31.0</version></dependency>
                        <dependency><groupId>io.opentelemetry</groupId><artifactId>opentelemetry-exporter-otlp</artifactId><version>1.31.0</version></dependency>
                        <dependency><groupId>io.opentelemetry</groupId><artifactId>opentelemetry-extension-trace-propagators</artifactId><version>1.31.0</version></dependency>
                        <dependency><groupId>io.opentelemetry</groupId><artifactId>opentelemetry-semconv</artifactId><version>1.31.0-alpha</version></dependency>
                """;
            pom = pom.replace("</dependencies>", obsDeps + "    </dependencies>");
        }
        return pom;
    }

    private String generateWebXml(Map<String, Object> spec, String packageName) {
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
                """, packagePath, JerseyGenerationContext.getAPITitle(spec).replaceAll("[^a-zA-Z0-9]", ""));
    }
}

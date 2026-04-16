package egain.oassdk.docs;

import egain.oassdk.core.exceptions.GenerationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test class for TemplateGenerator
 */
public class TemplateGeneratorTest {
    
    private TemplateGenerator generator;
    private Map<String, Object> spec;
    
    @BeforeEach
    public void setUp() {
        generator = new TemplateGenerator();
        spec = createValidOpenAPISpec();
    }
    
    @Test
    public void testGeneratorInitialization() {
        assertNotNull(generator);
    }
    
    @Test
    public void testGenerateTestDocumentation_Success(@TempDir Path tempDir) throws GenerationException, IOException {
        // Arrange
        TemplateGenerator.TestDocConfig config = new TemplateGenerator.TestDocConfig();
        config.setUnitTestsEnabled(true);
        config.setIntegrationTestsEnabled(true);
        
        // Act
        generator.generateTestDocumentation(spec, tempDir.toString(), config);
        
        // Assert
        Path doc = tempDir.resolve("TEST_DOCUMENTATION.md");
        assertTrue(Files.exists(doc));
        String content = Files.readString(doc);
        assertTrue(content.contains("Schemathesis"));
    }
    
    @Test
    public void testGenerateTestDocumentation_WithNullOutputDir() {
        // Arrange
        TemplateGenerator.TestDocConfig config = new TemplateGenerator.TestDocConfig();
        
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            generator.generateTestDocumentation(spec, null, config);
        });
    }
    
    @Test
    public void testGenerateProjectDocumentation_Success(@TempDir Path tempDir) throws GenerationException {
        // Arrange
        TemplateGenerator.ProjectDocConfig config = new TemplateGenerator.ProjectDocConfig();
        config.setProjectName("Test Project");
        config.setProjectDescription("Test Description");
        
        // Act
        generator.generateProjectDocumentation(spec, tempDir.toString(), config);
        
        // Assert
        assertTrue(Files.exists(tempDir.resolve("PROJECT_DOCUMENTATION.md")));
    }
    
    @Test
    public void testGenerateProjectDocumentation_WithNullOutputDir() {
        // Arrange
        TemplateGenerator.ProjectDocConfig config = new TemplateGenerator.ProjectDocConfig();
        
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            generator.generateProjectDocumentation(spec, null, config);
        });
    }
    
    @Test
    public void testTestDocConfig_SettersAndGetters() {
        // Arrange
        TemplateGenerator.TestDocConfig config = new TemplateGenerator.TestDocConfig();
        
        // Act
        config.setUnitTestsEnabled(false);
        config.setIntegrationTestsEnabled(false);
        config.setNfrTestsEnabled(false);
        config.setPerformanceTestsEnabled(false);
        config.setSecurityTestsEnabled(false);
        config.setTestDataLocation("/custom/path");
        config.setPostmanCollection("custom-collection.json");
        config.setPostmanEnvironment("custom-environment.json");
        
        // Assert
        assertFalse(config.isUnitTestsEnabled());
        assertFalse(config.isIntegrationTestsEnabled());
        assertFalse(config.isNfrTestsEnabled());
        assertFalse(config.isPerformanceTestsEnabled());
        assertFalse(config.isSecurityTestsEnabled());
        assertEquals("/custom/path", config.getTestDataLocation());
        assertEquals("custom-collection.json", config.getPostmanCollection());
        assertEquals("custom-environment.json", config.getPostmanEnvironment());
    }
    
    @Test
    public void testProjectDocConfig_SettersAndGetters() {
        // Arrange
        TemplateGenerator.ProjectDocConfig config = new TemplateGenerator.ProjectDocConfig();
        
        // Act
        config.setProjectName("My Project");
        config.setProjectDescription("My Description");
        config.setJavaVersion("17");
        config.setMavenVersion("3.8");
        config.setDockerImage("my-image");
        config.setDockerPort("9090");
        config.setContactEmail("test@example.com");
        config.setLicense("Apache 2.0");
        config.setLicenseUrl("https://apache.org/licenses/LICENSE-2.0");
        
        // Assert
        assertEquals("My Project", config.getProjectName());
        assertEquals("My Description", config.getProjectDescription());
        assertEquals("17", config.getJavaVersion());
        assertEquals("3.8", config.getMavenVersion());
        assertEquals("my-image", config.getDockerImage());
        assertEquals("9090", config.getDockerPort());
        assertEquals("test@example.com", config.getContactEmail());
        assertEquals("Apache 2.0", config.getLicense());
        assertEquals("https://apache.org/licenses/LICENSE-2.0", config.getLicenseUrl());
    }
    
    @Test
    public void testGenerateTestDocumentation_WithEmptySpec(@TempDir Path tempDir) throws GenerationException {
        // Arrange
        Map<String, Object> emptySpec = new HashMap<>();
        emptySpec.put("openapi", "3.0.0");
        emptySpec.put("info", Map.of("title", "Test", "version", "1.0.0"));
        emptySpec.put("paths", Map.of());
        
        TemplateGenerator.TestDocConfig config = new TemplateGenerator.TestDocConfig();
        
        // Act
        generator.generateTestDocumentation(emptySpec, tempDir.toString(), config);
        
        // Assert
        assertTrue(Files.exists(tempDir.resolve("TEST_DOCUMENTATION.md")));
    }
    
    @Test
    public void testGenerateProjectDocumentation_WithEndpoints(@TempDir Path tempDir) throws GenerationException {
        // Arrange
        Map<String, Object> specWithPaths = new HashMap<>(spec);
        Map<String, Object> paths = new HashMap<>();
        paths.put("/users", Map.of("get", Map.of("operationId", "getUsers")));
        paths.put("/products", Map.of("get", Map.of("operationId", "getProducts")));
        specWithPaths.put("paths", paths);
        
        TemplateGenerator.ProjectDocConfig config = new TemplateGenerator.ProjectDocConfig();
        
        // Act
        generator.generateProjectDocumentation(specWithPaths, tempDir.toString(), config);
        
        // Assert
        assertTrue(Files.exists(tempDir.resolve("PROJECT_DOCUMENTATION.md")));
    }
    
    /**
     * Helper method to create a valid OpenAPI specification
     */
    private Map<String, Object> createValidOpenAPISpec() {
        Map<String, Object> spec = new HashMap<>();
        spec.put("openapi", "3.0.0");
        spec.put("info", Map.of("title", "Test API", "version", "1.0.0", "description", "Test API Description"));
        
        Map<String, Object> paths = new HashMap<>();
        Map<String, Object> pathItem = new HashMap<>();
        Map<String, Object> get = new HashMap<>();
        get.put("operationId", "getTest");
        get.put("summary", "Get test data");
        get.put("responses", Map.of("200", Map.of("description", "OK")));
        pathItem.put("get", get);
        paths.put("/test", pathItem);
        spec.put("paths", paths);
        
        return spec;
    }
}


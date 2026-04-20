package egain.oassdk.test.sequence;

import egain.oassdk.core.exceptions.GenerationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RandomizedSequenceTester.
 */
public class RandomizedSequenceTesterTest {

    @Test
    public void testGenerateSequenceTestsNullOutputDirThrows() {
        RandomizedSequenceTester generator = new RandomizedSequenceTester();
        assertThrows(Exception.class, () ->
                generator.generateSequenceTests(Map.of(), null, "http://localhost:8080")
        );
    }

    @Test
    public void testGenerateSequenceTestsCreatesFiles(@TempDir Path tempDir) throws GenerationException {
        RandomizedSequenceTester generator = new RandomizedSequenceTester();
        Map<String, Object> spec = Map.of("paths", Map.of());
        generator.generateSequenceTests(spec, tempDir.toString(), "http://localhost:8080");
        assertTrue(Files.exists(tempDir.resolve("SequenceTestFramework.java")));
        assertTrue(Files.exists(tempDir.resolve("RandomSequenceGenerator.java")));
    }

    @Test
    public void testGenerateSequenceTestsFolderLikeSpecContainsScenariosAndTemplateResolution(@TempDir Path tempDir) throws Exception {
        Map<String, Object> spec = minimalFolderSpec();
        RandomizedSequenceTester generator = new RandomizedSequenceTester();
        generator.generateSequenceTests(spec, tempDir.toString(), "http://localhost:8080/knowledge/contentmgr/v4");

        String framework = Files.readString(tempDir.resolve("SequenceTestFramework.java"), StandardCharsets.UTF_8);
        assertTrue(framework.contains("resolveTemplateParams"));
        assertTrue(framework.contains("captureLocationState"));
        assertTrue(framework.contains("executeAPICall(APICall call, String resolvedPath)"));

        String cases = Files.readString(tempDir.resolve("SequenceTestCases.java"), StandardCharsets.UTF_8);
        assertTrue(cases.contains("testScenario_createAndGet"));
        assertTrue(cases.contains("scenarioCreateAndGet"));
        assertTrue(cases.contains("/folders/{folderID}"));
        assertTrue(cases.contains("generateScenarioBiasedSequence"));

        String gen = Files.readString(tempDir.resolve("RandomSequenceGenerator.java"), StandardCharsets.UTF_8);
        assertTrue(gen.contains("generateScenarioBiasedSequence"));
        assertTrue(gen.contains("scenarioTemplates"));
    }

    /**
     * Minimal OpenAPI-like map with folder create/get used to validate RST generation.
     */
    private static Map<String, Object> minimalFolderSpec() {
        Map<String, Object> folderSchema = new LinkedHashMap<>();
        folderSchema.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();
        Map<String, Object> nameProp = new LinkedHashMap<>();
        nameProp.put("type", "string");
        props.put("name", nameProp);
        folderSchema.put("properties", props);

        Map<String, Object> components = new LinkedHashMap<>();
        components.put("schemas", Map.of("FolderCreate", folderSchema));

        Map<String, Object> postOp = new LinkedHashMap<>();
        postOp.put("operationId", "createFolder");
        Map<String, Object> rb = new LinkedHashMap<>();
        Map<String, Object> content = new LinkedHashMap<>();
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("schema", Map.of("$ref", "#/components/schemas/FolderCreate"));
        content.put("application/json", json);
        rb.put("content", content);
        postOp.put("requestBody", rb);

        Map<String, Object> kbLangParam = new LinkedHashMap<>();
        kbLangParam.put("name", "kbLanguage");
        kbLangParam.put("in", "query");
        kbLangParam.put("required", true);
        kbLangParam.put("schema", Map.of("type", "string", "enum", List.of("en-US", "fr-FR")));

        Map<String, Object> levelParam = new LinkedHashMap<>();
        levelParam.put("name", "$level");
        levelParam.put("in", "query");
        levelParam.put("required", false);
        levelParam.put("schema", Map.of("type", "integer", "minimum", 0));

        Map<String, Object> getOp = new LinkedHashMap<>();
        getOp.put("operationId", "getFolder");
        getOp.put("parameters", List.of(kbLangParam, levelParam));

        Map<String, Object> foldersPath = new LinkedHashMap<>();
        foldersPath.put("post", postOp);

        Map<String, Object> folderIdPath = new LinkedHashMap<>();
        folderIdPath.put("get", getOp);

        Map<String, Object> paths = new LinkedHashMap<>();
        paths.put("/folders", foldersPath);
        paths.put("/folders/{folderID}", folderIdPath);

        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("paths", paths);
        spec.put("components", components);
        return spec;
    }
}

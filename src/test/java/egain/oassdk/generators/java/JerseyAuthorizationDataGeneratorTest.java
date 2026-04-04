package egain.oassdk.generators.java;

import egain.oassdk.config.GeneratorConfig;
import egain.oassdk.core.exceptions.GenerationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JerseyAuthorizationDataGeneratorTest {

    @TempDir
    Path tempDir;

    @Test
    void getterName_stripsHungarianMPrefix() {
        assertEquals("getFolderId", JerseyAuthorizationDataGenerator.getterName("folderId"));
        assertEquals("getDepartmentId", JerseyAuthorizationDataGenerator.getterName("mDepartmentId"));
        assertEquals("getParentFolderId", JerseyAuthorizationDataGenerator.getterName("mParentFolderId"));
    }

    @Test
    void generate_writesAuthorizationDataClass() throws Exception {
        Map<String, Object> folderAuth = new LinkedHashMap<>();
        folderAuth.put("className", "FolderAuthorizationData");
        folderAuth.put("package", "egain.ws.common.authorization.authoring.folder.data");
        folderAuth.put("extends", "egain.ws.common.authorization.KnowledgeAuthorizationData");
        folderAuth.put("imports", List.of("egain.ws.common.authorization.authoring.folder.FolderAuthorizer"));
        folderAuth.put("fields", List.of(
                Map.of("name", "folderId", "type", "long"),
                Map.of("name", "ids", "type", "java.util.List<Long>"),
                Map.of("name", "operation", "type", "FolderAuthorizer.Operation"),
                Map.of("name", "mDepartmentId", "type", "long", "default", -1),
                Map.of("name", "mParentFolderId", "type", "long", "default", -1)
        ));
        Map<String, Object> ctor1 = Map.of("parameters", List.of("folderId", "ids", "operation"));
        Map<String, Object> deptParam = new LinkedHashMap<>();
        deptParam.put("name", "departmentId");
        deptParam.put("assignsTo", "mDepartmentId");
        Map<String, Object> parentParam = new LinkedHashMap<>();
        parentParam.put("name", "parentFolderId");
        parentParam.put("assignsTo", "mParentFolderId");
        Map<String, Object> ctor2 = Map.of("parameters", List.of(
                "folderId", "ids", deptParam, parentParam, "operation"
        ));
        folderAuth.put("constructors", List.of(ctor1, ctor2));

        Map<String, Object> folderSchema = new LinkedHashMap<>();
        folderSchema.put("type", "object");
        folderSchema.put(JerseyAuthorizationDataGenerator.EXTENSION_KEY, folderAuth);

        Map<String, Object> schemas = new LinkedHashMap<>();
        schemas.put("Folder", folderSchema);
        Map<String, Object> components = Map.of("schemas", schemas);
        Map<String, Object> spec = Map.of("components", components);

        GeneratorConfig config = GeneratorConfig.builder()
                .authorizationDataGenerationEnabled(true)
                .build();

        new JerseyAuthorizationDataGenerator().generate(spec, tempDir.toString(), config);

        Path javaFile = tempDir.resolve("src/main/java/egain/ws/common/authorization/authoring/folder/data/FolderAuthorizationData.java");
        assertTrue(Files.isRegularFile(javaFile));
        String content = Files.readString(javaFile);

        assertTrue(content.contains("public class FolderAuthorizationData extends egain.ws.common.authorization.KnowledgeAuthorizationData"));
        assertTrue(content.contains("import egain.ws.common.authorization.authoring.folder.FolderAuthorizer;"));
        assertTrue(content.contains("import java.util.List;"));
        assertTrue(content.contains("private final List<Long> ids;"));
        assertTrue(content.contains("public FolderAuthorizationData(long folderId, List<Long> ids, FolderAuthorizer.Operation operation)"));
        assertTrue(content.contains("this.mDepartmentId = -1L;"));
        assertTrue(content.contains("public FolderAuthorizationData(long folderId, List<Long> ids, long departmentId, long parentFolderId, FolderAuthorizer.Operation operation)"));
        assertTrue(content.contains("this.mDepartmentId = departmentId;"));
        assertTrue(content.contains("public long getDepartmentId()"));
        assertTrue(content.contains("return \"FolderAuthorizationData{\" +"));
        assertTrue(content.contains("'}';"), "toString should close with brace");
    }

    @Test
    void generate_mergesInfoDefaultsExtends() throws Exception {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("title", "Test");
        Map<String, Object> defaults = Map.of("extends", "com.example.BaseAuthData");
        info.put(JerseyAuthorizationDataGenerator.INFO_DEFAULTS_KEY, defaults);

        Map<String, Object> ext = new LinkedHashMap<>();
        ext.put("className", "WidgetAuthorizationData");
        ext.put("package", "com.example.auth.widget");
        ext.put("fields", List.of(Map.of("name", "widgetId", "type", "long")));
        ext.put("constructors", List.of(Map.of("parameters", List.of("widgetId"))));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put(JerseyAuthorizationDataGenerator.EXTENSION_KEY, ext);

        Map<String, Object> spec = Map.of(
                "info", info,
                "components", Map.of("schemas", Map.of("Widget", schema))
        );

        GeneratorConfig config = GeneratorConfig.builder()
                .authorizationDataGenerationEnabled(true)
                .build();

        new JerseyAuthorizationDataGenerator().generate(spec, tempDir.toString(), config);

        String content = Files.readString(tempDir.resolve("src/main/java/com/example/auth/widget/WidgetAuthorizationData.java"));
        assertTrue(content.contains("extends com.example.BaseAuthData"));
    }

    @Test
    void generate_rejectsMissingDefaultForOmittedField() {
        Map<String, Object> ext = new LinkedHashMap<>();
        ext.put("className", "BadAuth");
        ext.put("package", "com.example.bad");
        ext.put("extends", "com.example.Base");
        ext.put("fields", List.of(
                Map.of("name", "a", "type", "long"),
                Map.of("name", "b", "type", "long")
        ));
        ext.put("constructors", List.of(Map.of("parameters", List.of("a"))));

        Map<String, Object> schema = Map.of(JerseyAuthorizationDataGenerator.EXTENSION_KEY, ext);
        Map<String, Object> spec = Map.of("components", Map.of("schemas", Map.of("Bad", schema)));

        GeneratorConfig config = GeneratorConfig.builder()
                .authorizationDataGenerationEnabled(true)
                .build();

        assertThrows(GenerationException.class,
                () -> new JerseyAuthorizationDataGenerator().generate(spec, tempDir.toString(), config));
    }

    @Test
    void generate_noOpWhenDisabled() throws Exception {
        Map<String, Object> ext = new LinkedHashMap<>();
        ext.put("className", "SkipAuth");
        ext.put("package", "com.example.skip");
        ext.put("extends", "com.example.Base");
        ext.put("fields", List.of(Map.of("name", "x", "type", "int", "default", 0)));
        ext.put("constructors", List.of(Map.of("parameters", List.of())));

        Map<String, Object> schema = Map.of(JerseyAuthorizationDataGenerator.EXTENSION_KEY, ext);
        Map<String, Object> spec = Map.of("components", Map.of("schemas", Map.of("S", schema)));

        GeneratorConfig config = GeneratorConfig.builder()
                .authorizationDataGenerationEnabled(false)
                .build();

        new JerseyAuthorizationDataGenerator().generate(spec, tempDir.toString(), config);

        assertTrue(Files.notExists(tempDir.resolve("src/main/java/com/example/skip/SkipAuth.java")));
    }
}

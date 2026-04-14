package egain.oassdk.generators.java;

import egain.oassdk.OASSDK;
import egain.oassdk.config.GeneratorConfig;
import egain.oassdk.core.exceptions.OASSDKException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JerseyModelGenerator simple oneOf XOR nested id")
class JerseyModelGeneratorOneOfXorTest {

    @TempDir
    Path tempOutputDir;

    @Test
    @DisplayName("OpenAPI string id forces isSetId predicate even if Java type is double")
    void stringOpenApiTypeOverridesDoubleJavaType() {
        String clause = JerseyModelGenerator.nestedXorNestedIdReturnClause(
                "department", "department", "double", "string", false);
        assertEquals("this.department == null || this.department.isSetId()", clause);
        assertFalse(clause.contains("0.0"));
    }

    @Test
    @DisplayName("Default mode: primitive double id uses zero check")
    void defaultPrimitiveDoubleUsesNonZeroCheck() {
        String clause = JerseyModelGenerator.nestedXorNestedIdReturnClause(
                "department", "department", "double", "number", false);
        assertEquals(
                "this.department == null || this.department.getId() != 0.0",
                clause);
    }

    @Test
    @DisplayName("Legacy mode: parent branch uses historical predicate")
    void legacyParentBranch() {
        String clause = JerseyModelGenerator.nestedXorNestedIdReturnClause(
                "parent", "parent", "FolderSummary", "string", true);
        assertEquals("(this.parent != null && !this.parent.isSetId())", clause);
    }

    @Test
    @DisplayName("Legacy mode: string id on department uses !isSetId when not parent")
    void legacyDepartmentStringId() {
        String clause = JerseyModelGenerator.nestedXorNestedIdReturnClause(
                "department", "department", "Department", "string", true);
        assertEquals("(this.department != null && !this.department.isSetId())", clause);
    }

    @Test
    @DisplayName("Generated createFolder model uses isSetId for string ids (no 0.0)")
    void integrationCreateFolderUsesIsSetIdForStringOpenApiId() throws OASSDKException, IOException {
        Path outputDir = tempOutputDir.resolve("oneof-gen");
        GeneratorConfig config = GeneratorConfig.builder()
                .modelsOnly(true)
                .build();
        OASSDK sdk = new OASSDK(config, null, null);
        try {
            sdk.loadSpec("src/test/resources/create_folder_oneof_xor.yaml");
            sdk.generateApplication("java", "jersey", "com.test.oneofxor", outputDir.toString());

            Path createFolderJava;
            try (Stream<Path> walk = Files.walk(outputDir)) {
                createFolderJava = walk
                        .filter(p -> p.getFileName().toString().equals("CreateFolder.java"))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("CreateFolder.java not found under " + outputDir));
            }
            String content = Files.readString(createFolderJava);
            assertTrue(content.contains(".isSetId()"), "Expected nested id checks to use isSetId() for string ids");
            assertFalse(content.contains("getId() != 0.0"),
                    "Must not emit double zero check when OpenAPI id is string");
            assertTrue(content.contains("isValidRequiredMutuallyExclusive()"));
            assertTrue(content.contains("isValidDepartment()"));
            assertTrue(content.contains("isValidParent()"));
            assertTrue(content.contains("If department is set then department.id must be set"));
            assertTrue(content.contains("If parent is set then parent.id must be set"));
        } finally {
            sdk.close();
        }
    }

    @Test
    @DisplayName("Legacy flag: longer validation messages on nested id asserts")
    void integrationLegacyMessages() throws OASSDKException, IOException {
        Path outputDir = tempOutputDir.resolve("oneof-legacy");
        GeneratorConfig config = GeneratorConfig.builder()
                .modelsOnly(true)
                .legacyXorNestedIdAsserts(true)
                .build();
        OASSDK sdk = new OASSDK(config, null, null);
        try {
            sdk.loadSpec("src/test/resources/create_folder_oneof_xor.yaml");
            sdk.generateApplication("java", "jersey", "com.test.oneofxor", outputDir.toString());

            Path createFolderJava;
            try (Stream<Path> walk = Files.walk(outputDir)) {
                createFolderJava = walk
                        .filter(p -> p.getFileName().toString().equals("CreateFolder.java"))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("CreateFolder.java not found"));
            }
            String content = Files.readString(createFolderJava);
            assertTrue(content.contains("when department attribute is set"));
            assertTrue(content.contains("when parent attribute is set"));
            assertTrue(content.contains("(this.parent != null && !this.parent.isSetId())"));
        } finally {
            sdk.close();
        }
    }
}

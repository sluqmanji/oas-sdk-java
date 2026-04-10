package egain.oassdk.generators.java;

import egain.oassdk.OASSDK;
import egain.oassdk.core.exceptions.OASSDKException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for contentmgr-style {@code /folders} resource generation:
 * singular class name, per-method {@code @Actor}, on-behalf OAuth mapping, scope enum names, List import, media types.
 */
@DisplayName("Jersey folders resource generation (contentmgr-style)")
class JerseyFoldersResourceGenerationTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("FolderResource: singular name, per-method Actor, CLIENT_ON_BEHALF_OF scopes, List import, JSON media types")
    void foldersResourceMatchesContentmgrStyle() throws OASSDKException, IOException {
        String yaml = """
                openapi: 3.0.0
                info:
                  title: Folders API
                  version: 1.0.0
                servers:
                  - url: https://api.example.com/knowledge/contentmgr/v4
                paths:
                  /folders:
                    post:
                      summary: Create Folder
                      operationId: createFolder
                      security:
                        - oAuthUser:
                            - knowledge.contentmgr.manage
                        - oAuthOnBehalfOfUser:
                            - knowledge.contentmgr.onbehalfof.manage
                      requestBody:
                        content:
                          application/json:
                            schema:
                              type: object
                      responses:
                        '201':
                          description: Created
                    get:
                      summary: Get Sub Folders
                      operationId: getSubFolders
                      security:
                        - oAuthUser:
                            - knowledge.contentmgr.read
                        - oAuthOnBehalfOfUser:
                            - knowledge.contentmgr.onbehalfof.read
                      parameters:
                        - name: folderAdditionalAttributes
                          in: query
                          schema:
                            type: array
                            items:
                              type: string
                      responses:
                        '200':
                          description: OK
                          content:
                            application/json:
                              schema:
                                type: object
                  /folders/{folderID}:
                    get:
                      summary: Get Folders By ID
                      operationId: getFolder
                      security:
                        - oAuthUser:
                            - knowledge.contentmgr.read
                        - oAuthOnBehalfOfUser:
                            - knowledge.contentmgr.onbehalfof.read
                      parameters:
                        - name: folderID
                          in: path
                          required: true
                          schema:
                            type: string
                      responses:
                        '200':
                          description: OK
                          content:
                            application/json:
                              schema:
                                type: object
                components:
                  securitySchemes:
                    oAuthUser:
                      type: oauth2
                      flows: {}
                    oAuthOnBehalfOfUser:
                      type: oauth2
                      flows: {}
                """;

        Path specFile = tempDir.resolve("folders-api.yaml");
        Files.writeString(specFile, yaml);

        Path out = tempDir.resolve("gen");
        String pkg = "com.test.contentmgr";
        try (OASSDK sdk = new OASSDK()) {
            sdk.loadSpec(specFile.toString());
            sdk.generateApplication("java", "jersey", pkg, out.toString());
        }

        Path resource = out.resolve("src/main/java/" + pkg.replace('.', '/') + "/resources/FolderResource.java");
        assertTrue(Files.exists(resource), "Expected FolderResource.java (singular) at " + resource);
        String content = Files.readString(resource);

        assertTrue(content.contains("public class FolderResource"), "Class should be FolderResource");
        int classDecl = content.indexOf("public class FolderResource");
        assertTrue(classDecl > 0);
        assertFalse(content.substring(0, classDecl).contains("@Actor("),
                "@Actor should be per-method only, not on the class");

        assertTrue(content.contains("ActorType.USER"), "Should include USER actor");
        assertTrue(content.contains("ActorType.CLIENT_ON_BEHALF_OF_USER"), "Should map oAuthOnBehalfOfUser");
        assertTrue(content.contains("OAuthScope.KNOWLEDGE_CONTENTMGR_CLIENT_ON_BEHALF_OF_MANAGE"),
                "onbehalfof.manage should become CLIENT_ON_BEHALF_OF_MANAGE");
        assertTrue(content.contains("OAuthScope.KNOWLEDGE_CONTENTMGR_CLIENT_ON_BEHALF_OF_READ"),
                "onbehalfof.read should become CLIENT_ON_BEHALF_OF_READ");
        assertTrue(content.contains("OAuthScope.KNOWLEDGE_CONTENTMGR_MANAGE"), "direct manage scope");
        assertTrue(content.contains("OAuthScope.KNOWLEDGE_CONTENTMGR_READ"), "direct read scope");

        assertTrue(content.contains("import java.util.List;"), "Array query param should require List import");
        assertTrue(content.contains("List<String>"), "folderAdditionalAttributes should be List<String>");

        assertTrue(content.contains("@Produces(MediaType.APPLICATION_JSON)"), "Inferred JSON-only produces");
        assertTrue(content.contains("@Consumes(MediaType.APPLICATION_JSON)"), "Inferred JSON-only consumes");
        assertTrue(content.contains("    @Consumes\n"), "GET without body should override consumes");
    }

    @Test
    @DisplayName("x-egain-resource-class-name overrides class name")
    void resourceClassNameExtensionOverridesHeuristic() throws OASSDKException, IOException {
        String yaml = """
                openapi: 3.0.0
                info:
                  title: X API
                  version: 1.0.0
                paths:
                  /folders:
                    x-egain-resource-class-name: CustomFoldersFacade
                    get:
                      operationId: listFolders
                      security:
                        - oAuthUser:
                            - a.b.read
                      responses:
                        '200':
                          description: OK
                components:
                  securitySchemes:
                    oAuthUser:
                      type: oauth2
                      flows: {}
                """;

        Path specFile = tempDir.resolve("x-folder.yaml");
        Files.writeString(specFile, yaml);

        Path out = tempDir.resolve("gen-x");
        String pkg = "com.test.x";
        try (OASSDK sdk = new OASSDK()) {
            sdk.loadSpec(specFile.toString());
            sdk.generateApplication("java", "jersey", pkg, out.toString());
        }

        Path resource = out.resolve("src/main/java/" + pkg.replace('.', '/') + "/resources/CustomFoldersFacade.java");
        assertTrue(Files.exists(resource), "Expected CustomFoldersFacade.java from extension");
        assertTrue(Files.readString(resource).contains("public class CustomFoldersFacade"));
    }
}

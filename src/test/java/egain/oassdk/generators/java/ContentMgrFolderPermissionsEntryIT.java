package egain.oassdk.generators.java;

import egain.oassdk.OASSDK;
import egain.oassdk.Util;
import egain.oassdk.config.GeneratorConfig;
import egain.oassdk.core.exceptions.OASSDKException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Optional integration check against published contentmgr v4 (skipped if repo layout differs).
 */
class ContentMgrFolderPermissionsEntryIT {

    private static final Path PUBLISHED_ROOT = Paths.get("C:/eGain/published");
    private static final Path CONTENTMGR_API = PUBLISHED_ROOT.resolve("knowledge/contentmgr/v4/api.yaml");

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getSpec(OASSDK sdk) throws Exception {
        Field f = OASSDK.class.getDeclaredField("spec");
        f.setAccessible(true);
        return (Map<String, Object>) f.get(sdk);
    }

    @Test
    void contentMgrSpec_mergesFolderPermissionsEntry_andCollectorReferencesIt() throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(CONTENTMGR_API),
            "Skip when published contentmgr api.yaml is not at " + CONTENTMGR_API);

        GeneratorConfig config = GeneratorConfig.builder()
                .language("java")
                .framework("jersey")
                .packageName("com.egain.knowledge.contentmgr.v4.api")
                .outputDir(System.getProperty("java.io.tmpdir"))
                .searchPaths(List.of(PUBLISHED_ROOT.toString()))
                .build();

        try (OASSDK sdk = new OASSDK(config, null, null)) {
            sdk.loadSpec(CONTENTMGR_API.toString());
            Map<String, Object> spec = getSpec(sdk);
            Map<String, Object> components = Util.asStringObjectMap(spec.get("components"));
            assertTrue(components != null, "components present");
            Map<String, Object> schemas = Util.asStringObjectMap(components.get("schemas"));
            assertTrue(schemas != null && schemas.containsKey("FolderPermissionsEntry"),
                "Merged spec must include components/schemas/FolderPermissionsEntry");

            JerseyGenerationContext ctx = new JerseyGenerationContext(spec, "", config, config.getPackageName());
            JerseySchemaCollector collector = new JerseySchemaCollector(ctx);
            Set<String> referenced = collector.collectAllReferencedSchemaNames(spec);
            assertTrue(referenced.contains("FolderPermissionsEntry"),
                "Referenced-schema set must include FolderPermissionsEntry for model generation");
        } catch (OASSDKException e) {
            throw new AssertionError(e);
        }
    }
}

package egain.oassdk.examples;

import egain.oassdk.core.exceptions.OASSDKException;
import egain.oassdk.core.parser.OASParser;
import egain.oassdk.test.sequence.RandomizedSequenceTester;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Loads an OpenAPI YAML from disk, resolves external {@code $ref}s using a search root, and emits
 * randomized sequence test (RST) Java sources via {@link RandomizedSequenceTester}.
 * <p>
 * Run from the oas-sdk-java repo root. Prefer environment variables so shells do not split paths on spaces:
 * {@code RST_SPEC_PATH}, {@code RST_SEARCH_ROOT}, {@code RST_OUTPUT_DIR}, optional {@code RST_BASE_URL}; then run
 * {@code mvn -q exec:java} (see {@code exec-maven-plugin} {@code mainClass} in {@code pom.xml}).
 * <p>
 * Or program arguments: {@code specPath searchRoot outputDir [baseUrl]}. If {@code baseUrl} is omitted, defaults to
 * {@code https://localhost/knowledge/contentmgr/v4}.
 */
public final class GenerateRstFromPublishedSpec {

    private static final String DEFAULT_BASE_URL = "https://localhost/knowledge/contentmgr/v4";

    private GenerateRstFromPublishedSpec() {
    }

    public static void main(String[] args) {
        String specPath;
        String searchRoot;
        String outputDir;
        String baseUrl;
        if (args.length >= 3) {
            specPath = Objects.requireNonNull(args[0], "specPath").trim();
            searchRoot = Objects.requireNonNull(args[1], "searchRoot").trim();
            outputDir = Objects.requireNonNull(args[2], "outputDir").trim();
            baseUrl = args.length >= 4 && !args[3].isBlank() ? args[3].trim() : DEFAULT_BASE_URL;
        } else if (args.length == 0) {
            specPath = trimOrNull(System.getenv("RST_SPEC_PATH"));
            searchRoot = trimOrNull(System.getenv("RST_SEARCH_ROOT"));
            outputDir = trimOrNull(System.getenv("RST_OUTPUT_DIR"));
            String envBase = trimOrNull(System.getenv("RST_BASE_URL"));
            baseUrl = envBase != null ? envBase : DEFAULT_BASE_URL;
            if (specPath == null || searchRoot == null || outputDir == null) {
                System.err.println("Usage: GenerateRstFromPublishedSpec <specPath> <searchRoot> <outputDir> [baseUrl]");
                System.err.println("Or set env RST_SPEC_PATH, RST_SEARCH_ROOT, RST_OUTPUT_DIR, optional RST_BASE_URL and run with no args.");
                System.exit(1);
                return;
            }
        } else {
            System.err.println("Usage: GenerateRstFromPublishedSpec <specPath> <searchRoot> <outputDir> [baseUrl]");
            System.err.println("Or set env RST_SPEC_PATH, RST_SEARCH_ROOT, RST_OUTPUT_DIR, optional RST_BASE_URL and run with no args.");
            System.exit(1);
            return;
        }

        try {
            run(specPath, searchRoot, outputDir, baseUrl);
            System.out.println("RST generation finished: " + outputDir);
        } catch (OASSDKException e) {
            System.err.println(e.getMessage());
            if (e.getCause() != null) {
                e.getCause().printStackTrace(System.err);
            }
            System.exit(2);
        } catch (Exception e) {
            e.printStackTrace(System.err);
            System.exit(3);
        }
    }

    private static void run(String specPath, String searchRoot, String outputDir, String baseUrl)
            throws OASSDKException, IOException {
        Path spec = Paths.get(specPath).normalize();
        if (!Files.isRegularFile(spec)) {
            throw new OASSDKException("Spec file not found: " + spec);
        }

        OASParser parser = new OASParser(List.of(searchRoot));
        String specKey = spec.toAbsolutePath().toString();
        if (File.separatorChar == '\\') {
            specKey = specKey.replace('\\', '/');
        }
        Map<String, Object> map = parser.parse(specKey);
        map = parser.resolveReferences(map, specKey);

        Files.createDirectories(Paths.get(outputDir));
        new RandomizedSequenceTester().generateSequenceTests(map, outputDir, baseUrl);
    }

    private static String trimOrNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}

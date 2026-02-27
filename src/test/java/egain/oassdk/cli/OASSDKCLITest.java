package egain.oassdk.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases for OASSDKCLI
 */
public class OASSDKCLITest {
    
    @Test
    public void testCLIInitialization() {
        OASSDKCLI cli = new OASSDKCLI();
        assertNotNull(cli);
    }
    
    @Test
    public void testCall() throws Exception {
        OASSDKCLI cli = new OASSDKCLI();
        Integer result = cli.call();
        
        assertNotNull(result);
        assertEquals(0, result);
    }
    
    @Test
    public void testGenerateCommandInitialization() {
        OASSDKCLI.GenerateCommand command = new OASSDKCLI.GenerateCommand();
        assertNotNull(command);
    }
    
    @Test
    public void testTestsCommandInitialization() {
        OASSDKCLI.TestsCommand command = new OASSDKCLI.TestsCommand();
        assertNotNull(command);
    }
    
    @Test
    public void testMockDataCommandInitialization() {
        OASSDKCLI.MockDataCommand command = new OASSDKCLI.MockDataCommand();
        assertNotNull(command);
    }
    
    @Test
    public void testSLACommandInitialization() {
        OASSDKCLI.SLACommand command = new OASSDKCLI.SLACommand();
        assertNotNull(command);
    }
    
    @Test
    public void testAllCommandInitialization() {
        OASSDKCLI.AllCommand command = new OASSDKCLI.AllCommand();
        assertNotNull(command);
    }
    
    @Test
    public void testValidateCommandInitialization() {
        OASSDKCLI.ValidateCommand command = new OASSDKCLI.ValidateCommand();
        assertNotNull(command);
    }
    
    @Test
    public void testInfoCommandInitialization() {
        OASSDKCLI.InfoCommand command = new OASSDKCLI.InfoCommand();
        assertNotNull(command);
    }

    @Test
    public void testGenerateCommandWithSpecZip(@TempDir Path tempDir) throws Exception {
        String yaml = "openapi: 3.0.0\n"
                + "info:\n  title: Zip API\n  version: 1.0.0\n"
                + "paths:\n  /ping:\n    get:\n      operationId: ping\n      responses:\n        '200':\n          description: OK\n";
        Path zipPath = tempDir.resolve("specs.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            zos.putNextEntry(new ZipEntry("api.yaml"));
            zos.write(yaml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        Path outputDir = tempDir.resolve("out");

        int exit = new picocli.CommandLine(new OASSDKCLI())
                .addSubcommand("generate", new OASSDKCLI.GenerateCommand())
                .addSubcommand("tests", new OASSDKCLI.TestsCommand())
                .addSubcommand("mockdata", new OASSDKCLI.MockDataCommand())
                .addSubcommand("sla", new OASSDKCLI.SLACommand())
                .addSubcommand("all", new OASSDKCLI.AllCommand())
                .addSubcommand("validate", new OASSDKCLI.ValidateCommand())
                .addSubcommand("info", new OASSDKCLI.InfoCommand())
                .execute("generate", "--spec-zip", zipPath.toString(), "-o", outputDir.toString(), "api.yaml");

        assertEquals(0, exit);
        assertTrue(Files.exists(outputDir));
    }
}


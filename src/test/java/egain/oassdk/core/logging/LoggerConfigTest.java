package egain.oassdk.core.logging;

import org.junit.jupiter.api.Test;

import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LoggerConfig.
 */
public class LoggerConfigTest {

    @Test
    public void testGetLoggerWithClass() {
        Logger logger = LoggerConfig.getLogger(LoggerConfigTest.class);
        assertNotNull(logger);
        assertEquals(LoggerConfigTest.class.getName(), logger.getName());
    }

    @Test
    public void testGetLoggerWithName() {
        Logger logger = LoggerConfig.getLogger("egain.oassdk.test");
        assertNotNull(logger);
        assertEquals("egain.oassdk.test", logger.getName());
    }

    @Test
    public void testGetLogDirectoryAfterInitialize() {
        // getLogger triggers initialize() if not yet done
        LoggerConfig.getLogger(LoggerConfigTest.class);
        String dir = LoggerConfig.getLogDirectory();
        assertNotNull(dir);
        assertFalse(dir.isEmpty());
    }

    @Test
    public void testGetLogFileName() {
        LoggerConfig.getLogger(LoggerConfigTest.class);
        String file = LoggerConfig.getLogFileName();
        assertNotNull(file);
        assertFalse(file.isEmpty());
    }

    @Test
    public void testGetMaxFileSize() {
        LoggerConfig.getLogger(LoggerConfigTest.class);
        int size = LoggerConfig.getMaxFileSize();
        assertTrue(size > 0);
    }

    @Test
    public void testGetMaxBackupFiles() {
        LoggerConfig.getLogger(LoggerConfigTest.class);
        int backups = LoggerConfig.getMaxBackupFiles();
        assertTrue(backups >= 0);
    }

    @Test
    public void testGetLogLevel() {
        LoggerConfig.getLogger(LoggerConfigTest.class);
        Level level = LoggerConfig.getLogLevel();
        assertNotNull(level);
    }
}

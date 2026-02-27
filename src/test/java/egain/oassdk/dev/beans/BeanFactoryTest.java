package egain.oassdk.dev.beans;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for BeanFactory (dev).
 */
public class BeanFactoryTest {

    @Test
    public void testGenerateBeansNullOutputDirThrows() {
        BeanFactory generator = new BeanFactory();
        assertThrows(IllegalArgumentException.class, () ->
                generator.generateBeans(Map.of(), null, "com.example.api")
        );
    }
}

package egain.oassdk;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Util} static helper methods.
 */
public class UtilTest {

    @Test
    public void testAsStringObjectMapNull() {
        assertNull(Util.asStringObjectMap(null));
    }

    @Test
    public void testAsStringObjectMapValid() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("a", 1);
        input.put("b", "two");
        Map<String, Object> result = Util.asStringObjectMap(input);
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(1, result.get("a"));
        assertEquals("two", result.get("b"));
        assertNotSame(input, result);
    }

    @Test
    public void testAsStringObjectMapNonMapThrows() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Util.asStringObjectMap("not a map"));
        assertTrue(e.getMessage().contains("Expected a Map"));
    }

    @Test
    public void testAsStringObjectMapNonStringKeyThrows() {
        Map<Object, Object> bad = new LinkedHashMap<>();
        bad.put(123, "value");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Util.asStringObjectMap(bad));
        assertTrue(e.getMessage().contains("Non-String key"));
    }

    @Test
    public void testAsStringObjectMapListNull() {
        assertNull(Util.asStringObjectMapList(null));
    }

    @Test
    public void testAsStringObjectMapListValid() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("k", "v");
        List<Map<String, Object>> input = Arrays.asList(m, null);
        List<Map<String, Object>> result = Util.asStringObjectMapList(input);
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("v", result.get(0).get("k"));
        assertNull(result.get(1));
    }

    @Test
    public void testAsStringObjectMapListNonListThrows() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Util.asStringObjectMapList("not a list"));
        assertTrue(e.getMessage().contains("Expected a List"));
    }

    @Test
    public void testAsStringListNull() {
        assertNull(Util.asStringList(null));
    }

    @Test
    public void testAsStringListValid() {
        List<String> input = Arrays.asList("a", "b", "c");
        List<String> result = Util.asStringList(input);
        assertNotNull(result);
        assertEquals(Arrays.asList("a", "b", "c"), result);
    }

    @Test
    public void testAsStringListWithNulls() {
        List<String> input = Arrays.asList("a", null, "c");
        List<String> result = Util.asStringList(input);
        assertNotNull(result);
        assertEquals("a", result.get(0));
        assertNull(result.get(1));
        assertEquals("c", result.get(2));
    }

    @Test
    public void testAsStringListNonListThrows() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Util.asStringList(Map.of("x", 1)));
        assertTrue(e.getMessage().contains("Expected a List"));
    }

    @Test
    public void testAsStringListNonStringElementThrows() {
        List<Object> input = Arrays.asList("a", 42, "c");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Util.asStringList(input));
        assertTrue(e.getMessage().contains("non-String element"));
    }

    @Test
    public void testAsObjectListNull() {
        assertNull(Util.asObjectList(null));
    }

    @Test
    public void testAsObjectListValid() {
        List<Object> input = Arrays.asList("a", 1, null);
        List<Object> result = Util.asObjectList(input);
        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals("a", result.get(0));
        assertEquals(1, result.get(1));
        assertNull(result.get(2));
    }

    @Test
    public void testAsObjectListNonListThrows() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Util.asObjectList("not a list"));
        assertTrue(e.getMessage().contains("Expected a List"));
    }
}

package de.oliver_heger.mediastore.shared;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

/**
 * Test class for {@code ObjectUtils}.
 *
 * @author Oliver Heger
 * @version $Id: $
 */
public class TestObjectUtils
{
    /**
     * Tests nonNullList() for a defined list.
     */
    @Test
    public void testNonNullListNotNull()
    {
        List<Object> list = new ArrayList<Object>();
        assertSame("Wrong list", list, ObjectUtils.nonNullList(list));
    }

    /**
     * Tests nonNullList() for a null list.
     */
    @Test
    public void testNonNullListNull()
    {
        assertTrue("No empty list", ObjectUtils.nonNullList(null).isEmpty());
    }

    /**
     * Tests nonNullSet() for a defined set.
     */
    @Test
    public void testNonNullSetNotNull()
    {
        Set<Object> set = new HashSet<Object>();
        assertSame("Wrong set", set, ObjectUtils.nonNullSet(set));
    }

    /**
     * Tests nonNullSet() for a null set.
     */
    @Test
    public void testNonNullSetNull()
    {
        assertTrue("No empty set", ObjectUtils.nonNullSet(null).isEmpty());
    }

    /**
     * Tests nonNullMap() for a defined map.
     */
    @Test
    public void testNonNullMapNotNull()
    {
        Map<Object, Object> map =
                Collections.unmodifiableMap(new HashMap<Object, Object>());
        assertSame("Wrong map", map, ObjectUtils.nonNullMap(map));
    }

    /**
     * Tests nonNullMap() for a null map.
     */
    @Test
    public void testNonNullMapNull()
    {
        assertTrue("No empty map", ObjectUtils.nonNullMap(null).isEmpty());
    }

    /**
     * Tests compareTo() if both objects are null.
     */
    @Test
    public void testCompareToNulls()
    {
        Integer v1 = null;
        Integer v2 = null;
        assertEquals("Wrong result", 0, ObjectUtils.compareTo(v1, v2));
    }

    /**
     * Tests compareTo() if one argument is null.
     */
    @Test
    public void testCompareToOneNull()
    {
        assertEquals("Wrong result 1", -1, ObjectUtils.compareTo(null, 1));
        assertEquals("Wrong result 2", 1, ObjectUtils.compareTo(1, null));
    }

    /**
     * Tests compareTo() if the arguments are not null.
     */
    @Test
    public void testCompareToNonNull()
    {
        assertEquals("Wrong result 1", -1, ObjectUtils.compareTo(100, 1000));
        assertEquals("Wrong result 2", 1,
                ObjectUtils.compareTo(20110112223424L, 1L));
    }

    /**
     * Tests the empty string constant.
     */
    @Test
    public void testEmptyString()
    {
        assertEquals("Wrong string length", 0, ObjectUtils.EMPTY.length());
    }

    /**
     * Tests toString() for null input.
     */
    @Test
    public void testToStringNull()
    {
        assertEquals("Wrong result", ObjectUtils.EMPTY,
                ObjectUtils.toString(null));
    }

    /**
     * Tests toString() for non-null input.
     */
    @Test
    public void testToStringNotNull()
    {
        Long obj = 20111218194412L;
        assertEquals("Wrong result", obj.toString(),
                ObjectUtils.toString(obj, "some default"));
    }
}

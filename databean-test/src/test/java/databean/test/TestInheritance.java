package databean.test;

import databean.test.model2.Cat;
import databean.test.model2.Dog;
import databean.test.model2.Felix;
import org.junit.Test;

import static org.junit.Assert.*;

public class TestInheritance {
    @Test(expected = UnsupportedOperationException.class)
    public void testInheritanceReadonlyOverridesMutable() {
        Felix.of().name("Felix1");
    }
    @Test
    public void testInheritanceOverrideMutableOverridesReadonly() {
        final Felix felix = Felix.of().age(7);
        assertEquals(Integer.valueOf(7), felix.age());
    }
    @Test
    public void testReadOnlyDefaultValues() {
        assertFalse(Cat.of().name("Tom").cutie());
        assertTrue(Felix.of().cutie());
        assertFalse(Dog.of().name("Pit").hasHorn());
        assertTrue(Dog.of().name("Pit").hasTail());
    }
    @Test(expected = UnsupportedOperationException.class)
    public void testFixedProperties() {
        final Felix felix = Felix.of();
        // Felix.color is fixed property and can not be changed
        felix.ofColor("black");
    }
}

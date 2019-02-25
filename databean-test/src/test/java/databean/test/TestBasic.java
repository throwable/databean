package databean.test;

import databean.test.model.*;
import databean.test.model2.Cat;
import databean.test.model2.Felix;
import org.junit.Test;

import java.io.Serializable;
import java.util.function.Function;

import static org.junit.Assert.*;

public class TestBasic {
    @Test
    public void testUser() {
        final User pedro = User.of()
                .age(21)
                .name("Pedro")
                .contact(Contact.of()
                        .phone("555123456")
                        .address(Address.of()
                                .city("Madrid")
                                .street("Mayor")
                        )
                );
        assertEquals(21, pedro.age());
        assertEquals("Pedro", pedro.name());
        assertEquals("555123456", pedro.contact().phone());
        assertEquals("Madrid", pedro.contact().address().city());
        // defaults
        assertEquals("unknown", pedro.hobby());     // default value
        assertTrue(pedro.active());         // primitive default value
        // nonnull defaults
        assertEquals("", pedro.comments());

        final IUser silvia = pedro.ofName("Silvia");
        assertNotSame(pedro, silvia);
        assertEquals("Silvia", silvia.name());
        assertEquals(21, silvia.age());
        assertEquals("Madrid", silvia.contact().address().city());

        // automatic creation of @DataClass for no-nnull properties
        final Contact contact = Contact.of();
        assertNotNull(contact.address());

        // nested class generation
        pedro.birthInfo(User.BirthInfo.of(
                User.BirthInfo.Date.of()
                        .year(1987)
                        .month(2)
                        .day(10)
                )
                .place("Coopertown")
        );

        assertEquals(1987, pedro.birthInfo().date().year());
        assertEquals("Coopertown", pedro.birthInfo().place());
    }

    @Test(expected = NullPointerException.class)
    public void testSetNulls1() {
        User.of().age(12)
                .name(null);
    }
    @Test(expected = NullPointerException.class)
    public void testSetNulls2() {
        User.of().age(12)
                .name("Test")
                .comments(null);
    }
    @Test(expected = NullPointerException.class)
    public void testSetNulls3() {
        User.of().age(12)
                .name("Test")
                .ofName(null);
    }
}

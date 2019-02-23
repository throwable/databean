package databean.test;

import databean.test.model.*;
import org.junit.Test;

import java.io.Serializable;
import java.util.function.Function;

import static org.junit.Assert.*;

public class TestUser {
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






    static private <R, V> Path<V> $(Function<R, V> name) {
        return null;
    }

    static class Path<R> {
        <V> Path<V> $(Function<R, V> name) {
            return null;
        }
    }


    public void testMetadata() {
        //$(User::contact).$(Contact::address).$(Address::street);
    }

    interface A0 {
        Object c();
        default int d() {
            return 0;
        }
    }
    interface A extends A0 {
        default int a() {
            return 0;
        }
        Number b();
        Number c();
    }
    interface A1 {
        Integer b();
        Serializable c();
    }
    interface A2 {
        Serializable c();
    }
    interface B extends A, A1, A2 {
        @Override
        default int a() {
            return A.super.a();
        }
        @Override
        Integer b();
        default Integer c() {
            return null;
        }
    }
    public static class C1 implements A {
        @Override
        public Number b() {
            return null;
        }

        @Override
        public Number c() {
            return null;
        }
    }
    public static class C extends C1 implements B {
        @Override
        public int a() {
            return B.super.a();
        }

        @Override
        public Integer b() {
            return null;
        }

        @Override
        public Integer c() {
            return null;
        }
    }
}

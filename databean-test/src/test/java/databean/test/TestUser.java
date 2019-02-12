package databean.test;

import databean.test.model.*;
import org.junit.Test;

import java.util.function.Function;
import java.util.function.Supplier;

public class TestUser {
    //@Test
    public void testUser() {
        final Class<User> userClass = User.class;
        final Class<$User> userMetaClass = $User.class;
        User user = null;
        final Supplier<String> name = user::name;
        final Function<User, String> name1 = User::name;
        $(User::contact).$(Contact::address).$(Address::street);
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
        $User.of()
                .age(12)
                .name("Vasya")
                .contact($Contact.of()
                        .phone("test")
                );
    }
}

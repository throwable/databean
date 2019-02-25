package databean.test;

import java.io.Serializable;
import java.util.function.Function;

public interface UserMeta1 {
    int name();


    /*static private <R, V> Path<V> $(Function<R, V> name) {
        return null;
    }

    static class Path<R> {
        <V> Path<V> $(Function<R, V> name) {
            return null;
        }
    }


    public void testMetadata() {
        //$(User::contact).$(Contact::address).$(Address::street);
    }*/

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

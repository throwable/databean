package databean.test.model;

import databean.DataClass;
import databean.Initial;

@DataClass
public interface User {
    @Initial
    int age();

    @Initial
    String name();

    Contact contact();
}

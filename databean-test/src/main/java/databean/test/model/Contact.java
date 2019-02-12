package databean.test.model;

import databean.DataClass;

@DataClass
public interface Contact {
    String phone();
    Address address();
}

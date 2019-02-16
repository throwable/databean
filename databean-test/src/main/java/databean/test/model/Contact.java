package databean.test.model;

import databean.DataClass;

import javax.annotation.Nonnull;

@DataClass
public interface Contact extends $Contact {
    String phone();

    @Nonnull
    Address address();
}

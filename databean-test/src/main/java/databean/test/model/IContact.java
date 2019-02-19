package databean.test.model;

import databean.DataClass;

import javax.annotation.Nonnull;

@DataClass
public interface IContact {
    String phone();

    @Nonnull
    IAddress address();
}

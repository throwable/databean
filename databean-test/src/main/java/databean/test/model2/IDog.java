package databean.test.model2;

import databean.DataClass;

import javax.annotation.Nonnull;

@DataClass
public interface IDog extends IPet {
    @Override @Nonnull
    default String type() {
        return "Dog";
    }

    @Override
    default String sound() {
        return "Rauff";
    }

    @Nonnull
    String race();
}

package databean.test.model2;

import databean.DataClass;
import databean.ReadOnly;

import javax.annotation.Nonnull;

@DataClass
public interface ICat extends IPet {
    @ReadOnly @Override @Nonnull
    default String type() {
        return "Cat";
    }

    @Override
    default String sound() {
        return "Meow";
    }

    default double weight() {
        return 3.0;
    }

    @ReadOnly @Override
    default Integer age() {
        return 1;
    }
}

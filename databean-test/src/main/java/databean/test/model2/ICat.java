package databean.test.model2;

import databean.DataClass;
import databean.Fixed;
import databean.ReadOnly;

import javax.annotation.Nonnull;

@DataClass
public interface ICat extends IPet {
    @Fixed @Override @Nonnull
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

    // allow default values for read-only method
    @ReadOnly
    boolean cutie();

    @ReadOnly
    String color();
}

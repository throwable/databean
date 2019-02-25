package databean.test.model2;

import databean.DataClass;
import databean.DefaultValue;
import databean.Fixed;
import databean.ReadOnly;

import javax.annotation.Nonnull;

@DataClass
public interface IDog extends IPet {
    @Fixed @Override @Nonnull
    default String type() {
        return "Dog";
    }

    @Override
    default String sound() {
        return "Rauff";
    }

    @Nonnull
    String race();

    // default value generation: always false
    @ReadOnly @Nonnull
    Boolean hasHorn();

    @ReadOnly @DefaultValue("true")
    boolean hasTail();
}

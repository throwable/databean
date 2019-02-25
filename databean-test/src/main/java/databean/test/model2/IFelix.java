package databean.test.model2;

import databean.DataClass;
import databean.DefaultValue;
import databean.Fixed;
import databean.ReadOnly;

import javax.annotation.Nonnull;

@DataClass
public interface IFelix extends ICat {
    @Fixed @Nonnull @Override
    default String name() {
        return "Felix";
    }

    @ReadOnly @Override
    default String sound() {
        return "Murrraaayy";
    }

    @ReadOnly @Override
    default double weight() {
        return 5.5;
    }

    // read-write property
    @Override
    default Integer age() {
        return 5;
    }

    @Override @DefaultValue("true")
    boolean cutie();

    @Fixed @Override @Nonnull @DefaultValue("\"ginger\"")
    String color();
}

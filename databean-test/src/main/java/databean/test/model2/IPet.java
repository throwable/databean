package databean.test.model2;

import databean.DataClass;
import databean.DefaultValue;
import databean.Initial;
import databean.ReadOnly;

import javax.annotation.Nonnull;

@DataClass
public interface IPet {
    @Initial @Nonnull
    String type();

    @Initial @Nonnull
    String name();

    @ReadOnly @DefaultValue("0")
    Integer age();

    String sound();
}

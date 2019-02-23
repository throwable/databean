package databean.test.model1;

import databean.DataClass;
import databean.Initial;
import databean.ReadOnly;

@DataClass
public interface IDimension {
    @Initial @ReadOnly
    int width();
    @Initial @ReadOnly
    int height();
}

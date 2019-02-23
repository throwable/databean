package databean.test.model1;

import databean.DataClass;
import databean.Initial;
import databean.ReadOnly;

@DataClass
public interface IPoint {
    @Initial @ReadOnly
    int x();
    @Initial @ReadOnly
    int y();
}

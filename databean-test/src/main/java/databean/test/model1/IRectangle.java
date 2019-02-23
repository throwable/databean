package databean.test.model1;

import databean.DataClass;
import databean.Initial;
import databean.ReadOnly;

@DataClass
public interface IRectangle {
    @Initial @ReadOnly
    IPoint point();
    @Initial @ReadOnly
    IDimension size();

    static IRectangle of(int x, int y, int w, int h) {
        return Rectangle.of(Point.of(x, y), Dimension.of(w, h));
    }
}

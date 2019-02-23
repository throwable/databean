package databean.ap;

import javax.annotation.Nullable;

public class BeanPropertyInfo {
    public final DataClassInfo.Property property;
    @Nullable
    public final DataClassInfo.Property beanSuperclassProperty;
    public final boolean readOnlyOverridesMutable;

    public BeanPropertyInfo(DataClassInfo.Property property) {
        this(property, null, false);
    }

    public BeanPropertyInfo(DataClassInfo.Property property, DataClassInfo.Property beanSuperclassProperty, boolean readOnlyOverridesMutable) {
        this.property = property;
        this.beanSuperclassProperty = beanSuperclassProperty;
        this.readOnlyOverridesMutable = readOnlyOverridesMutable;
    }
}

package databean.ap;

import com.squareup.javapoet.ClassName;

import javax.lang.model.type.TypeMirror;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DataClassInfo {
    public static class Property {
        public final String name;
        public final TypeMirror type;
        public final boolean isInitial;
        public final boolean hasDefaultValue;
        public final boolean isComputed;
        public final boolean isReadOnly;

        public Property(String name, TypeMirror type, boolean isInitial, boolean isReadOnly,
                        boolean hasDefaultValue, boolean isComputed)
        {
            this.name = name;

            /** Preconditions */
            if (isInitial) {
                check(hasDefaultValue, false, "initial property must not have default value");
                check(isComputed, false, "initial property must be computed");
            }
            if (hasDefaultValue) {
                check(isComputed, true, "computed property must not have default value");
            }
            if (isComputed) {
                check(isReadOnly, true, "computed property are read only");
            }
            if (isReadOnly) {
                check(isComputed || hasDefaultValue || isInitial, true,
                        "read-only property must be computed, initial or with default value");
            }

            this.type = type;
            this.isInitial = isInitial;
            this.isReadOnly = isReadOnly;
            this.hasDefaultValue = hasDefaultValue;
            this.isComputed = isComputed;
        }

        private void check(boolean value, boolean control, String message) {
            if (value != control)
                throw new IllegalArgumentException("error in property definition: " + name + ": " + message);
        }
    }

    public final String packageName;
    public final String className;
    public final String metaClassName;

    public final Map<String, Property> properties;
    public final boolean mutable;

    public DataClassInfo(String packageName, String className, List<Property> properties, boolean mutable) {
        this.packageName = packageName;
        this.className = className;
        this.properties = new LinkedHashMap<>();
        properties.forEach(it -> {
            this.properties.put(it.name, it);
        });
        this.mutable = mutable;
        this.metaClassName = metaClassName(className);
    }


    public ClassName className() {
        return ClassName.get(packageName, className);
    }

    public ClassName metaClassName() {
        return ClassName.get(packageName, metaClassName);
    }

    public static String metaClassName(String className) {
        return "M" + className;
    }

    public static String beanClassName(String className) {
        return className + "Bean";
    }
}

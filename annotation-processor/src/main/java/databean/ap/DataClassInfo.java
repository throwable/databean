package databean.ap;

import com.squareup.javapoet.ClassName;

import javax.annotation.Nullable;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.type.TypeMirror;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DataClassInfo {
    public static class Property {
        public final String name;
        public final boolean isBeanNameDeclaration;
        public final TypeMirror type;
        public final boolean isInitial;
        /* has default value set by default method */
        public final boolean hasDefaultValue;
        public final boolean isComputed;
        public final boolean isReadOnly;
        @Nullable
        public final AnnotationMirror notNullAnnotation;

        public Property(String name, boolean isBeanNameDeclaration, TypeMirror type, boolean isInitial, boolean isReadOnly,
                        boolean hasDefaultValue, boolean isComputed, @Nullable AnnotationMirror notNullAnnotation)
        {
            this.name = name;
            this.isBeanNameDeclaration = isBeanNameDeclaration;
            this.notNullAnnotation = notNullAnnotation;

            /** Preconditions */
            if (isInitial) {
                check(hasDefaultValue, false, "initial property must not have default value");
                check(isComputed, false, "initial property must be computed");
            }
            if (hasDefaultValue) {
                check(isComputed, false, "computed property must not have default value");
            }
            if (isComputed) {
                check(isReadOnly, false, "computed property are read only");
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

        public String defaultValueGetterName() {
            return isBeanNameDeclaration ? BeanGenerator.getterName(type, name) : name;
        }
    }

    public final String packageName;
    public final String className;
    public final String metaClassName;
    public final String beanClassName;

    public final Map<String, Property> properties;
    public final boolean generateBeanAccessors;
    public final boolean mutable;

    public DataClassInfo(String packageName, String className, String metaClassName, List<Property> properties,
                         boolean generateBeanAccessors, boolean mutable) {
        this.packageName = packageName;
        this.className = className;
        this.metaClassName = metaClassName;
        this.generateBeanAccessors = generateBeanAccessors;
        this.properties = new LinkedHashMap<>();
        properties.forEach(it -> {
            this.properties.put(it.name, it);
        });
        this.mutable = mutable;
        //this.metaClassName = metaClassName(className);
        this.beanClassName = beanClassName(className);
    }


    public boolean hasDefaultConstructor() {
        return this.properties.values().stream().noneMatch(it -> it.isInitial);
    }


    public ClassName className() {
        return ClassName.get(packageName, className);
    }

    public ClassName metaClassName() {
        return ClassName.get(packageName, metaClassName);
    }

    public ClassName beanClassName() {
        return ClassName.get(packageName, beanClassName);
    }

    /*public static String metaClassName(String className) {
        return "M" + className;
    }*/

    public static String beanClassName(String className) {
        return className + "Bean";
    }
}

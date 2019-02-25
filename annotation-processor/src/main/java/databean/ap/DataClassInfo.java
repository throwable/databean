package databean.ap;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;

import javax.annotation.Nullable;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;
import java.util.List;
import java.util.Objects;

public class DataClassInfo {
    public static class Property {
        public final String name;
        public final boolean isBeanNameDeclaration;
        public final TypeMirror type;
        public final boolean isDataClass;
        public final boolean isInitial;
        /* has default value set by default method */
        public final boolean hasDefaultValue;
        @Nullable
        public final String defaultValueExpression;
        public final boolean isComputed;
        public final boolean isReadOnly;
        public final boolean isFixed;
        @Nullable
        public final AnnotationMirror notNullAnnotation;

        public Property(String name, boolean isBeanNameDeclaration, TypeMirror type, boolean isDataClass, boolean isInitial, boolean isReadOnly,
                        boolean hasDefaultValue, @Nullable String defaultValueExpression, boolean isComputed, boolean isFixed, @Nullable AnnotationMirror notNullAnnotation)
        {
            this.name = name;
            this.isBeanNameDeclaration = isBeanNameDeclaration;
            this.isDataClass = isDataClass;
            this.defaultValueExpression = defaultValueExpression;
            this.isFixed = isFixed;
            this.notNullAnnotation = notNullAnnotation;

            /*   Preconditions */
            if (isInitial) {
                check(hasDefaultValue, false, "initial property must not have default value");
                check(isComputed, false, "initial property must not be computed");
            }
            if (hasDefaultValue) {
                check(isComputed, false, "computed property must not have default value");
            }
            if (isComputed) {
                check(isReadOnly, true, "computed property are read only");
            }
            /*if (isReadOnly) {
                check(isComputed || hasDefaultValue || isInitial, true,
                        "read-only property must be initial or with default value defined");
            }*/
            if (isFixed) {
                check(isReadOnly, true, "fixed property must be read-only");
                check(isInitial, false, "fixed property must not be read-only");
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

        public TypeName typeName() {
            String rawString = type.toString();
            if (type.getKind().isPrimitive())
                return TypeName.get(type);
            int dotPosition = rawString.lastIndexOf(".");
            String packageName = rawString.substring(0, dotPosition);
            String className = rawString.substring(dotPosition + 1);
            return ClassName.get(packageName, className);
        }

        public String getterName() {
            return (type.getKind().isPrimitive() && "boolean".equals(type.toString()) ?
                    "is" : "get") + BeanGenerator.capitalize(name);
        }

        public String setterName() {
            return "set" + BeanGenerator.capitalize(name);
        }

        public String writeAccessorName() {
            return isBeanNameDeclaration ? setterName() : name;
        }

        public String readAccessorName() {
            return isBeanNameDeclaration ? getterName() : name;
        }

        /*public String defaultValueGetterName() {
            return isBeanNameDeclaration ? getterName() : name;
        }*/

        public boolean declaredWithDefaultMethod() {
            return isComputed || hasDefaultValue && (defaultValueExpression == null);
        }

        public Property withDefaults(boolean hasDefaultValue, String defaultValueExpression) {
            return new Property(name, isBeanNameDeclaration, type, isDataClass, isInitial, isReadOnly,
                    hasDefaultValue, defaultValueExpression, isComputed, isFixed, notNullAnnotation);
        }

        public Property withInitial(boolean isInitial) {
            return new Property(name, isBeanNameDeclaration, type, isDataClass, isInitial, isReadOnly,
                    hasDefaultValue, defaultValueExpression, isComputed, isFixed, notNullAnnotation);
        }

        public Property withReadOnly(boolean isReadOnly) {
            return new Property(name, isBeanNameDeclaration, type, isDataClass, isInitial, isReadOnly,
                    hasDefaultValue, defaultValueExpression, isComputed, isFixed, notNullAnnotation);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Property property = (Property) o;

            if (isBeanNameDeclaration != property.isBeanNameDeclaration) return false;
            if (isDataClass != property.isDataClass) return false;
            if (isInitial != property.isInitial) return false;
            if (hasDefaultValue != property.hasDefaultValue) return false;
            if (isComputed != property.isComputed) return false;
            if (isReadOnly != property.isReadOnly) return false;
            if (isFixed != property.isFixed) return false;
            if (!name.equals(property.name)) return false;
            if (!type.equals(property.type)) return false;
            if (!Objects.equals(defaultValueExpression, property.defaultValueExpression))
                return false;
            return Objects.equals(notNullAnnotation, property.notNullAnnotation);
        }

        @Override
        public int hashCode() {
            int result = name.hashCode();
            result = 31 * result + (isBeanNameDeclaration ? 1 : 0);
            result = 31 * result + type.hashCode();
            result = 31 * result + (isDataClass ? 1 : 0);
            result = 31 * result + (isInitial ? 1 : 0);
            result = 31 * result + (hasDefaultValue ? 1 : 0);
            result = 31 * result + (defaultValueExpression != null ? defaultValueExpression.hashCode() : 0);
            result = 31 * result + (isComputed ? 1 : 0);
            result = 31 * result + (isReadOnly ? 1 : 0);
            result = 31 * result + (isFixed ? 1 : 0);
            result = 31 * result + (notNullAnnotation != null ? notNullAnnotation.hashCode() : 0);
            return result;
        }
    }

    @Nullable
    public final DataClassInfo enclosingClass;
    public final TypeMirror enclosingType;
    public final TypeMirror classType;
    public final String metaClassSimpleName;
    public final String beanClassName;

    public final List<Property> properties;
    public final boolean generateBeanAccessors;
    public final boolean inheritFromSuperclass;
    public final List<ExecutableElement> customConstructors;

    public final List<DataClassInfo> superClasses;


    public DataClassInfo(@Nullable DataClassInfo enclosingClass, TypeMirror enclosingType,
                         TypeMirror classType, String metaClassSimpleName, List<Property> properties,
                         boolean generateBeanAccessors, boolean inheritFromSuperclass,
                         List<ExecutableElement> customConstructors, List<DataClassInfo> superClasses)
    {
        this.enclosingType = enclosingType;
        this.classType = classType;
        this.metaClassSimpleName = metaClassSimpleName;
        this.enclosingClass = enclosingClass;
        this.generateBeanAccessors = generateBeanAccessors;
        this.inheritFromSuperclass = inheritFromSuperclass;
        this.superClasses = superClasses;
        this.properties = properties;
        //this.metaClassName = metaClassName(className);
        this.beanClassName = beanClassName(className().simpleName());
        this.customConstructors = customConstructors;
    }


    public boolean hasDefaultConstructor() {
        return this.properties.stream().noneMatch(it -> it.isInitial);
    }

    public ClassName className() {
        return (ClassName) ClassName.get(classType);
    }

    public ClassName metaClassName() {
        if (enclosingClass != null)
            return enclosingClass.metaClassName().nestedClass(metaClassSimpleName);
        else
            return ClassName.get(className().packageName(), metaClassSimpleName);
    }

    public String packageName() {
        if (enclosingClass != null) return enclosingClass.packageName();
        else return enclosingType.toString();
    }

    public ClassName beanClassName() {
        if (enclosingClass != null)
            return enclosingClass.beanClassName().nestedClass(beanClassName);
        else
            return ClassName.get(packageName(), beanClassName);
    }

    public static String metaClassName(String className) {
        if (className.startsWith("I") && className.length() > 2 && Character.isUpperCase(className.charAt(1)))
            // IUser -> User
            return className.substring(1);
        else
            // User -> MUser
            return "M" + className;
    }

    public static String beanClassName(String className) {
        return className.substring(1) + "Bean";
        /*if (className.startsWith("I") && className.length() > 2 && Character.isUpperCase(className.charAt(1)))
            // IUser -> User
            ;
        else return className + "Bean";*/
    }
}

package databean.ap;

import databean.*;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BeanMetadataResolver {
    private final ProcessingEnvironment procEnv;

    private final Map<String, DataClassInfo> cache = new HashMap<>();

    public BeanMetadataResolver(ProcessingEnvironment procEnv) {
        this.procEnv = procEnv;
    }


    public DataClassInfo resolve(TypeElement element)
    {
        DataClassInfo dataClassInfo = cache.get(element.getQualifiedName().toString());
        if (dataClassInfo != null)
            return dataClassInfo;

        final String className = element.getSimpleName().toString();
        final TypeMirror enclosingType = element.getEnclosingElement().asType();

        final String metaClassName = DataClassInfo.metaClassName(className);

        if (enclosingType.getKind() != TypeKind.PACKAGE) {
            if (element.getEnclosingElement().getAnnotation(DataClass.class) == null)
                throw new RuntimeException("Interface '" + className + "' can be nested only in other @DataClass");
        }

        final DataClass dataClassAnno = element.getAnnotation(DataClass.class);
        final boolean generateBeanAccessors = dataClassAnno.generateBeanAccessors();
        final List<ExecutableElement> customConstructors = new ArrayList<>();

        // Properties with the same name are allowed but must have compatible types.
        // The most narrow type is choosed as a resulting property's type
        final List<DataClassInfo> superClasses = new ArrayList<>();

        for (TypeMirror superInterface : element.getInterfaces()) {
            final Element superInterfaceEl = procEnv.getTypeUtils().asElement(superInterface);
            if (superInterfaceEl.getAnnotation(DataClass.class) != null) {
                final DataClassInfo superDataClass = resolve((TypeElement) superInterfaceEl);
                superClasses.add(superDataClass);
            }
        }

        final List<DataClassInfo.Property> properties = resolveElementProperties(element);

        for (Element enclosedElement : element.getEnclosedElements()) {
            if (enclosedElement instanceof ExecutableElement) {
                final ExecutableElement executableElement = (ExecutableElement) enclosedElement;
                if (executableElement.getModifiers().contains(Modifier.STATIC)) {
                    if ("of".equals(executableElement.getSimpleName().toString())) {
                        // secondary constructor
                        customConstructors.add(executableElement);
                    }
                }
            }
        }

        final DataClassInfo enclosingClass;
        //final String metaClassSimpleName;
        if (enclosingType.getKind() != TypeKind.PACKAGE) {
            enclosingClass = resolve((TypeElement) element.getEnclosingElement());
        } else {
            enclosingClass = null;
        }

        dataClassInfo = new DataClassInfo(enclosingClass, enclosingType, element.asType(), metaClassName,
                properties, generateBeanAccessors, dataClassAnno.inheritFromSuperclass(), customConstructors, superClasses);
        cache.put(element.getQualifiedName().toString(), dataClassInfo);
        return dataClassInfo;
    }




    private List<DataClassInfo.Property> resolveElementProperties(Element element) {
        final ArrayList<DataClassInfo.Property> properties = new ArrayList<>();
        final String className = element.getSimpleName().toString();

        for (Element enclosedElement : element.getEnclosedElements()) {
            if (enclosedElement instanceof ExecutableElement) {
                final ExecutableElement executableElement = (ExecutableElement) enclosedElement;
                if (executableElement.getModifiers().contains(Modifier.STATIC))
                    continue;
                final String methodName = executableElement.getSimpleName().toString();
                final TypeMirror returnType = executableElement.getReturnType();
                final String propertyName = getPropertyNameFromDeclaration(methodName, returnType);
                final boolean beanNameDeclaration = !propertyName.equals(methodName);

                boolean computed = executableElement.getAnnotation(Computed.class) != null;
                boolean initial = executableElement.getAnnotation(Initial.class) != null;
                boolean readonly = computed || executableElement.getAnnotation(ReadOnly.class) != null;
                String defaultValueExpression = executableElement.getAnnotation(DefaultValue.class) != null ?
                        executableElement.getAnnotation(DefaultValue.class).value() : "";
                boolean hasDefaultValue = (executableElement.isDefault() || !defaultValueExpression.isEmpty())
                        && !computed;

                if (!executableElement.isDefault()) {
                    if (!executableElement.getParameters().isEmpty())
                        throw new RuntimeException("Dataclass interface method must not have parameters: " +
                                className + "." + propertyName);
                    if (!executableElement.getThrownTypes().isEmpty())
                        throw new RuntimeException("Dataclass interface method must not declare any checked exception: " +
                                className + "." + propertyName);
                    if ("void".equals(executableElement.getReturnType().toString()))
                        throw new RuntimeException("Dataclass interface method return value can not be void: " +
                                className + "." + propertyName);
                } else {
                    // default method: detect if it is a property or a business method
                    if (!defaultValueExpression.isEmpty())
                        throw new RuntimeException("Dataclass property must declare it's default value either " +
                                "via default methos or @DefaultValue anotation but not both: " +
                                className + "." + propertyName);
                    if (!executableElement.getParameters().isEmpty() ||
                            !executableElement.getThrownTypes().isEmpty() ||
                            "void".equals(executableElement.getReturnType().toString()) ||
                            executableElement.getAnnotation(BusinessMethod.class) != null)
                    {
                        // business method (not a property)
                        continue;
                    }
                }

                AnnotationMirror notNullAnnotation = executableElement.getAnnotationMirrors().stream()
                        .filter(it -> {
                            final Name name = it.getAnnotationType().asElement().getSimpleName();
                            return name.toString().equalsIgnoreCase("NotNull") ||
                                    name.toString().equalsIgnoreCase("NonNull");
                        })
                        .findAny().orElse(null);

                final TypeElement typeElement = procEnv.getElementUtils().getTypeElement(returnType.toString());
                final boolean isDataClass = typeElement != null && typeElement.getAnnotation(DataClass.class) != null;

                try {
                    properties.add(new DataClassInfo.Property(propertyName, beanNameDeclaration, returnType, isDataClass, initial, readonly,
                            hasDefaultValue, defaultValueExpression.isEmpty() ? null : defaultValueExpression,
                            computed, notNullAnnotation));
                } catch (IllegalArgumentException e) {
                    throw new RuntimeException("DataClass property definition error " + className + "." + propertyName + ": " + e.getMessage(), e);
                }
            }
        }
        return properties;
    }


    private static String getPropertyNameFromDeclaration(String methodName, TypeMirror type) {
        if (type.getKind().isPrimitive() && "boolean".equals(type.toString())) {
            if (methodName.startsWith("is") && methodName.length() > 2 &&
                    Character.isUpperCase(methodName.charAt(2)))
                return Character.toLowerCase(methodName.charAt(2)) + methodName.substring(3);
        } else {
            if (methodName.startsWith("get") && methodName.length() > 3 &&
                    Character.isUpperCase(methodName.charAt(3)))
                return Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
        }
        return methodName;
    }
}

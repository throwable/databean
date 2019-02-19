package databean.ap;

import com.squareup.javapoet.ClassName;
import databean.BusinessMethod;
import databean.Computed;
import databean.DataClass;
import databean.Initial;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class BeanMetadataResolver {
    private final ProcessingEnvironment procEnv;

    public BeanMetadataResolver(ProcessingEnvironment procEnv) {
        this.procEnv = procEnv;
    }

    /**
     * TODO: cache resolved results
     */
    public DataClassInfo resolve(TypeElement element) {
        final String className = element.getSimpleName().toString();
        //final String packageName = procEnv.getElementUtils().getPackageOf(element).getQualifiedName().toString();
        final TypeMirror parentType = element.getEnclosingElement().asType();

        if (element.getInterfaces().isEmpty())
            throw new RuntimeException("Interface '" + className +"' must declare it's metaClass as a first element in 'extends' section: " +
                    "interface MyEntity extends MetaMyEntity...");
        final TypeMirror metaClassType = element.getInterfaces().get(0);
        final String metaClassName = metaClassType.toString();
        if (procEnv.getElementUtils().getTypeElement(metaClassName) != null)
            throw new RuntimeException("Interface '" + className +"': unable to generate metaClass: " +
                    "a class with name '" + metaClassType + "' already exists");
        /*if (metaClassType.contains(".")) {
            throw new RuntimeException("Interface '" + className +"' must declare it's metaClass within the same package");
        }*/
        if (parentType.getKind() != TypeKind.PACKAGE) {
            if (element.getEnclosingElement().getAnnotation(DataClass.class) == null)
                throw new RuntimeException("Interface '" + className + "' can be nested only in other @DataClass");
        }

        final DataClass dataClassAnno = element.getAnnotation(DataClass.class);
        final boolean mutable = dataClassAnno.mutable();
        final boolean generateBeanAccessors = dataClassAnno.generateBeanAccessors();
        final List<ExecutableElement> customConstructors = new ArrayList<>();

        final ArrayList<DataClassInfo.Property> properties = new ArrayList<>();

        for (Element enclosedElement : element.getEnclosedElements()) {
            if (enclosedElement instanceof ExecutableElement) {
                final ExecutableElement executableElement = (ExecutableElement) enclosedElement;
                final String methodName = executableElement.getSimpleName().toString();
                final TypeMirror returnType = executableElement.getReturnType();
                final String propertyName = getPropertyNameFromDeclaration(methodName, returnType);
                final boolean beanNameDeclaration = !propertyName.equals(methodName);

                boolean computed = executableElement.getAnnotation(Computed.class) != null;
                boolean initial = executableElement.getAnnotation(Initial.class) != null;
                boolean readonly = computed || initial && !executableElement.getAnnotation(Initial.class).mutable();
                boolean defaultValue = executableElement.isDefault() && !computed;


                if (executableElement.getModifiers().contains(Modifier.STATIC)) {
                    if ("of".equals(executableElement.getSimpleName().toString())) {
                        // secondary constructor
                        customConstructors.add(executableElement);
                    }
                    continue;
                }

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
                    if (!executableElement.getParameters().isEmpty() ||
                            !executableElement.getThrownTypes().isEmpty() ||
                            "void".equals(executableElement.getReturnType().toString()) ||
                            executableElement.getAnnotation(BusinessMethod.class) != null)
                    {
                        // business method (not a property)
                        continue;
                    }
                }

                /*final List<? extends AnnotationMirror> annotationMirrors = executableElement.getAnnotationMirrors();
                if (!annotationMirrors.isEmpty()) {
                    final AnnotationMirror annotationMirror = annotationMirrors.get(0);
                    final DeclaredType annotationType = annotationMirror.getAnnotationType();
                    final Element element1 = annotationType.asElement();
                    final Name simpleName = element1.getSimpleName();
                    final ElementKind kind = element1.getKind();
                    final TypeMirror enclosingType = annotationType.getEnclosingType();
                    final String name = enclosingType.getKind().name();
                }*/

                AnnotationMirror notNullAnnotation = executableElement.getAnnotationMirrors().stream()
                        .filter(it -> {
                            final Name name = it.getAnnotationType().asElement().getSimpleName();
                            return name.toString().equalsIgnoreCase("NotNull") ||
                                    name.toString().equalsIgnoreCase("NonNull");
                        })
                        .findAny().orElse(null);
                try {
                    properties.add(new DataClassInfo.Property(propertyName, beanNameDeclaration, returnType, initial, readonly,
                            defaultValue, computed, notNullAnnotation));
                } catch (IllegalArgumentException e) {
                    throw new RuntimeException("DataClass property definition error: " + e.getMessage());
                }
            }
        }

        final DataClassInfo parent;
        final String metaClassSimpleName;
        if (parentType.getKind() != TypeKind.PACKAGE) {
            parent = resolve((TypeElement) element.getEnclosingElement());
            metaClassSimpleName = metaClassName.substring(metaClassName.lastIndexOf('.') + 1);
            final ClassName parentMetaClassName = parent.metaClassName();
            final String parentMetaClassNameNoPackage = parentMetaClassName.toString().substring(parent.packageName().length() + 1);
            final String requiredMetaClassName = parentMetaClassNameNoPackage + "." + metaClassSimpleName;
            if (!requiredMetaClassName.equals(metaClassName))
                throw new RuntimeException("Dataclass '" + className + "' must extends nested metaClass: '" + requiredMetaClassName + "'");
        } else {
            parent = null;
            metaClassSimpleName = metaClassName;
        }

        return new DataClassInfo(parent, parentType, element.asType(), metaClassSimpleName,
                properties, generateBeanAccessors, mutable, customConstructors);
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

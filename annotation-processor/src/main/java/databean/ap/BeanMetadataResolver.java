package databean.ap;

import databean.BusinessMethod;
import databean.Computed;
import databean.DataClass;
import databean.Initial;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;

public class BeanMetadataResolver {
    private final ProcessingEnvironment procEnv;

    public BeanMetadataResolver(ProcessingEnvironment procEnv) {
        this.procEnv = procEnv;
    }

    public DataClassInfo resolve(TypeElement element) {
        final String className = element.getSimpleName().toString();
        final String packageName = procEnv.getElementUtils().getPackageOf(element).getQualifiedName().toString();
        final String metaClassName = DataClassInfo.metaClassName(className);
        final boolean mutable = element.getAnnotation(DataClass.class).mutable();

        /* Do not check this
        if (element.getInterfaces().stream()
                .noneMatch(it -> it.toString().equals(metaClassName)))
            throw new RuntimeException("Interface " + className +" must extend " + metaClassName);
        */

        final ArrayList<DataClassInfo.Property> metaProperties = new ArrayList<>();

        for (Element enclosedElement : element.getEnclosedElements()) {

            if (enclosedElement instanceof ExecutableElement) {
                final ExecutableElement executableElement = (ExecutableElement) enclosedElement;
                final String propertyName = executableElement.getSimpleName().toString();
                final TypeMirror returnType = executableElement.getReturnType();

                boolean computed = executableElement.getAnnotation(Computed.class) != null;
                boolean initial = executableElement.getAnnotation(Initial.class) != null;
                boolean readonly = computed || initial && !executableElement.getAnnotation(Initial.class).mutable();
                boolean defaultValue = executableElement.isDefault() && !computed;

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
                    if (executableElement.getParameters().isEmpty() ||
                            executableElement.getThrownTypes().isEmpty() ||
                            "void".equals(executableElement.getReturnType().toString()) ||
                            executableElement.getAnnotation(BusinessMethod.class) != null)
                    {
                        // business method (not a property)
                        continue;
                    }
                }
                try {
                    metaProperties.add(new DataClassInfo.Property(propertyName, returnType, initial, readonly,
                            defaultValue, computed));
                } catch (IllegalArgumentException e) {
                    throw new RuntimeException("DataClass property definition error: " + e.getMessage());
                }
            }
        }

        return new DataClassInfo(packageName, className, metaProperties, mutable);
    }
}

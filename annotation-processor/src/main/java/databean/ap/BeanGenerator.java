package databean.ap;

import com.squareup.javapoet.*;
import databean.MetaClass;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class BeanGenerator {
    public static final String INIT_METHOD_NAME = "of";

    private final ProcessingEnvironment procEnv;
    private final Map<TypeName, DataClassInfo> dataBeans;

    public BeanGenerator(ProcessingEnvironment procEnv, Map<TypeName, DataClassInfo> dataBeans) {
        this.procEnv = procEnv;
        this.dataBeans = dataBeans;
    }

    public void generate(DataClassInfo dataClassInfo) {
        try {
            writeMutator(dataClassInfo);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }


    private void writeMutator(DataClassInfo dataClassInfo) throws IOException
    {
        writeDataBeanImpl(dataClassInfo);

        TypeSpec.Builder mutableDataClass = TypeSpec
                .interfaceBuilder(dataClassInfo.metaClassName())
                .addSuperinterface(dataClassInfo.className())
                .addSuperinterface(ParameterizedTypeName.get(
                        ClassName.get(MetaClass.class),
                        dataClassInfo.className()))
                .addModifiers(Modifier.PUBLIC/*, Modifier.FINAL*/);

        generateInitializer(mutableDataClass, dataClassInfo);

        for (DataClassInfo.Property property : dataClassInfo.properties.values()) {
            final TypeName typeName = getTypeName(property.type);
            final TypeName mutableTypeName = resolveMutableReturnType(typeName);

            if (!property.isReadOnly) {
                mutableDataClass.addMethod(MethodSpec
                        .methodBuilder(property.name)
                        .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                        .returns(dataClassInfo.metaClassName())
                        .addParameter(typeName, property.name)
                        .build());
            } else {
                mutableDataClass.addMethod(MethodSpec
                        .methodBuilder("of" + capitalize(property.name))
                        .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                        .returns(dataClassInfo.metaClassName())
                        .addParameter(typeName, property.name)
                        .build());
            }

            if (!typeName.equals(mutableTypeName)) {
                // DataClass: override getters with mutable class
                mutableDataClass.addMethod(MethodSpec
                        .methodBuilder(property.name)
                        .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                        .addAnnotation(Override.class)
                        .returns(mutableTypeName)
                        .build());
            }
        }

        final JavaFile javaFile = JavaFile.builder(dataClassInfo.packageName, mutableDataClass.build()).build();
        javaFile.writeTo(procEnv.getFiler());
    }


    private void writeDataBeanImpl(DataClassInfo dataClassInfo) throws IOException
    {
        final ClassName beanClassName = ClassName.get(dataClassInfo.packageName,
                DataClassInfo.beanClassName(dataClassInfo.className));
        TypeSpec.Builder beanClass = TypeSpec
                .classBuilder(beanClassName)
                .addSuperinterface(dataClassInfo.metaClassName())
                .addSuperinterface(Cloneable.class)
                .addModifiers(Modifier.PUBLIC/*, Modifier.FINAL*/);

        final MethodSpec.Builder constructorBuilder = MethodSpec
                .constructorBuilder()
                .addModifiers(Modifier.PUBLIC);

        // init method
        for (DataClassInfo.Property property : dataClassInfo.properties.values()) {
            // native getter
            final TypeName mutableTypeName = resolveMutableReturnType(getTypeName(property.type));
            beanClass.addMethod(MethodSpec
                    .methodBuilder(property.name)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(Override.class)
                    .addStatement("return this.$N", property.name)
                    .returns(mutableTypeName)
                    .build());

            // add bean getter
            beanClass.addMethod(MethodSpec
                    .methodBuilder(getterName(property.type, property.name))
                    .addModifiers(Modifier.PUBLIC)
                    .addStatement("return $N()", property.name)
                    .returns(getTypeName(property.type))
                    .build());

            if (property.isComputed)
                continue;

            beanClass.addField(mutableTypeName, property.name, Modifier.PRIVATE);

            if (property.isInitial) {
                constructorBuilder.addParameter(getTypeName(property.type), property.name);
                constructorBuilder.addStatement("this.$N = $N", property.name, property.name);
            }

            if (!property.isReadOnly) {
                // native setter
                beanClass.addMethod(MethodSpec
                        .methodBuilder(property.name)
                        .addAnnotation(Override.class)
                        .addModifiers(Modifier.PUBLIC)
                        .returns(dataClassInfo.metaClassName())
                        .addParameter(getTypeName(property.type), property.name)
                        // TODO: smart casting:
                        //  when param is instance of MetaClass instanceof simple assign
                        //  else cast to immutable metaclass using reflection
                        .addStatement("this.$N = ($T) $N", property.name, mutableTypeName, property.name)
                        .addStatement("return this")
                        .build());

                // add bean setter
                beanClass.addMethod(MethodSpec
                        .methodBuilder(setterName(property.name))
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(getTypeName(property.type), property.name)
                        .addStatement("$N($N)", property.name, property.name)
                        .build());
            } else {
                // native copy-setter
                beanClass.addMethod(MethodSpec
                        .methodBuilder("of" + capitalize(property.name))
                        .addAnnotation(Override.class)
                        .addModifiers(Modifier.PUBLIC)
                        .returns(dataClassInfo.metaClassName())
                        .addParameter(getTypeName(property.type), property.name)
                        .beginControlFlow("try")
                        .addStatement("$T cloned = ($T) clone()", beanClassName, beanClassName)
                        .addStatement("cloned.$N = $N", property.name, property.name)
                        .addStatement("return cloned")
                        .nextControlFlow("catch (CloneNotSupportedException e)")
                        //.endControlFlow()
                        .addStatement("throw new RuntimeException(e)")
                        .endControlFlow()
                        .build());
            }
        }

        // init method
        for (DataClassInfo.Property property : dataClassInfo.properties.values()) {
            if (property.hasDefaultValue) {
                constructorBuilder.addStatement("this.$N = super.$N()", property.name, property.name);
            }
        }

        beanClass.addMethod(constructorBuilder.build());

        JavaFile.builder(dataClassInfo.packageName, beanClass.build()).build()
                .writeTo(procEnv.getFiler());
    }


    private void generateInitializer(TypeSpec.Builder metadataClass, DataClassInfo dataClassInfo) {
        final List<DataClassInfo.Property> initProperties = dataClassInfo.properties.values().stream()
                .filter(it -> it.isInitial)
                .collect(Collectors.toList());

        // init method
        final MethodSpec.Builder initMethodBuilder = MethodSpec
                .methodBuilder("of")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(ClassName.get(dataClassInfo.packageName, dataClassInfo.metaClassName));
        for (DataClassInfo.Property initProperty : initProperties) {
            initMethodBuilder.addParameter(getTypeName(initProperty.type), initProperty.name);
        }
        final MethodSpec initMethod = initMethodBuilder
                .addStatement("return null")
                .build();
        metadataClass.addMethod(initMethod);

        // staging init method
        if (!initProperties.isEmpty()) {
            final MethodSpec.Builder init1MethodBuilder = MethodSpec
                    .methodBuilder("of")
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .addParameter(ClassName.get("", "Init"), "init")
                    .returns(ClassName.get(dataClassInfo.packageName, dataClassInfo.metaClassName))
                    .addStatement("return null");
            metadataClass.addMethod(init1MethodBuilder.build());

            final MethodSpec.Builder stagingInitMethodBuilder = MethodSpec
                    .methodBuilder("of")
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .returns(ClassName.get("", "Init.$" + initProperties.get(0).name))
                    .addStatement("return new Init().new $$" + initProperties.get(0).name + "()");
            metadataClass.addMethod(stagingInitMethodBuilder.build());

            final TypeSpec.Builder initBuilder = TypeSpec
                    .classBuilder("Init")
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC);
            final MethodSpec.Builder initConstBuilder = MethodSpec
                    .constructorBuilder()
                    .addModifiers(Modifier.PUBLIC);

            for (int i = 0; i < initProperties.size(); i++) {
                final DataClassInfo.Property initProperty = initProperties.get(i);
                initBuilder.addField(getTypeName(initProperty.type), initProperty.name, Modifier.PRIVATE);
                initConstBuilder.addParameter(getTypeName(initProperty.type), initProperty.name);
                initConstBuilder.addStatement("this." + initProperty.name + " = " + initProperty.name);

                final DataClassInfo.Property nextProperty = i < initProperties.size()-1 ? initProperties.get(i+1) : null;

                final TypeSpec.Builder stagingSetterWrapper = TypeSpec
                        .classBuilder("$" + initProperty.name)
                        .addModifiers(Modifier.PUBLIC);
                final MethodSpec.Builder setter = MethodSpec
                        .methodBuilder(initProperty.name)
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(getTypeName(initProperty.type), initProperty.name)
                        .addStatement("Init.this." + initProperty.name + " = " + initProperty.name)
                        .returns(nextProperty != null ? ClassName.get("", "$" + nextProperty.name) :
                                ClassName.get(dataClassInfo.packageName, dataClassInfo.metaClassName));
                if (nextProperty != null) {
                    setter.addStatement("return new " + "$$" + nextProperty.name + "()");
                } else {
                    setter.addStatement("return of(Init.this)");
                }
                stagingSetterWrapper.addMethod(setter.build());

                initBuilder.addType(stagingSetterWrapper.build());
            }

            initBuilder.addMethod(initConstBuilder.build());
            initBuilder.addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build());

            metadataClass.addType(initBuilder.build());
        }
    }


    private TypeName resolveMutableReturnType(TypeName typeName) {
        final DataClassInfo dataClassInfo = dataBeans.get(typeName);
        if (dataClassInfo == null)
            return typeName;
        else
            return dataClassInfo.metaClassName();
    }


    private static TypeName getTypeName(TypeMirror typeMirror) {
        String rawString = typeMirror.toString();
        if (typeMirror.getKind().isPrimitive())
            return TypeName.get(typeMirror);
        int dotPosition = rawString.lastIndexOf(".");
        String packageName = rawString.substring(0, dotPosition);
        String className = rawString.substring(dotPosition + 1);
        return ClassName.get(packageName, className);
    }

    private static String getterName(TypeMirror typeMirror, String name) {
        return (typeMirror.getKind().isPrimitive() && "boolean".equals(typeMirror.toString()) ?
                "is" : "get") + capitalize(name);
    }
    private static String setterName(String name) {
        return "set" + capitalize(name);
    }
    private static String capitalize(String name) {
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}

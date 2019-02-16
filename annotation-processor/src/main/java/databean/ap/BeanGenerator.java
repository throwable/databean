package databean.ap;

import com.squareup.javapoet.*;
import databean.MetaClass;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class BeanGenerator {
    public static final String INIT_METHOD_NAME = "of";

    private final ProcessingEnvironment procEnv;
    private final BeanMetadataResolver beanMetadataResolver;
    private final Map<TypeName, DataClassInfo> dataBeans;
    private final DefaultValueGenerator defaultValueGenerator;

    public BeanGenerator(ProcessingEnvironment procEnv, BeanMetadataResolver beanMetadataResolver, Map<TypeName, DataClassInfo> dataBeans) {
        this.procEnv = procEnv;
        this.dataBeans = dataBeans;
        this.beanMetadataResolver = beanMetadataResolver;
        defaultValueGenerator = new DefaultValueGenerator(procEnv, beanMetadataResolver);
    }

    public void generate(DataClassInfo dataClassInfo) {
        try {
            writeMetaClass(dataClassInfo);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }


    private void writeMetaClass(DataClassInfo dataClassInfo) throws IOException
    {
        writeDataBeanImpl(dataClassInfo);

        TypeSpec.Builder metaClass = TypeSpec
                .interfaceBuilder(dataClassInfo.metaClassName())
                //.addSuperinterface(dataClassInfo.className())
                .addSuperinterface(ParameterizedTypeName.get(
                        ClassName.get(MetaClass.class),
                        dataClassInfo.className()))
                .addModifiers(Modifier.PUBLIC/*, Modifier.FINAL*/);

        generateInitializers(metaClass, dataClassInfo);

        for (DataClassInfo.Property property : dataClassInfo.properties.values()) {
            final TypeName typeName = getTypeName(property.type);

            final ParameterSpec.Builder paramSpec = ParameterSpec.builder(typeName, property.name);
            if (property.notNullAnnotation != null)
                paramSpec.addAnnotation(AnnotationSpec.get(property.notNullAnnotation));

            if (!property.isReadOnly) {
                metaClass.addMethod(MethodSpec
                        .methodBuilder(property.isBeanNameDeclaration ? setterName(property.name) : property.name)
                        .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                        .returns(dataClassInfo.className())
                        .addParameter(paramSpec.build())
                        .build());
            } else {
                // native copy-setter
                metaClass.addMethod(MethodSpec
                        .methodBuilder("of" + capitalize(property.name))
                        .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                        .returns(dataClassInfo.className())
                        .addParameter(paramSpec.build())
                        .build());
            }
        }

        final JavaFile javaFile = JavaFile.builder(dataClassInfo.packageName, metaClass.build()).build();
        javaFile.writeTo(procEnv.getFiler());
    }


    private void writeDataBeanImpl(DataClassInfo dataClassInfo) throws IOException
    {
        TypeSpec.Builder beanClass = TypeSpec
                .classBuilder(dataClassInfo.beanClassName())
                //.addSuperinterface(dataClassInfo.metaClassName())
                .addSuperinterface(dataClassInfo.className())
                .addSuperinterface(Cloneable.class)
                .addModifiers(Modifier.PUBLIC/*, Modifier.FINAL*/);

        final MethodSpec.Builder constructorBuilder = MethodSpec
                .constructorBuilder()
                .addModifiers(Modifier.PUBLIC);

        for (DataClassInfo.Property property : dataClassInfo.properties.values()) {
            final TypeName mutableTypeName = getTypeName(property.type);

            final List<AnnotationSpec> valueAnnotations = new ArrayList<>();
            if (property.notNullAnnotation != null) {
                valueAnnotations.add(AnnotationSpec.get(property.notNullAnnotation));
            }

            // Getter
            if (dataClassInfo.generateBeanAccessors) {
                if (!property.isBeanNameDeclaration) {
                    // add simple accessor as proxy
                    beanClass.addMethod(MethodSpec
                            .methodBuilder(property.name)
                            .addModifiers(Modifier.PUBLIC)
                            .addAnnotation(Override.class)
                            .addAnnotations(valueAnnotations)
                            .addStatement("return $N()", getterName(property.type, property.name))
                            .returns(mutableTypeName)
                            .build());
                }
                // add bean getter as native accessor
                beanClass.addMethod(MethodSpec
                        .methodBuilder(getterName(property.type, property.name))
                        .addModifiers(Modifier.PUBLIC)
                        //.addStatement(generateGetterBody(property))
                        .addAnnotations(valueAnnotations)
                        .addCode(generateGetterBody(dataClassInfo, property))
                        .returns(getTypeName(property.type))
                        .build());
            } else {
                if (property.isBeanNameDeclaration) {
                    // add bean getter as native accessor
                    beanClass.addMethod(MethodSpec
                            .methodBuilder(getterName(property.type, property.name))
                            .addModifiers(Modifier.PUBLIC)
                            .addStatement("return this.$N", property.name)
                            .addAnnotations(valueAnnotations)
                            .returns(getTypeName(property.type))
                            .build());
                } else {
                    // add simple accessor as native accessor
                    beanClass.addMethod(MethodSpec
                            .methodBuilder(property.name)
                            .addModifiers(Modifier.PUBLIC)
                            .addAnnotation(Override.class)
                            .addAnnotations(valueAnnotations)
                            .addStatement("return this.$N", property.name)
                            .returns(mutableTypeName)
                            .build());
                }
            }

            if (property.isComputed)
                continue;

            final ParameterSpec parameterSpec = ParameterSpec.builder(getTypeName(property.type), property.name)
                    .addAnnotations(valueAnnotations).build();

            beanClass.addField(FieldSpec.builder(mutableTypeName, property.name, Modifier.PRIVATE)
                    .addAnnotations(valueAnnotations)
                    .build());

            if (property.isInitial) {
                constructorBuilder.addParameter(parameterSpec);
                if (property.notNullAnnotation != null)
                    constructorBuilder.addCode(genCheckNotNull(dataClassInfo, property.name));
                constructorBuilder.addStatement("this.$N = $N", property.name, property.name);
            }

            // Setter
            if (dataClassInfo.generateBeanAccessors) {
                if (!property.isReadOnly && !property.isBeanNameDeclaration) {
                    // add simple accessor as proxy
                    beanClass.addMethod(MethodSpec
                            .methodBuilder(property.name)
                            .addAnnotation(Override.class)
                            .addModifiers(Modifier.PUBLIC)
                            .returns(dataClassInfo.className())
                            .addParameter(parameterSpec)
                            .addStatement("$N($N)", setterName(property.name), property.name)   // delegate to bean setter
                            .addStatement("return this")
                            .build());
                }
                // add bean setter as native accessor
                final MethodSpec.Builder setter = MethodSpec
                        .methodBuilder(setterName(property.name))
                        .addModifiers(property.isReadOnly ? Modifier.PUBLIC : Modifier.PROTECTED)
                        //.addParameter(getTypeName(property.type), property.name)
                        .addParameter(parameterSpec);
                if (property.notNullAnnotation != null)
                    setter.addCode(genCheckNotNull(dataClassInfo, property.name));
                setter.addStatement("this.$N = $N", property.name, property.name);
                beanClass.addMethod(setter.build());
            } else {
                if (property.isBeanNameDeclaration) {
                    // add bean setter as native accessor
                    final MethodSpec.Builder setter = MethodSpec
                            .methodBuilder(setterName(property.name))
                            .addModifiers(property.isReadOnly ? Modifier.PUBLIC : Modifier.PROTECTED)
                            .addParameter(parameterSpec);
                    if (property.notNullAnnotation != null)
                        setter.addCode(genCheckNotNull(dataClassInfo, property.name));
                    setter.addStatement("this.$N = $N", property.name, property.name);
                    beanClass.addMethod(setter.build());
                } else {
                    if (!property.isReadOnly) {
                        // add simple accessor as native accessor
                        final MethodSpec.Builder setter = MethodSpec
                                .methodBuilder(property.name)
                                .addAnnotation(Override.class)
                                .addModifiers(Modifier.PUBLIC)
                                .returns(dataClassInfo.className())
                                .addParameter(parameterSpec);
                        if (property.notNullAnnotation != null)
                            setter.addCode(genCheckNotNull(dataClassInfo, property.name));
                        setter.addStatement("this.$N = $N", property.name, property.name)
                                .addStatement("return this");
                        beanClass.addMethod(setter.build());
                    }
                }
            }

            if (property.isReadOnly) {
                final boolean hasBeanSetter = property.isBeanNameDeclaration || dataClassInfo.generateBeanAccessors;
                // native copy-setter
                beanClass.addMethod(MethodSpec
                        .methodBuilder("of" + capitalize(property.name))
                        .addAnnotation(Override.class)
                        .addModifiers(Modifier.PUBLIC)
                        .returns(dataClassInfo.className())
                        .addParameter(parameterSpec)
                        .beginControlFlow("try")
                        .addStatement("$T cloned = ($T) clone()", dataClassInfo.beanClassName(), dataClassInfo.beanClassName())
                        .addStatement(hasBeanSetter ?
                                "cloned." + setterName(property.name) + "($N)" :
                                "cloned." + property.name + " = $N", property.name
                        )
                        .addStatement("return cloned")
                        .nextControlFlow("catch (CloneNotSupportedException e)")
                        //.endControlFlow()
                        .addStatement("throw new RuntimeException(e)")
                        .endControlFlow()
                        .build());
            }
        }

        constructorBuilder.addStatement("$$init()");
        beanClass.addMethod(constructorBuilder.build());

        // $init method that is called after initialization is done
        final MethodSpec.Builder $init = MethodSpec
                .methodBuilder("$init")
                .returns(TypeName.VOID);

        for (DataClassInfo.Property property : dataClassInfo.properties.values()) {
            if (property.hasDefaultValue) {
                $init.addStatement("this.$N = $T.super.$N()", property.name,
                        dataClassInfo.className(), property.defaultValueGetterName());
            }
            else if (!property.isInitial && property.notNullAnnotation != null && !property.type.getKind().isPrimitive()) {
                $init.addStatement("this.$N = " + defaultValueGenerator.generateDefaultValueFor(dataClassInfo, property),
                        property.name);
            }
        }

        beanClass.addMethod($init.build());

        // if class has initial parameters add non-public empty constructor
        if (dataClassInfo.properties.values().stream().anyMatch(it -> it.isInitial))
            beanClass.addMethod(MethodSpec.constructorBuilder()
                    .build());

        JavaFile.builder(dataClassInfo.packageName, beanClass.build()).build()
                .writeTo(procEnv.getFiler());
    }

    private void generateInitializers(TypeSpec.Builder metadataClass, DataClassInfo dataClassInfo) {
        final List<DataClassInfo.Property> initProperties = dataClassInfo.properties.values().stream()
                .filter(it -> it.isInitial)
                .collect(Collectors.toList());
        // of(...) method
        final MethodSpec.Builder initMethodBuilder = MethodSpec
                .methodBuilder("of")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(ClassName.get(dataClassInfo.packageName, dataClassInfo.className));

        if (!initProperties.isEmpty()) {
            // staging init method
            final MethodSpec.Builder stagingInitMethodBuilder = MethodSpec
                    .methodBuilder("of")
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .returns(ClassName.get("", "$" + initProperties.get(0).name))
                    .addStatement("$T bean = new $T()", dataClassInfo.beanClassName(), dataClassInfo.beanClassName())
                    .addStatement("return new $$" + initProperties.get(0).name + "(bean)");
            metadataClass.addMethod(stagingInitMethodBuilder.build());

            for (int i = 0; i < initProperties.size(); i++) {
                final DataClassInfo.Property initProperty = initProperties.get(i);
                final DataClassInfo.Property nextProperty = i < initProperties.size()-1 ? initProperties.get(i+1) : null;

                final List<AnnotationSpec> valueAnnotations = new ArrayList<>();
                if (initProperty.notNullAnnotation != null) {
                    valueAnnotations.add(AnnotationSpec.get(initProperty.notNullAnnotation));
                }

                // of(...) method parameters
                initMethodBuilder.addParameter(ParameterSpec.builder(getTypeName(initProperty.type), initProperty.name)
                        .addAnnotations(valueAnnotations).build());

                final TypeSpec.Builder stagingSetterWrapper = TypeSpec
                        .classBuilder("$" + initProperty.name)
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                        .addField(FieldSpec.builder(
                                dataClassInfo.beanClassName(), "bean", Modifier.PRIVATE).build())
                        .addMethod(MethodSpec.constructorBuilder()
                            .addParameter(dataClassInfo.beanClassName(), "bean")
                            .addStatement("this.bean = bean")
                            .build()
                        );
                final MethodSpec.Builder setter = MethodSpec
                        .methodBuilder(initProperty.name)
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(ParameterSpec.builder(getTypeName(initProperty.type), initProperty.name)
                                .addAnnotations(valueAnnotations).build())
                        .addStatement("bean." + setterName(initProperty.name) + "($N)", initProperty.name)
                        .returns(nextProperty != null ? ClassName.get("", "$" + nextProperty.name) :
                                dataClassInfo.className());
                if (nextProperty != null) {
                    setter.addStatement("return new " + "$$" + nextProperty.name + "(bean)");
                } else {
                    setter
                            .addStatement("bean.$$init()")
                            .addStatement("return bean");
                }
                stagingSetterWrapper.addMethod(setter.build());

                metadataClass.addType(stagingSetterWrapper.build());
            }
        }

        final MethodSpec initMethod = initMethodBuilder
                .addStatement("return new $T" + initProperties.stream()
                                .map(it -> it.name)
                                .collect(Collectors.joining(",", "(", ")")),
                        dataClassInfo.beanClassName())
                .build();

        metadataClass.addMethod(initMethod);
    }


    private CodeBlock generateGetterBody(DataClassInfo dataClassInfo, DataClassInfo.Property property) {
        final boolean hasBeanSetter = property.isBeanNameDeclaration || dataClassInfo.generateBeanAccessors;

        final CodeBlock.Builder codeBlock = CodeBlock.builder();
        /*if (property.hasDefaultValue && !property.type.getKind().isPrimitive()) {
            // primitive default values must be set on constructor call
            codeBlock
                    .beginControlFlow("if (this.$N == null)", property.name)
                    .addStatement("$T value = $T.super.$N()", property.type, dataClassInfo.className(), property.name)
                    .addStatement(hasBeanSetter ?
                            setterName(property.name) + "($N)" :
                            "this." + property.name + " = $N", property.name
                    )
                    .endControlFlow()
                    .build();
        }*/
        codeBlock.addStatement("return this." + property.name);
        return codeBlock.build();
    }


    private static CodeBlock genCheckNotNull(DataClassInfo dataClassInfo, String name) {
        return CodeBlock.builder()
                .addStatement("java.util.Objects.requireNonNull($N, \"$T.$N\")", name, dataClassInfo.className(), name)
                .build();
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

    public static String getterName(TypeMirror typeMirror, String name) {
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

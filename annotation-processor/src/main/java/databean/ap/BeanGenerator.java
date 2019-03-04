package databean.ap;

import com.squareup.javapoet.*;
import databean.DataClass;
import databean.MetaClass;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

public class BeanGenerator {
    public static final String INIT_METHOD_NAME = "of";

    private final ProcessingEnvironment procEnv;
    private final BeanMetadataResolver beanMetadataResolver;
    private final Map<TypeName, DataClassInfo> dataBeans;
    private final DefaultValueGenerator defaultValueGenerator;
    private final BeanPropertyResolver beanPropertyResolver;

    public BeanGenerator(ProcessingEnvironment procEnv, BeanMetadataResolver beanMetadataResolver, Map<TypeName, DataClassInfo> dataBeans) {
        this.procEnv = procEnv;
        this.dataBeans = dataBeans;
        this.beanMetadataResolver = beanMetadataResolver;
        defaultValueGenerator = new DefaultValueGenerator(procEnv, beanMetadataResolver);
        this.beanPropertyResolver = new BeanPropertyResolver(procEnv);
    }

    public void generate(DataClassInfo dataClassInfo) {
        try {
            if (dataClassInfo.enclosingClass == null) {
                writeDataBean(dataClassInfo);
                writeMetaClass(dataClassInfo);
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }


    private void writeMetaClass(DataClassInfo dataClassInfo) throws IOException {
        TypeSpec.Builder metaClass = TypeSpec
                .interfaceBuilder(dataClassInfo.metaClassName())
                .addSuperinterface(dataClassInfo.className())
                // inheritance problem
                /*.addSuperinterface(ParameterizedTypeName.get(
                        ClassName.get(MetaClass.class),
                        dataClassInfo.className()))*/
                .addModifiers(Modifier.PUBLIC/*, Modifier.FINAL*/);

        generateMetaClass(metaClass, dataClassInfo);

        final JavaFile javaFile = JavaFile.builder(dataClassInfo.packageName(), metaClass.build()).build();
        javaFile.writeTo(procEnv.getFiler());
    }


    private void generateMetaClass(TypeSpec.Builder metaClass, DataClassInfo dataClassInfo) {
        dataClassInfo.superClasses.forEach(it ->
            metaClass.addSuperinterface(it.metaClassName()));

        if (!dataClassInfo.isAbstract)
            generateInitializers(metaClass, dataClassInfo);

        //final List<BeanPropertyInfo> propertyInfos = beanPropertyResolver.beanProperties(dataClassInfo);

        for (DataClassInfo.Property property : dataClassInfo.properties) {

        /*for (BeanPropertyInfo propertyInfo : propertyInfos) {
            final DataClassInfo.Property property = propertyInfo.property;*/

            final TypeName typeName;
            if (property.isDataClass)
                typeName = requireNonNull(resolveDataClass(property.type)).metaClassName();
            else
                typeName = property.typeName();

            List<AnnotationSpec> typeAnnotations = new ArrayList<>();
            if (property.notNullAnnotation != null)
                typeAnnotations.add(AnnotationSpec.get(property.notNullAnnotation));

            final ParameterSpec.Builder paramSpec = ParameterSpec.builder(typeName, property.name);
            paramSpec.addAnnotations(typeAnnotations);

            if (!property.isReadOnly) {
                metaClass.addMethod(MethodSpec
                        .methodBuilder(property.writeAccessorName())
                        .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                        .returns(dataClassInfo.metaClassName())
                        .addParameter(paramSpec.build())
                        .build());
            } else {
                // native copy-setter: ofXXX()
                if (!property.isFixed && !dataClassInfo.isAbstract) {
                    metaClass.addMethod(MethodSpec
                            .methodBuilder("of" + capitalize(property.name))
                            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                            .returns(dataClassInfo.metaClassName())
                            .addParameter(paramSpec.build())
                            .build());
                }
            }

            // substitute dataClass getter return value by metaClass
            if (property.isDataClass) {
                if (property.hasDefaultValue) {
                    // chaining call to A.super.getter()
                    CodeBlock defaultBody;
                    if (property.defaultValueExpression == null) {
                        defaultBody = CodeBlock.builder()
                                .addStatement("return $T.super.$N()", dataClassInfo.className(), property.readAccessorName())
                                .build();
                    } else {
                        defaultBody = CodeBlock.builder()
                                .addStatement("return $L", property.defaultValueExpression)
                                .build();
                    }
                    metaClass.addMethod(MethodSpec
                            .methodBuilder(property.readAccessorName())
                            .returns(typeName)
                            .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
                            .addAnnotation(Override.class)
                            .addAnnotations(typeAnnotations)
                            .addCode(defaultBody)
                            .build());
                }
                else {
                    metaClass.addMethod(MethodSpec
                            .methodBuilder(property.readAccessorName())
                            .returns(typeName)
                            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                            .addAnnotation(Override.class)
                            .addAnnotations(typeAnnotations)
                            .build());
                }
            }
            else if (property.hasDefaultValue) {
                // defined default value: copy default method that can be accessed from bean implementation
                // chaining call to A.super.getter()
                CodeBlock defaultBody;
                if (property.defaultValueExpression == null) {
                    defaultBody = CodeBlock.builder()
                        .addStatement("return $T.super.$N()", dataClassInfo.className(), property.readAccessorName())
                        .build();
                } else {
                    defaultBody = CodeBlock.builder()
                            .addStatement("return $L", property.defaultValueExpression)
                            .build();
                }
                metaClass.addMethod(MethodSpec
                        .methodBuilder(property.readAccessorName())
                        .returns(typeName)
                        .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
                        .addAnnotation(Override.class)
                        .addAnnotations(typeAnnotations)
                        //.addStatement("return $T.super.$N()", dataClassInfo.className(), getterName)
                        .addCode(defaultBody)
                        .build());
            }
        }

        // copy custom constructors
        if (!dataClassInfo.isAbstract) {
            for (ExecutableElement customConstructor : dataClassInfo.customConstructors) {
                metaClass.addMethod(MethodSpec.methodBuilder(customConstructor.getSimpleName().toString())
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                        .returns(dataClassInfo.metaClassName())
                        .addParameters(customConstructor.getParameters().stream()
                                .map(ParameterSpec::get).collect(Collectors.toList()))
                        .addStatement("return ($T) $T.of($L)", dataClassInfo.metaClassName(), dataClassInfo.className(),
                                customConstructor.getParameters().stream()
                                        .map(VariableElement::getSimpleName)
                                        .collect(Collectors.joining(","))
                        )
                        .build());
            }
        }

        // nested data classes
        dataBeans.values().stream()
                .filter(it -> it.enclosingClass != null &&
                        it.enclosingClass.className().equals(dataClassInfo.className()))
                .forEach(it -> {
                    TypeSpec.Builder nestedMetaClass = TypeSpec
                            .interfaceBuilder(it.metaClassName().simpleName())
                            .addSuperinterface(it.className())
                            .addSuperinterface(ParameterizedTypeName.get(
                                    ClassName.get(MetaClass.class),
                                    it.className()))
                            .addModifiers(Modifier.PUBLIC, Modifier.STATIC);
                    generateMetaClass(nestedMetaClass, it);
                    metaClass.addType(nestedMetaClass.build());
                });
    }


    private void writeDataBean(DataClassInfo dataClassInfo) throws IOException {
        TypeSpec.Builder beanClass = TypeSpec
                .classBuilder(dataClassInfo.beanClassName())
                //.addSuperinterface(dataClassInfo.metaClassName())
                .addSuperinterface(dataClassInfo.metaClassName())
                .addSuperinterface(Cloneable.class)
                .addModifiers(Modifier.PUBLIC/*, Modifier.FINAL*/);

        generateDataBean(beanClass, dataClassInfo);

        JavaFile.builder(dataClassInfo.enclosingType.toString(), beanClass.build()).build()
                .writeTo(procEnv.getFiler());
    }


    private void generateDataBean(TypeSpec.Builder beanClass, DataClassInfo dataClassInfo)
    {
        final DataClassInfo beanSuperClass;
        if (!dataClassInfo.superClasses.isEmpty() && dataClassInfo.inheritFromSuperclass) {
            beanSuperClass = dataClassInfo.superClasses.get(0);
            beanClass.superclass(beanSuperClass.beanClassName());
        } else {
            beanSuperClass = null;
        }

        final MethodSpec.Builder constructorBuilder = MethodSpec
                .constructorBuilder()
                .addModifiers(dataClassInfo.isAbstract ? Modifier.PROTECTED : Modifier.PUBLIC);

        final List<BeanPropertyInfo> properties = beanPropertyResolver.beanProperties(dataClassInfo);

        for (BeanPropertyInfo propertyInfo : properties)
        {
            final DataClassInfo.Property property = propertyInfo.property;

            //final TypeName mutableTypeName = getTypeName(property.type);
            final TypeName typeName;
            if (property.isDataClass)
                typeName = requireNonNull(resolveDataClass(property.type)).metaClassName();
            else
                typeName = property.typeName();

            final List<AnnotationSpec> valueAnnotations = new ArrayList<>();
            if (property.notNullAnnotation != null) {
                valueAnnotations.add(AnnotationSpec.get(property.notNullAnnotation));
            }

            // if property inherited from superclass and have the same type
            final boolean overridesPropertyOfSameType = propertyInfo.beanSuperclassProperty != null &&
                    procEnv.getTypeUtils().isSameType(property.type, propertyInfo.beanSuperclassProperty.type);

            // read accessor
            if (!overridesPropertyOfSameType) {
                if (dataClassInfo.generateBeanAccessors) {
                    if (!property.isBeanNameDeclaration) {
                        // add simple accessor as proxy
                        beanClass.addMethod(MethodSpec
                                .methodBuilder(property.readAccessorName())
                                .addModifiers(Modifier.PUBLIC)
                                .addAnnotation(Override.class)
                                .addAnnotations(valueAnnotations)
                                .addStatement("return $N()", property.getterName())
                                .returns(typeName)
                                .build());
                    }
                    // add bean getter as native accessor
                    beanClass.addMethod(MethodSpec
                            .methodBuilder(property.getterName())
                            .addModifiers(Modifier.PUBLIC)
                            //.addStatement(generateGetterBody(property))
                            .addAnnotations(valueAnnotations)
                            .addCode(generateGetterBody(dataClassInfo, propertyInfo))
                            .returns(typeName)
                            .build());
                } else {
                    if (property.isBeanNameDeclaration) {
                        // add bean getter as native accessor
                        beanClass.addMethod(MethodSpec
                                .methodBuilder(property.readAccessorName())
                                .addModifiers(Modifier.PUBLIC)
                                //.addStatement("return this.$N", property.name)
                                .addCode(generateGetterBody(dataClassInfo, propertyInfo))
                                .addAnnotations(valueAnnotations)
                                .returns(typeName)
                                .build());
                    } else {
                        // add simple accessor as native accessor
                        beanClass.addMethod(MethodSpec
                                .methodBuilder(property.readAccessorName())
                                .addModifiers(Modifier.PUBLIC)
                                .addAnnotation(Override.class)
                                .addAnnotations(valueAnnotations)
                                //.addStatement("return this.$N", property.name)
                                .addCode(generateGetterBody(dataClassInfo, propertyInfo))
                                .returns(typeName)
                                .build());
                    }
                }
            }

            if (property.isComputed)
                continue;

            final ParameterSpec parameterSpec = ParameterSpec.builder(typeName, property.name)
                    .addAnnotations(valueAnnotations).build();

            /*if (dataClassInfo.className().simpleName().equals("ICat"))
                Math.random();*/

            // Field
            if (propertyInfo.beanSuperclassProperty == null)
                beanClass.addField(FieldSpec.builder(typeName, property.name, Modifier.PROTECTED)
                        .addAnnotations(valueAnnotations)
                        .build());

            // Constructor
            if (property.isInitial) {
                constructorBuilder.addParameter(parameterSpec);
                constructorBuilder.addCode(generateSetterBody(dataClassInfo, propertyInfo));
            }

            // Write accessor
            if (!overridesPropertyOfSameType || propertyInfo.readOnlyOverridesMutable ||
                    !property.equals(propertyInfo.beanSuperclassProperty)) {
                if (dataClassInfo.generateBeanAccessors) {
                    if (!property.isReadOnly && !property.isBeanNameDeclaration) {
                        // add simple accessor as proxy
                        beanClass.addMethod(MethodSpec
                                .methodBuilder(property.writeAccessorName())
                                .addAnnotation(Override.class)
                                .addModifiers(Modifier.PUBLIC)
                                .returns(dataClassInfo.metaClassName())
                                .addParameter(parameterSpec)
                                //.addCode(generateSetterBody(dataClassInfo, propertyInfo))
                                .addStatement("$N($N)", property.setterName(), property.name)
                                .addStatement("return this")
                                .build());
                    }
                    // add bean setter as native accessor
                    beanClass.addMethod(MethodSpec
                            .methodBuilder(property.setterName())
                            .addModifiers(property.isReadOnly && !propertyInfo.readOnlyOverridesMutable ?
                                    Modifier.PROTECTED : Modifier.PUBLIC)
                            //.addParameter(getTypeName(property.type), property.name)
                            .addParameter(parameterSpec)
                            .addCode(generateSetterBody(dataClassInfo, propertyInfo))
                            .build());
                } else {
                    if (property.isBeanNameDeclaration) {
                        // add bean setter as native accessor
                        beanClass.addMethod(MethodSpec
                                .methodBuilder(property.setterName())
                                .addModifiers(property.isReadOnly && !propertyInfo.readOnlyOverridesMutable ?
                                        Modifier.PROTECTED : Modifier.PUBLIC)
                                .addParameter(parameterSpec)
                                .addCode(generateSetterBody(dataClassInfo, propertyInfo))
                                .build());
                    } else {
                        if (!property.isReadOnly) {
                            // add simple accessor as native accessor
                            beanClass.addMethod(MethodSpec
                                    .methodBuilder(property.writeAccessorName())
                                    .addAnnotation(Override.class)
                                    .addModifiers(Modifier.PUBLIC)
                                    .returns(dataClassInfo.metaClassName())
                                    .addParameter(parameterSpec)
                                    .addCode(generateSetterBody(dataClassInfo, propertyInfo))
                                    .addStatement("return this")
                                    .build());
                        }
                    }
                }

                // native copy-setter
                if (property.isReadOnly) {
                    //final boolean hasBeanSetter = property.isBeanNameDeclaration || dataClassInfo.generateBeanAccessors;
                    if (!property.isFixed) {
                        // copy-and-set
                        beanClass.addMethod(MethodSpec
                                .methodBuilder("of" + capitalize(property.name))
                                .addAnnotations(dataClassInfo.isAbstract ? Collections.emptyList() :
                                        Collections.singletonList(AnnotationSpec.builder(Override.class).build()))
                                .addModifiers(dataClassInfo.isAbstract ? Modifier.PROTECTED : Modifier.PUBLIC)
                                .returns(dataClassInfo.metaClassName())
                                .addParameter(parameterSpec)
                                .addCode(property.notNullAnnotation != null ?
                                        genCheckNotNull(dataClassInfo, property.name) :
                                        CodeBlock.builder().build())
                                /*.beginControlFlow("try")
                                .addStatement("$T cloned = ($T) clone()", dataClassInfo.beanClassName(), dataClassInfo.beanClassName())
                                // direct field access because setter may be masked with read-only precondition
                                .addStatement("cloned.$N = $N", property.name, property.name)
                                .addStatement("return cloned")
                                .nextControlFlow("catch (CloneNotSupportedException e)")
                                //.endControlFlow()
                                .addStatement("throw new RuntimeException(e)")
                                .endControlFlow()*/
                                .addStatement("$T cloned = new $T()", dataClassInfo.beanClassName(), dataClassInfo.beanClassName())
                                .addStatement("this.$$fill(cloned)")
                                .addStatement("cloned.$N = $N", property.name, property.name)
                                .addStatement("return cloned")
                                .build());
                    }
                    else {
                        /*
                        One of superMetaClasses has the same property defined as @ReadOnly (but not @Fixed) =>
                        it also defines ofXXX() method. On a @Fixed property we must mask this method to not to
                        allow the modification of the value of this property.
                         */
                        boolean superTypeHasOfXXXDefined = false;
                        for (DataClassInfo superClass : dataClassInfo.superClasses) {
                            if (superClass.properties.stream()
                                    .anyMatch(it -> it.name.equals(property.name) && !it.isFixed && it.isReadOnly)) {
                                superTypeHasOfXXXDefined = true;
                                break;
                            }
                        }
                        if (superTypeHasOfXXXDefined) {
                            beanClass.addMethod(MethodSpec
                                    .methodBuilder("of" + capitalize(property.name))
                                    .addModifiers(dataClassInfo.isAbstract ? Modifier.PROTECTED : Modifier.PUBLIC)
                                    .addAnnotations(dataClassInfo.isAbstract ? Collections.emptyList() :
                                            Collections.singletonList(AnnotationSpec.builder(Override.class).build()))
                                    .returns(dataClassInfo.metaClassName())
                                    .addParameter(parameterSpec)
                                    .addStatement("throw new UnsupportedOperationException($S)", "attempt to change fixed property")
                                    .build());
                        }
                    }
                }
            }
        }

        constructorBuilder.addStatement("$$init()");
        beanClass.addMethod(constructorBuilder.build());

        // $init method that is called after initialization is done and sets default values

        final MethodSpec.Builder $init = MethodSpec
                .methodBuilder("$init")
                .returns(TypeName.VOID);

        final MethodSpec.Builder $fill = MethodSpec.methodBuilder("$fill")
                .addModifiers(Modifier.PROTECTED)
                .returns(Void.TYPE)
                .addParameter(ParameterSpec.builder(dataClassInfo.beanClassName(), "cloned").build());

        if (beanSuperClass != null) {
            $init.addStatement("super.$$init()");
            $fill.addStatement("super.$$fill(cloned)");
        }

        for (DataClassInfo.Property property : dataClassInfo.properties) {
        /*for (BeanPropertyInfo propertyInfo : properties) {
            final DataClassInfo.Property property = propertyInfo.property;*/
            if (property.hasDefaultValue) {
                //if (property.defaultValueExpression == null) {
                $init.addStatement("this.$N = $T.super.$N()", property.name,
                        dataClassInfo.metaClassName(), property.readAccessorName());
                /*} else {
                    $init.addStatement("this.$N = $L", property.name, property.defaultValueExpression);
                }*/
            } else if (!property.isInitial && property.notNullAnnotation != null && !property.type.getKind().isPrimitive()) {
                $init.addStatement("this.$N = " + defaultValueGenerator.generateDefaultValueFor(dataClassInfo, property),
                        property.name);
            }
        }
        for (BeanPropertyInfo property : properties) {
            if (property.beanSuperclassProperty == null) {
                $fill.addStatement("cloned.$N = this.$N", property.property.name, property.property.name);
            }
        }

        beanClass.addMethod($init.build());
        beanClass.addMethod($fill.build());

        // if class has initial parameters add non-public empty constructor
        if (properties.stream().anyMatch(it -> it.property.isInitial))
            beanClass.addMethod(MethodSpec.constructorBuilder()
                    .build());

        // nested data beans
        dataBeans.values().stream()
                .filter(it -> it.enclosingClass != null &&
                        it.enclosingClass.className().equals(dataClassInfo.className()))
                .forEach(it -> {
                    TypeSpec.Builder nestedBeanClass = TypeSpec
                            .classBuilder(it.beanClassName())
                            //.addSuperinterface(dataClassInfo.metaClassName())
                            .addSuperinterface(it.metaClassName())
                            .addSuperinterface(Cloneable.class)
                            .addModifiers(Modifier.PUBLIC, Modifier.STATIC);
                    generateDataBean(nestedBeanClass, it);
                    beanClass.addType(nestedBeanClass.build());
                });
    }


    private void generateInitializers(TypeSpec.Builder metadataClass, DataClassInfo dataClassInfo)
    {
        final List<BeanPropertyInfo> properties = beanPropertyResolver.beanProperties(dataClassInfo);
        //final List<DataClassInfo.Property> properties = beanProperties(dataClassInfo);
        final List<DataClassInfo.Property> initProperties = properties.stream()
                .map(it -> it.property)
                .filter(it -> it.isInitial)
                .collect(Collectors.toList());
        // of(...) method
        final MethodSpec.Builder initMethodBuilder = MethodSpec
                .methodBuilder("of")
                .addModifiers(dataClassInfo.isAbstract ? Modifier.PROTECTED : Modifier.PUBLIC, Modifier.STATIC)
                .returns(dataClassInfo.metaClassName());

        if (!initProperties.isEmpty()) {
            // staging init method
            final MethodSpec.Builder stagingInitMethodBuilder = MethodSpec
                    .methodBuilder("of")
                    .addModifiers(dataClassInfo.isAbstract ? Modifier.PROTECTED : Modifier.PUBLIC, Modifier.STATIC)
                    .returns(ClassName.get("", "$" + initProperties.get(0).name))
                    .addStatement("$T bean = new $T()", dataClassInfo.beanClassName(), dataClassInfo.beanClassName())
                    .addStatement("return new $$" + initProperties.get(0).name + "(bean)");
            metadataClass.addMethod(stagingInitMethodBuilder.build());

            for (int i = 0; i < initProperties.size(); i++) {
                final DataClassInfo.Property initProperty = initProperties.get(i);
                final DataClassInfo.Property nextProperty = i < initProperties.size() - 1 ? initProperties.get(i + 1) : null;

                final List<AnnotationSpec> valueAnnotations = new ArrayList<>();
                if (initProperty.notNullAnnotation != null) {
                    valueAnnotations.add(AnnotationSpec.get(initProperty.notNullAnnotation));
                }

                final TypeName typeName;
                if (initProperty.isDataClass)
                    typeName = requireNonNull(resolveDataClass(initProperty.type)).metaClassName();
                else
                    typeName = initProperty.typeName();

                // of(...) method parameters
                initMethodBuilder.addParameter(ParameterSpec.builder(typeName, initProperty.name)
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
                        .addParameter(ParameterSpec.builder(typeName, initProperty.name)
                                .addAnnotations(valueAnnotations).build())
                        .addStatement("bean." + initProperty.setterName() + "($N)", initProperty.name)
                        .returns(nextProperty != null ? ClassName.get("", "$" + nextProperty.name) :
                                dataClassInfo.metaClassName());
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


    private CodeBlock generateGetterBody(DataClassInfo dataClassInfo, BeanPropertyInfo propertyInfo) {
        final DataClassInfo.Property property = propertyInfo.property;
        final CodeBlock.Builder codeBlock = CodeBlock.builder();
        if (propertyInfo.beanSuperclassProperty != null) {
            if (!procEnv.getTypeUtils().isSameType(property.type, propertyInfo.beanSuperclassProperty.type))
                codeBlock.addStatement("return ($T) super.$N()", property.type, property.getterName());
            else
                codeBlock.addStatement("return super.$N()", property.type, property.getterName());
        } else {
            codeBlock.addStatement("return this.$N", property.name);
        }
        return codeBlock.build();
    }


    private CodeBlock generateSetterBody(DataClassInfo dataClassInfo, BeanPropertyInfo propertyInfo) {
        final CodeBlock.Builder builder = CodeBlock.builder();
        final DataClassInfo.Property property = propertyInfo.property;

        if (propertyInfo.beanSuperclassProperty != null) {
            // overrides another bean property
            if (propertyInfo.readOnlyOverridesMutable)
                builder.addStatement("throw new UnsupportedOperationException($S)", "Read-only property");
            else {
                if (property.notNullAnnotation != null)
                    builder.add(genCheckNotNull(dataClassInfo, property.name));
                builder.addStatement("super.$N($N)", property.setterName(), property.name);
            }

        } else {
            if (property.notNullAnnotation != null)
                builder.add(genCheckNotNull(dataClassInfo, property.name));
            builder.addStatement("this.$N = $N", property.name, property.name);
        }
        return builder.build();
    }


    private static CodeBlock genCheckNotNull(DataClassInfo dataClassInfo, String name) {
        return CodeBlock.builder()
                .addStatement("java.util.Objects.requireNonNull($N, \"$T.$N\")", name, dataClassInfo.className(), name)
                .build();
    }

    public static String capitalize(String name) {
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private DataClassInfo resolveDataClass(TypeMirror typeMirror) {
        final TypeElement typeElement = procEnv.getElementUtils().getTypeElement(typeMirror.toString());

        if (typeElement.getAnnotation(DataClass.class) != null) {
            return beanMetadataResolver.resolve(typeElement);
        }

        return null;
    }
}

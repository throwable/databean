package databean.ap;

import com.squareup.javapoet.*;
import databean.MetaClass;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class BeanMetadataWriter {
    public static final String INIT_METHOD_NAME = "of";

    private final ProcessingEnvironment procEnv;

    public BeanMetadataWriter(ProcessingEnvironment procEnv) {
        this.procEnv = procEnv;
    }

    public void writeBeanMetadata(BeanMetadata beanMetadata) {
        try {
            writeMetaFile(beanMetadata);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }


    private void writeMetaFile(BeanMetadata beanMetadata) throws IOException
    {
        TypeSpec.Builder metadataClass = TypeSpec
                .interfaceBuilder(BeanMetadata.metaClassName(beanMetadata.className))
                .addSuperinterface(ClassName.get(beanMetadata.packageName, beanMetadata.className))
                .addSuperinterface(ParameterizedTypeName.get(
                        ClassName.get(MetaClass.class),
                        ClassName.get(beanMetadata.packageName, beanMetadata.className)))
                .addModifiers(Modifier.PUBLIC/*, Modifier.FINAL*/);

        writeInitializer(metadataClass, beanMetadata);

        for (BeanMetadata.MetaProperty metaProperty : beanMetadata.properties.values()) {
            if (!metaProperty.isReadOnly) {
                MethodSpec intentMethod = MethodSpec
                        .methodBuilder(metaProperty.name)
                        .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                        .returns(ClassName.get(beanMetadata.packageName, beanMetadata.metaClassName))
                        .addParameter(getTypeName(metaProperty.type), metaProperty.name)
                        .build();
                metadataClass.addMethod(intentMethod);
            }
        }

        final JavaFile javaFile = JavaFile.builder(beanMetadata.packageName, metadataClass.build()).build();
        javaFile.writeTo(procEnv.getFiler());
    }

    private void writeInitializer(TypeSpec.Builder metadataClass, BeanMetadata beanMetadata) {
        final List<BeanMetadata.MetaProperty> initProperties = beanMetadata.properties.values().stream()
                .filter(it -> it.isInitial)
                .collect(Collectors.toList());

        // init method
        final MethodSpec.Builder initMethodBuilder = MethodSpec
                .methodBuilder("of")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(ClassName.get(beanMetadata.packageName, beanMetadata.metaClassName));
        for (BeanMetadata.MetaProperty initProperty : initProperties) {
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
                    .returns(ClassName.get(beanMetadata.packageName, beanMetadata.metaClassName))
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
                final BeanMetadata.MetaProperty initProperty = initProperties.get(i);
                initBuilder.addField(getTypeName(initProperty.type), initProperty.name, Modifier.PRIVATE);
                initConstBuilder.addParameter(getTypeName(initProperty.type), initProperty.name);
                initConstBuilder.addStatement("this." + initProperty.name + " = " + initProperty.name);

                final BeanMetadata.MetaProperty nextProperty = i < initProperties.size()-1 ? initProperties.get(i+1) : null;

                final TypeSpec.Builder stagingSetterWrapper = TypeSpec
                        .classBuilder("$" + initProperty.name)
                        .addModifiers(Modifier.PUBLIC);
                final MethodSpec.Builder setter = MethodSpec
                        .methodBuilder(initProperty.name)
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(getTypeName(initProperty.type), initProperty.name)
                        .addStatement("Init.this." + initProperty.name + " = " + initProperty.name)
                        .returns(nextProperty != null ? ClassName.get("", "$" + nextProperty.name) :
                                ClassName.get(beanMetadata.packageName, beanMetadata.metaClassName));
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


    private static TypeName getTypeName(TypeMirror typeMirror) {
        String rawString = typeMirror.toString();
        if (typeMirror.getKind().isPrimitive())
            return TypeName.get(typeMirror);
        int dotPosition = rawString.lastIndexOf(".");
        String packageName = rawString.substring(0, dotPosition);
        String className = rawString.substring(dotPosition + 1);
        return ClassName.get(packageName, className);
    }
}

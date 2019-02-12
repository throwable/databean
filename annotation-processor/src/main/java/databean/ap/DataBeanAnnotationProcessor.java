package databean.ap;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import databean.DataClass;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/*
https://www.javacodegeeks.com/2015/09/java-annotation-processors.html
*/
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedAnnotationTypes({"databean.DataClass"})
public class DataBeanAnnotationProcessor extends AbstractProcessor {
    private ProcessingEnvironment procEnv;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.procEnv = processingEnv;
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        procEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "*** Starting annotation processor ***");

        final BeanMetadataResolver beanMetadataResolver = new BeanMetadataResolver(procEnv);
        final BeanMetadataWriter beanMetadataWriter = new BeanMetadataWriter(procEnv);

        for (Element element : roundEnv.getElementsAnnotatedWith(DataClass.class)) {
            TypeElement typeElement = (TypeElement) element;
            try {
                if (element.getKind() != ElementKind.INTERFACE)
                    throw new RuntimeException("@DataBean annotation must be applied to an interface.");

                procEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "added file " + typeElement.getSimpleName().toString());

                final BeanMetadata beanMetadata = beanMetadataResolver.resolve(typeElement);
                beanMetadataWriter.writeBeanMetadata(beanMetadata);
            } catch (Exception e) {
                procEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Error processing class "
                        + typeElement.getSimpleName() + ": " + e.getMessage());
                throw e;
            }

        /*String dataClassName = typeElement.getSimpleName().toString();
        String packageName = procEnv.getElementUtils().getPackageOf(typeElement).getQualifiedName().toString();

        ClassName dataClass = ClassName.get(packageName, dataClassName);

        try {
            *//*JavaFileObject jfo = procEnv.getFiler().createSourceFile(dataClassName + "Meta");
            Writer writer = jfo.openWriter();*//*

            TypeSpec.Builder metadataClass = TypeSpec
                    .interfaceBuilder("$" + dataClassName)
                    .addModifiers(Modifier.PUBLIC*//*, Modifier.FINAL*//*);

            final JavaFile javaFile = JavaFile.builder(packageName, metadataClass.build()).build();
            javaFile.writeTo(procEnv.getFiler());
        *//*MethodSpec intentMethod = MethodSpec
                .methodBuilder(METHOD_PREFIX + dataClassName)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(classIntent)
                .addParameter(classContext, "context")
                .addStatement("return new $T($L, $L)", classIntent, "context", dataClass + ".class")
                .build();
        navigatorClass.addMethod(intentMethod);*//*
        } catch (IOException e) {
            throw new RuntimeException(e);
        }*/
        }

        return true;
    }
}

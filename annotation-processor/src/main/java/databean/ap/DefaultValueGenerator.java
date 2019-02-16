package databean.ap;

import databean.DataClass;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.util.HashMap;
import java.util.Map;

public class DefaultValueGenerator {
    private final static Map<String, String> TYPE_INIT_MAP = new HashMap<String,String>() {{
        put("java.lang.String", "\"\"");
        put("java.lang.Integer", "Integer.valueOf(0)");
        put("java.lang.Long", "Long.valueOf(0)");
        put("java.lang.Byte", "Byte.valueOf(0)");
        put("java.lang.Short", "Short.valueOf(0)");
        put("java.lang.Float", "Float.valueOf(0)");
        put("java.lang.Double", "Double.valueOf(0)");
        put("java.lang.Boolean", "Boolean.FALSE");
        put("java.lang.BigDecimal", "BigDecimal.ZERO");
        put("java.lang.BigInteger", "BigInteger.ZERO");
    }};

    private final ProcessingEnvironment procEnv;
    private final BeanMetadataResolver beanMetadataResolver;

    public DefaultValueGenerator(ProcessingEnvironment procEnv, BeanMetadataResolver beanMetadataResolver) {
        this.procEnv = procEnv;
        this.beanMetadataResolver = beanMetadataResolver;
    }

    public String generateDefaultValueFor(DataClassInfo dataClassInfo, DataClassInfo.Property property) {
        // standard property
        final String init = TYPE_INIT_MAP.get(property.type.toString());
        if (init != null)
            return init;

        //final Element element = procEnv.getTypeUtils().asElement(property.type);
        final TypeElement typeElement = procEnv.getElementUtils().getTypeElement(property.type.toString());

        if (typeElement.getAnnotation(DataClass.class) != null) {
            final DataClassInfo typeClassInfo = beanMetadataResolver.resolve(typeElement);
            if (typeClassInfo.hasDefaultConstructor())
                return typeClassInfo.metaClassName().toString().replace("$", "$$") + ".of()";
        }
        // TODO: add Enumeration default value
        throw new IllegalArgumentException("Can not determine default value for non-nullable property " +
                dataClassInfo.className + "." + property.name +". Please try to set it explicitly.");

    }
}

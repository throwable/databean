package databean.ap;

import javax.annotation.processing.ProcessingEnvironment;
import java.util.*;
import java.util.stream.Collectors;

public class BeanPropertyResolver {
    private final ProcessingEnvironment procEnv;

    private final Map<String, List<BeanPropertyInfo>> cache = new HashMap<>();

    public BeanPropertyResolver(ProcessingEnvironment procEnv) {
        this.procEnv = procEnv;
    }


    public List<BeanPropertyInfo> beanProperties(DataClassInfo dataClassInfo)
    {
        List<BeanPropertyInfo> propertyInfo = cache.get(dataClassInfo.className().toString());
        if (propertyInfo != null)
            return propertyInfo;

        final List<DataClassInfo> superClasses;
        /*if (dataClassInfo.inheritFromSuperclass && !dataClassInfo.superClasses.isEmpty())
            superClasses = dataClassInfo.superClasses.subList(1, dataClassInfo.superClasses.size());
        else*/
        superClasses = dataClassInfo.superClasses;

        final Map<String, List<BeanPropertyInfo>> inheritedProperties = new LinkedHashMap<>();

        for (DataClassInfo superClass : superClasses) {
            final List<BeanPropertyInfo> properties = beanProperties(superClass);
            properties.forEach(it ->
                    inheritedProperties.computeIfAbsent(it.property.name, k -> new ArrayList<>()).add(it)
            );
        }

        final Map<String, BeanPropertyInfo> consolidatedProperties = new LinkedHashMap<>();

        /*if (dataClassInfo.className().simpleName().equals("ICat"))
            Math.random();*/


        /*
         Consolidation algorithm between inherited multiple properties
          */
        for (List<BeanPropertyInfo> propertyInfos : inheritedProperties.values()) {
            List<DataClassInfo.Property> properties = propertyInfos.stream()
                    .map(it -> it.property).collect(Collectors.toList());
            if (properties.size() == 1) {
                consolidatedProperties.put(properties.get(0).name, propertyInfos.get(0));
                continue;
            }
            /*
            - select resulting property with the most narrow type
            - check if all property types are compatible (one must be subinterface of another)
            */
            DataClassInfo.Property resultingProperty = properties.stream().max((prop1, prop2) -> {
                if (procEnv.getTypeUtils().isSameType(prop1.type, prop2.type)) {
                    return 0;
                } else {
                    // for property with the same name choose the most narrow type
                    if (procEnv.getTypeUtils().isSubtype(prop1.type, prop2.type))
                        // prop1.type is subtype of prop2.type
                        return -1;
                    else if (procEnv.getTypeUtils().isSubtype(prop2.type, prop1.type))
                        return 1;
                    else
                        throw new RuntimeException("Property type of '" + dataClassInfo.className().simpleName() + "." +
                                prop1.name + "' is incompatible with one of it's inherited superinterfaces");
                }
            }).orElseThrow(() -> new RuntimeException("WTF: empty property list???"));

            /*
            - if any of the properties has @Initial annotation:
                - If resulting property or any of properties with the resulting property's type has default value
                    inherit the default value to the resulting property
                - In other case set resulting property as @Initial
            - if the resulting property is readonly but any another property is mutable
                set the resulting property as mutable
             */
            boolean hasAnyInitialProperty = false;
            DataClassInfo.Property propertyWithDefaultValue = null;
            boolean hasAnyMutableProperty = false;
            for (DataClassInfo.Property property : properties) {
                if (property.isInitial) hasAnyInitialProperty = true;
                if (!property.isReadOnly) hasAnyMutableProperty = true;
                if (procEnv.getTypeUtils().isSameType(property.type, resultingProperty.type)) {
                    if (property.hasDefaultValue)
                        propertyWithDefaultValue = property;
                }
            }

            if (hasAnyInitialProperty) {
                if (propertyWithDefaultValue != null) {
                    resultingProperty = resultingProperty.withDefaults(
                            propertyWithDefaultValue.hasDefaultValue,
                            propertyWithDefaultValue.defaultValueExpression);
                } else {
                    resultingProperty = resultingProperty.withInitial(true);
                }
            }

            if (resultingProperty.isReadOnly && hasAnyMutableProperty) {
                resultingProperty = resultingProperty.withReadOnly(false);
            }

            consolidatedProperties.put(resultingProperty.name, new BeanPropertyInfo(
                    resultingProperty, propertyInfo.get(0).beanSuperclassProperty, propertyInfo.get(0).readOnlyOverridesMutable));
        }


        Map<String, BeanPropertyInfo> beanProperties = new LinkedHashMap<>();
        for (BeanPropertyInfo pi : consolidatedProperties.values()) {
            beanProperties.put(pi.property.name, new BeanPropertyInfo(pi.property, pi.property, pi.readOnlyOverridesMutable));
        }

        /*
        Overriding rules: for the property definitions overridden in subinterface
            - check if property type is the same or more narrow
            - if superproperty is initial check if property has @Initial or default value defined
            - if superproperty has default value defined check if overridden property has also default value defined
            - if superproperty is mutable but property is marked as readonly:
                On bean generation:
                - en mutable accessor (property(value)) saltar una excepción (UnmodifiableException)
                - en setter (setProperty(value)) no hacer nada (empty)
                - generar clone-setter: ofProperty(value)
         */
        for (DataClassInfo.Property property : dataClassInfo.properties) {
            final BeanPropertyInfo superPropertyInfo = consolidatedProperties.get(property.name);
            if (superPropertyInfo == null) {
                // property is new and does not override any another one
                //consolidatedProperties.put(property.name, property);
                beanProperties.put(property.name, new BeanPropertyInfo(property));
                continue;
            }
            final DataClassInfo.Property superProperty = superPropertyInfo.property;

            if (!procEnv.getTypeUtils().isAssignable(property.type, superProperty.type)) {
                // property.type is not assignable to superProperty.type
                throw new RuntimeException("Property type of '" + dataClassInfo.className().simpleName() + "." +
                        property.name + "' is incompatible with one of it's inherited superinterfaces");
            }

            if (superProperty.isInitial) {
                if (!property.isInitial && !property.hasDefaultValue)
                    throw new RuntimeException("Property '" + dataClassInfo.className().simpleName() + "." +
                            property.name + "' must be declared as @Initial or define it's default value " +
                            "because it overrides an @Initial property declared in one of it's supertypes");
            }

            if (superProperty.hasDefaultValue && !property.hasDefaultValue)
                throw new RuntimeException("Property '" + dataClassInfo.className().simpleName() + "." +
                        property.name + "' must declare it's default value as it is declared in one of it's supertypes");

            // generate bean property
            if (dataClassInfo.inheritFromSuperclass && !dataClassInfo.superClasses.isEmpty()) {
                final List<BeanPropertyInfo> beanSuperclassProperties = beanProperties(
                        dataClassInfo.superClasses.get(0));
                final BeanPropertyInfo beanSuperclassProperty = beanSuperclassProperties.stream()
                        .filter(it -> property.name.equals(it.property.name))
                        .findAny().orElse(null);
                if (beanSuperclassProperty != null) {
                    if (!beanSuperclassProperty.property.isReadOnly && property.isReadOnly) {
                        // special case: override readonly property
                        beanProperties.put(property.name,
                                new BeanPropertyInfo(property, beanSuperclassProperty.property, true));
                    } else {
                        beanProperties.put(property.name,
                                new BeanPropertyInfo(property, beanSuperclassProperty.property,
                                        beanSuperclassProperty.readOnlyOverridesMutable));
                    }
                } else {
                    beanProperties.put(property.name, new BeanPropertyInfo(property));
                }
            } else {
                beanProperties.put(property.name, new BeanPropertyInfo(property));
            }
        }

        final List<BeanPropertyInfo> beanPropertyInfos = new ArrayList<>(beanProperties.values());
        cache.put(dataClassInfo.className().toString(), beanPropertyInfos);
        return beanPropertyInfos;
    }
}

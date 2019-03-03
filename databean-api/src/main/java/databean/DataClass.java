package databean;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface DataClass {
    /**
     * Generate bean getters/setters in implementing class
     */
    boolean generateBeanAccessors() default true;

    /**
     * Generated bean will extend a bean generated from first inherited data interface
     */
    boolean inheritFromSuperclass() default true;

    /**
     * Abstract data class can not have instances
     */
    boolean isAbstract() default false;
}

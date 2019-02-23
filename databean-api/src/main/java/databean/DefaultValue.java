package databean;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates the property's default value.
 * Value parameter must be a valid java expression that returns correspondent value.
 * If value parameter is not set a default interface method must be provided that returns value.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface DefaultValue {
    String value() default "";
}

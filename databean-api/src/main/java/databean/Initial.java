package databean;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that the property must be set within the primary constructor
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface Initial {
}

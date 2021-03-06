package databean;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Computed property is a read-only property that is computed every time it is called.
 * No value will be stored in any field.
 * TODO: really necessary? May be done with BusinessMethod
 */
@Deprecated
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface Computed {
}

package databean;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Fixed properties are DataClass-specific and contains a read-only default value that and can not be changed
 * neither by constructor nor by copy-and-set methods. Subclasses may override fixed properties.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface Fixed {
}

package su.demands.d4jdframework.command.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface SubcommandHandler {
    String[] value(); // Aliases
    String description() default "no description";
    boolean isRequired() default false;
}

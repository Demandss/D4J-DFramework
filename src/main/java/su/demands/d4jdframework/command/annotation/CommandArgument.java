package su.demands.d4jdframework.command.annotation;

import discord4j.core.object.command.ApplicationCommandOption;

import java.lang.annotation.*;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CommandArgument {

    /**
     * Display name.
     */
    String name() default "";
    /**
     * Displayed description.
     */
    String description() default "no description";

    /**
     * You can set the type of data that can be entered by the user in the command argument.
     *
     * Auto-cast to ApplicationCommandOption.Type.ATTACHMENT is not supported use Snowflake for parameter type.
     */
    ApplicationCommandOption.Type type() default ApplicationCommandOption.Type.STRING;
    /**
     * If you do not include this, then a String type argument will always come in handy in your method.
     */
    boolean isRequired() default false;
}
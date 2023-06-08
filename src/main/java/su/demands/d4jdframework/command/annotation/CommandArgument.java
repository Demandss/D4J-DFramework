package su.demands.d4jdframework.command.annotation;

import discord4j.core.object.command.ApplicationCommandOption;

import java.lang.annotation.*;

@Repeatable(CommandArguments.class)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface CommandArgument {
    String name();
    String description() default "no description";
    ApplicationCommandOption.Type type() default ApplicationCommandOption.Type.STRING;
    boolean isRequired() default false;
}
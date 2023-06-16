package su.demands.d4jdframework.command;

import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClient;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.entity.Role;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.Channel;
import discord4j.discordjson.json.ApplicationCommandOptionData;
import discord4j.discordjson.json.ApplicationCommandRequest;
import lombok.val;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import su.demands.d4jdframework.command.annotation.CommandArgument;
import su.demands.d4jdframework.command.executor.Executor;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class Command extends CommandBase {
    private final Logger LOGGER = LoggerFactory.getLogger(Command.class);

    public Command(@NotNull DiscordClient client, String label, String... aliases) {
        super(client, label, aliases);

        GatewayDiscordClient gatewayClient = client.login().block();

        registerDiscordTypesParser(gatewayClient);

        List<ApplicationCommandOptionData> options = new ArrayList<>();

        for (Method method : getClass().getMethods()) {
            Parameter[] params = method.getParameters();
            for (Parameter param : List.of(params)) {
                CommandArgument argumentHandler = param.getAnnotation(CommandArgument.class);
                if (argumentHandler == null) continue;
                options.add(ApplicationCommandOptionData.builder()
                        .name(argumentHandler.name().equals("") ? param.getName() : argumentHandler.name())
                        .description(argumentHandler.description())
                        .type(argumentHandler.type().getValue())
                        .required(argumentHandler.isRequired())
                        .build());
            }
        }

        for (val alias : getAliases()) {
            ApplicationCommandRequest greetCmdRequest = ApplicationCommandRequest.builder()
                    .name(alias)
                    .description(getDescription())
                    .addAllOptions(options)
                    .build();

            if (isGlobalCommand()) {
                client.getApplicationService()
                        .createGlobalApplicationCommand(getApplicationId(),greetCmdRequest)
                        .doOnNext(cmd -> LOGGER.debug("Successfully registered Global Command :: {}", cmd.name()))
                        .doOnError(e -> LOGGER.error("Failed to register Global Command :: {} ERROR :: {}", alias, e.getMessage()))
                        .subscribe();
            } else {
                client.getApplicationService()
                        .createGuildApplicationCommand(getApplicationId(),getGuildId(),greetCmdRequest)
                        .doOnNext(cmd -> LOGGER.debug("Successfully registered for Guild :: {} Command :: {}", getGuildId(), cmd.name()))
                        .doOnError(e -> LOGGER.error("Failed to register for Guild :: {} Command :: {} ERROR :: {}", alias, getGuildId(), e.getMessage()))
                        .subscribe();
            }
        }

        gatewayClient.on(ChatInputInteractionEvent.class, event -> {
            if (Arrays.stream(getAliases()).map(String::toLowerCase).allMatch(event.getCommandName()::equals))
            {
                return execute0(new Executor(event));
            }
            return Mono.empty();
        }).subscribe();

        loadSubcommands();
    }

    protected void registerDiscordTypesParser(GatewayDiscordClient gatewayClient) {
        getTypeParser()
                .registerParser(Snowflake.class, (input, helper) -> Snowflake.of(input.trim()))
                .registerParser(User.class, (input, helper) -> gatewayClient.getUserById(Snowflake.of(input)).block())
                .registerParser(Role.class, (input, helper) -> {
                    if (!input.contains("&"))
                    {
                        String message = "\"%s\" is not parsable to a DiscordRole.";
                        throw new IllegalArgumentException(String.format(message, input));
                    }
                    String[] out = input.split("&");
                    return gatewayClient.getRoleById(Snowflake.of(out[1]), Snowflake.of(out[0])).block();
                })
                .registerParser(Channel.class, (input, helper) -> gatewayClient.getChannelById(Snowflake.of(input)).block());
    }
}

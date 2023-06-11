package su.demands.d4jdframework.command;

import discord4j.core.DiscordClient;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.discordjson.json.ApplicationCommandOptionData;
import discord4j.discordjson.json.ApplicationCommandRequest;
import lombok.Getter;
import lombok.Setter;
import lombok.val;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import su.demands.d4jdframework.command.annotation.CommandArgument;
import su.demands.d4jdframework.command.annotation.CommandArguments;
import su.demands.d4jdframework.command.annotation.CommandHandler;
import su.demands.d4jdframework.command.executor.Executor;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class Command extends CommandBase {
    private final Logger LOGGER = LoggerFactory.getLogger(Command.class);

    @Getter @Setter
    private boolean isGlobalCommand = true;

    @Getter @Setter
    private long guildId;

    public Command(@NotNull DiscordClient client, String label, String... aliases) {
        super(client, label, aliases);

        List<ApplicationCommandOptionData> options = new ArrayList<>();

        for (Method method : getClass().getMethods()) {
            CommandArgument argumentHandler = method.getAnnotation(CommandArgument.class);
            CommandHandler commandHandler = method.getAnnotation(CommandHandler.class);
            if (argumentHandler == null || commandHandler == null)
            {
                commandHandler = method.getAnnotation(CommandHandler.class);
                CommandArguments argumentsHandler = method.getAnnotation(CommandArguments.class);
                if (argumentsHandler == null || commandHandler == null) continue;
                List<CommandArgument> commandArguments = List.of(argumentsHandler.value());
                commandArguments.forEach(arg -> options.add(ApplicationCommandOptionData.builder()
                        .name(arg.name())
                        .description(arg.description())
                        .type(arg.type().getValue())
                        .required(arg.isRequired())
                        .build())
                );
            } else {
                options.add(ApplicationCommandOptionData.builder()
                        .name(argumentHandler.name())
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

            if (isGlobalCommand) {
                client.getApplicationService()
                        .createGlobalApplicationCommand(getApplicationId(),greetCmdRequest)
                        .doOnNext(cmd -> LOGGER.debug("Successfully registered Global Command :: {}", cmd.name()))
                        .doOnError(e -> LOGGER.error("Failed to register global commands :: {}", e.getMessage()))
                        .subscribe();
            } else {
                client.getApplicationService()
                        .createGuildApplicationCommand(getApplicationId(),guildId,greetCmdRequest)
                        .doOnNext(cmd -> LOGGER.debug("Successfully registered for Guild :: {} Command :: {}", guildId, cmd.name()))
                        .doOnError(e -> LOGGER.error("Failed to register global commands :: {}", e.getMessage()))
                        .subscribe();
            }
        }

        GatewayDiscordClient gatewayClient = client.login().block();

        gatewayClient.on(ChatInputInteractionEvent.class, event -> {
            if (Arrays.stream(getAliases()).map(String::toLowerCase).allMatch(event.getCommandName()::equals))
            {
                return execute0(event.getOptions(),new Executor(event));
            }
            return Mono.empty();
        }).subscribe();

        loadSubcommands();
    }
}

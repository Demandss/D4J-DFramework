package su.demands.d4jdframework.command;

import com.github.drapostolos.typeparser.TypeParser;
import com.github.drapostolos.typeparser.TypeParserBuilder;
import discord4j.core.DiscordClient;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandOption;
import discord4j.core.object.entity.Role;
import discord4j.discordjson.json.ApplicationCommandData;
import discord4j.discordjson.json.ApplicationCommandOptionData;
import discord4j.discordjson.json.ApplicationCommandRequest;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import su.demands.d4jdframework.command.annotation.CommandArgument;
import su.demands.d4jdframework.command.annotation.CommandHandler;
import su.demands.d4jdframework.command.annotation.SubcommandHandler;
import su.demands.d4jdframework.command.executor.Executor;

import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public abstract class CommandBase {

    private final Logger LOGGER = LoggerFactory.getLogger(CommandBase.class);

    @Getter
    @Setter
    private String description = "description";

    @Getter
    private final String[] aliases;

    @Getter
    @Setter
    private CommandHandle handler;

    private final DiscordClient client;

    private final long applicationId;

    @Getter
    @Setter
    private long guildId;

    @Getter
    private TypeParserBuilder typeParser = TypeParser.newBuilder();

    /*public CommandBase(@NotNull DiscordClient client, String label, String... aliases) {
        this(client, toArray(label, aliases));
    }*/

    public CommandBase(@NotNull DiscordClient client, long guildId, String... aliases) {
        this.aliases = aliases;
        this.client = client;
        this.guildId = guildId;
        applicationId = Objects.requireNonNull(getClient().getApplicationId().block());
        loadHandle();
    }

    public boolean isGlobalCommand() {
        return guildId != 0;
    }

    protected DiscordClient getClient() {
        return client;
    }

    protected long getApplicationId() {
        return applicationId;
    }

    protected final Publisher<?> execute0(Executor executor) {
        AtomicReference<CommandBase> handle = new AtomicReference<>(this);
        List<ApplicationCommandInteractionOption> options = executor.getChatInputInteractionEvent().getOptions();

        options.forEach(option -> {
            if (option.getType() == ApplicationCommandOption.Type.SUB_COMMAND) {
                var subcommand = subcommands.get(option.getName());
                if (subcommand != null) {
                    handle.set(subcommand);
                }
            }
        });

        if (handle.get() == this) {
            return getHandler().execute(executor);
        } else
            return handle.get().execute0(executor);
    }

    private static String[] toArray(String label, String[] aliases) {
        String[] result = new String[aliases.length + 1];
        result[0] = label;
        int index = 1;
        for (var alias : aliases) {
            result[index] = alias;
            index++;
        }
        return result;
    }

    public String getLabel() {
        return aliases[0].toLowerCase();
    }

    Object[] getMethodArguments0(Executor executor, Method method) {
        List<Object> args = new ArrayList<>(Collections.emptyList());
        List<Parameter> parameters = List.of(method.getParameters());
        List<ApplicationCommandInteractionOption> options = executor.getChatInputInteractionEvent().getOptions();

        args.add(0,parameters.get(0).getType().cast(executor));

        for (Parameter param : parameters) {
            CommandArgument commandArgument = param.getAnnotation(CommandArgument.class);
            if (commandArgument == null) continue;
            String argName = commandArgument.name().equals("") ? param.getName() : commandArgument.name();
            ApplicationCommandInteractionOption option = null;

            for (ApplicationCommandInteractionOption it : options)
            {
                if (it.getName().equals(argName)) {
                    option = it;
                    break;
                }
            }

            if (option == null || option.getValue().isEmpty()) {
                args.add(param.getType().cast(null));
            } else {
                String value = option.getValue().get().getRaw();

                if (param.getType() == String.class || !commandArgument.isRequired()) {
                    args.add(param.getType().cast(value));
                } else {
                    Class<?> castType = param.getType();
                    if (param.getType().isPrimitive())
                        castType = MethodType.methodType(param.getType()).wrap().returnType();

                    if (commandArgument.type() == ApplicationCommandOption.Type.ROLE)
                    {
                        value += "&" + executor.interaction().getGuildId().get().asString();
                    }

                    args.add(castType.cast(typeParser.build().parseType(value,castType)));
                }
            }
        }
        return args.toArray();
    }

    public void loadHandle() {
        for (Method method : getClass().getMethods()) {
            CommandHandler handler = method.getAnnotation(CommandHandler.class);
            if (handler == null) continue;
            Class<?>[] parameters = method.getParameterTypes();
            if (parameters.length < 1) continue;

            boolean useArguments = parameters.length > 1;

            this.handler = new CommandHandle() {
                @SneakyThrows
                @Override
                public Publisher<?> execute(Executor executor) {
                    if (useArguments) {
                        if (parameters[1].getTypeName().toLowerCase().contains("object[]"))
                            return (Publisher<?>) method.invoke(CommandBase.this, parameters[0].cast(executor), getMethodArguments0(executor, method));
                        else
                            return (Publisher<?>) method.invoke(CommandBase.this, getMethodArguments0(executor, method));
                    }
                    return (Publisher<?>) method.invoke(CommandBase.this, parameters[0].cast(executor));
                }
            };

            if (!handler.description().equals("no description"))
                setDescription(handler.description());
        }
    }

    @Getter
    private final Map<String, CommandBase> subcommands = new HashMap<>();

    public void addSubcommand(CommandBase subcommand) {
        if (subcommands.containsValue(subcommand)) {
            throw new IllegalStateException("subcommand is already registered");
        }
        for (var alias : subcommand.getAliases()) {
            subcommands.put(alias.toLowerCase(), subcommand);
        }
    }

    public void loadSubcommands() {
        for (Method method : getClass().getMethods()) {
            SubcommandHandler handler = method.getAnnotation(SubcommandHandler.class);
            if (handler == null) {
                continue;
            }
            Class<?>[] parameters = method.getParameterTypes();
            if (parameters.length < 1) continue;

            boolean useArguments = parameters.length > 1;

            CommandBase handle = new CommandBase(getClient(), guildId, handler.value()) {
                @SneakyThrows
                @CommandHandler
                public Publisher<?> execute(Executor executor, Object[] args) {
                    if (useArguments) {
                        return (Publisher<?>) method.invoke(CommandBase.this, args);
                    }
                    return (Publisher<?>) method.invoke(CommandBase.this, parameters[0].cast(executor));
                }
            };
            if (!handler.description().equals("no description")) {
                handle.setDescription(handler.description());
            }

            for (var alias : getAliases()) {

                List<ApplicationCommandOptionData> options = new ArrayList<>();

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

                for (var handlerAlias : handler.value())
                {
                    ApplicationCommandRequest greetCmdRequest = ApplicationCommandRequest.builder()
                            .name(alias)
                            .description(getDescription())
                            .addOption(ApplicationCommandOptionData.builder()
                                    .name(handlerAlias)
                                    .description(handler.description())
                                    .type(ApplicationCommandOption.Type.SUB_COMMAND.getValue())
                                    .addAllOptions(options)
                                    .build())
                            .build();

                    if (isGlobalCommand())
                        getClient().getApplicationService()
                                .createGlobalApplicationCommand(getApplicationId(), greetCmdRequest)
                                .doOnNext(cmd -> LOGGER.debug("Successfully registered Global SubCommand :: {} fore Command :: {}", handlerAlias, alias))
                                .doOnError(e -> LOGGER.error("Failed to register Global SubCommand :: {} ERROR :: {}", handlerAlias, e.getMessage()))
                                .subscribe();
                    else
                        getClient().getApplicationService()
                                .createGuildApplicationCommand(getApplicationId(), getGuildId(), greetCmdRequest)
                                .doOnNext(cmd -> LOGGER.debug("Successfully registered for Guild :: {} SubCommands :: {} fore Command :: {}", getGuildId(), handler.value(), alias))
                                .doOnError(e -> LOGGER.error("Failed to register for Guild :: {} SubCommand :: {} ERROR :: {}", getGuildId(), handlerAlias, e.getMessage()))
                                .subscribe();
                }
            }

            handle.loadSubcommands();
            addSubcommand(handle);
        }
    }
}

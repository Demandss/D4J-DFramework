package su.demands.d4jdframework.command;

import discord4j.core.DiscordClient;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.command.ApplicationCommandOption;
import discord4j.discordjson.json.ApplicationCommandData;
import discord4j.discordjson.json.ApplicationCommandOptionData;
import discord4j.discordjson.json.ApplicationCommandRequest;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.val;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
import su.demands.d4jdframework.command.annotation.CommandArgument;
import su.demands.d4jdframework.command.annotation.CommandArguments;
import su.demands.d4jdframework.command.annotation.CommandHandler;
import su.demands.d4jdframework.command.annotation.SubcommandHandler;
import su.demands.d4jdframework.command.executor.Executor;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public abstract class CommandBase {

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

    public CommandBase(@NotNull DiscordClient client, String label, String... aliases) {
        this(client,toArray(label, aliases));
    }

    public CommandBase(@NotNull DiscordClient client, String[] aliases) {
        this.aliases = aliases;
        this.client = client;
        applicationId = Objects.requireNonNull(getClient().getApplicationId().block());
        loadHandle();
    }

    protected DiscordClient getClient() {
        return client;
    }

    protected long getApplicationId() {
        return applicationId;
    }

    protected final Publisher<?> execute0(List<ApplicationCommandInteractionOption> options, Executor executor) {
        AtomicReference<CommandBase> handle = new AtomicReference<>(this);

        options.forEach(option -> {
            if (option.getType() == ApplicationCommandOption.Type.SUB_COMMAND) {
                val subcommand = subcommands.get(option.getName());
                if (subcommand != null) {
                    handle.set(subcommand);
                }
            }
        });

        if (handle.get() == this)
        {
            List<Object> args = new ArrayList<>(Collections.emptyList());

            options.forEach(option -> args.add(option.getValue()
                    .map(ApplicationCommandInteractionOptionValue::asString)
                    .orElse("")));
            String[] arguments = new String[args.size()];
            args.toArray(arguments);
            return getHandler().execute(executor,arguments);
        }
        else
            return handle.get().execute0(options,executor);
    }

    private static String[] toArray(String label, String[] aliases) {
        String[] result = new String[aliases.length + 1];
        result[0] = label;
        int index = 1;
        for (val alias : aliases) {
            result[index] = alias;
            index++;
        }
        return result;
    }

    public String getLabel() {
        return aliases[0].toLowerCase();
    }

    public void loadHandle() {
        for (Method method : getClass().getMethods()) {
            CommandHandler handler = method.getAnnotation(CommandHandler.class);
            if (handler == null) continue;
            Class<?>[] parameters = method.getParameterTypes();
            if (parameters.length != 1 && parameters.length != 2) continue;

            boolean useArguments = parameters.length == 2;

            this.handler = new CommandHandle() {
                @SneakyThrows
                @Override
                public Publisher<?> execute(Executor executor, String[] arguments) {
                    if (useArguments)
                        return (Publisher<?>) method.invoke(CommandBase.this,parameters[0].cast(executor),arguments);
                    else
                        return (Publisher<?>) method.invoke(CommandBase.this,parameters[0].cast(executor));
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
        for (val alias : subcommand.getAliases()) {
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
            if (parameters.length != 1 && parameters.length != 2) {
                continue;
            }

            boolean useArguments = parameters.length == 2;

            CommandBase handle = new CommandBase(getClient(),handler.value()) {
                @SneakyThrows
                @CommandHandler
                public Publisher<?> execute(Executor executor, String[] arguments) {
                    if (useArguments)
                        return (Publisher<?>) method.invoke(CommandBase.this,parameters[0].cast(executor),arguments);
                    else
                        return (Publisher<?>) method.invoke(CommandBase.this,parameters[0].cast(executor));
                }
            };
            if (!handler.description().equals("no description")) {
                handle.setDescription(handler.description());
            }

            Map<String, ApplicationCommandData> discordCommands = getClient().getApplicationService()
                    .getGlobalApplicationCommands(getApplicationId())
                    .collectMap(ApplicationCommandData::name)
                    .block();

            ApplicationCommandData discordGreetCmd = discordCommands.get(getLabel());
            long discordGreetCmdId = discordGreetCmd.id().asLong();

            List<ApplicationCommandOptionData> options = new ArrayList<>();
            for (Method method0 : getClass().getMethods()) {
                CommandArgument argumentHandler = method0.getAnnotation(CommandArgument.class);
                SubcommandHandler subcommandHandler = method0.getAnnotation(SubcommandHandler.class);
                if (argumentHandler == null || subcommandHandler == null) {
                    subcommandHandler = method0.getAnnotation(SubcommandHandler.class);
                    CommandArguments argumentsHandler = method0.getAnnotation(CommandArguments.class);
                    if (argumentsHandler == null || subcommandHandler == null) continue;
                    List<CommandArgument> commandArguments = List.of(argumentsHandler.value());
                    System.out.println(commandArguments);
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

            ApplicationCommandRequest greetCmdRequest = ApplicationCommandRequest.builder()
                .name(getLabel())
                .description(getDescription())
                .addOption(ApplicationCommandOptionData.builder()
                    .name(handler.value()[0])
                    .description(handler.description())
                    .type(ApplicationCommandOption.Type.SUB_COMMAND.getValue())
                    .addAllOptions(options)
                    .required(handler.isRequired())
                    .build()
            ).build();

            getClient().getApplicationService()
                    .modifyGlobalApplicationCommand(getApplicationId(), discordGreetCmdId, greetCmdRequest)
                    .subscribe();

            handle.loadSubcommands();
            addSubcommand(handle);
        }
    }
}

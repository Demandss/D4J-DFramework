package su.demands.d4jdframework.command.example;

import discord4j.core.DiscordClient;
import org.reactivestreams.Publisher;
import su.demands.d4jdframework.command.Command;
import su.demands.d4jdframework.command.annotation.CommandArgument;
import su.demands.d4jdframework.command.annotation.CommandHandler;
import su.demands.d4jdframework.command.annotation.SubcommandHandler;
import su.demands.d4jdframework.command.executor.Executor;

public class PingCommand extends Command {

    public PingCommand(DiscordClient client) {
        super(client,"ping");
    }

    @CommandHandler(description = "this is ping command ¯\\_(ツ)_/¯")
    public Publisher<?> execute(Executor executor,
                                @CommandArgument(description = "multiplier ¯\\_(ツ)_/¯", isRequired = true) int multiplier,
                                @CommandArgument(description = "maybe pong ?") String input) {
        var event = executor.chatInputInteractionEvent();
        if (input != null)
            if (input.toLowerCase().contains("pong"))
                return event.reply("Ping!");
            else if (input.toLowerCase().contains("ping"))
                return event.reply("Ping\nPong!");
            else
                return event.reply("What should i answer?");
        if (multiplier > 0) {
            return event.reply("Pong\nPong!".repeat(multiplier));
        }
        return event.reply("Pong!");
    }

    @SubcommandHandler({"pong"})
    public Publisher<?> pong(Executor executor) {
        var event = executor.chatInputInteractionEvent();
        return event.reply("Ping!");
    }
}

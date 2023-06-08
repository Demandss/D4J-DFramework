package su.demands.d4jdframework.command.example;

import discord4j.core.DiscordClient;
import lombok.val;
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
    @CommandArgument(name = "input", description = "maybe pong ?")
    @CommandArgument(name = "input\uD835\uDEA12", description = "maybe ping ?")
    public Publisher<?> execute(Executor executor, String... arg) {
        val event = executor.chatInputInteractionEvent();
        if (arg.length == 1)
            if (arg[0].toLowerCase().contains("pong"))
                return event.reply("Ping!");
            else if (arg[0].toLowerCase().contains("ping"))
                return event.reply("Ping\nPong!");
            else
                return event.reply("What should i answer?");
        if (arg.length == 2)
            if (arg[0].toLowerCase().contains("ping") && arg[1].toLowerCase().contains("ping"))
                return  event.reply("Pong\nPong!");
        return event.reply("Pong!");
    }

    @SubcommandHandler({"pong"})
    public Publisher<?> pong(Executor executor) {
        val event = executor.chatInputInteractionEvent();
        return event.reply("Ping!");
    }
}

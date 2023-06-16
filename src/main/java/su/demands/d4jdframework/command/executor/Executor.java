package su.demands.d4jdframework.command.executor;

import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.Interaction;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.object.entity.channel.PrivateChannel;
import lombok.Getter;
import reactor.core.publisher.Mono;

import java.util.LinkedList;
import java.util.List;

public record Executor(@Getter ChatInputInteractionEvent chatInputInteractionEvent) {

    public Interaction interaction() {
        return getChatInputInteractionEvent().getInteraction();
    }

    public User handle() { return interaction().getUser(); };

    public Mono<MessageChannel> currentChannel() { return interaction().getChannel(); }
}

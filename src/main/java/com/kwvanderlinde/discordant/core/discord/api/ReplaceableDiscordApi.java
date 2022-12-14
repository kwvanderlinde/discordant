package com.kwvanderlinde.discordant.core.discord.api;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class ReplaceableDiscordApi implements DiscordApi {
    private final List<MessageHandler> messageHandlers = new ArrayList<>();
    private DiscordApi api;


    public ReplaceableDiscordApi(@Nonnull DiscordApi initialApi) {
        this.api = initialApi;
    }

    public void replace(Supplier<DiscordApi> factory) {
        this.api.close();  // TODO Wait for it to finish. Rather, chain the rest after it.
        this.api = factory.get();
        for (final var handler : messageHandlers) {
            this.api.addHandler(handler);
        }
    }

    @Override
    public void addHandler(MessageHandler messageHandler) {
        this.messageHandlers.add(messageHandler);
        this.api.addHandler(messageHandler);
    }

    @Override
    public void close() {
        this.api.close();
        this.api = new NullDiscordApi();
    }

    @Override
    public @Nonnull String getBotName() {
        return this.api.getBotName();
    }

    @Override
    public @Nullable Guild getGuild() {
        return this.api.getGuild();
    }

    @Override
    public @Nullable User getUserById(String userId) {
        return this.api.getUserById(userId);
    }

    @Override
    public void sendEmbed(@Nonnull MessageEmbed e) {
        this.api.sendEmbed(e);
    }

    @Override
    public void sendEmbed(@Nonnull MessageChannel ch, @Nonnull MessageEmbed e) {
        this.api.sendEmbed(ch, e);
    }

    @Override
    public void postConsoleMessage(@Nonnull String msg) {
        this.api.postConsoleMessage(msg);
    }

    @Override
    public void setTopic(@Nonnull String msg) {
        this.api.setTopic(msg);
    }
}

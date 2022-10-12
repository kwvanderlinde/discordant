package com.kwvanderlinde.discordant.mc.events;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.advancements.Advancement;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

public class PlayerEvents {
    /**
     * An event that is called when a player sends a message via chat.
     */
    public static final Event<ChatMessageSent> CHAT_MESSAGE_SENT = EventFactory.createArrayBacked(ChatMessageSent.class, callbacks -> (player, msg, text) -> {
        for (final var callback : callbacks) {
            callback.onMessage(player, msg, text);
        }
    });

    /**
     * An event that is called when a player dies.
     */
    public static final Event<Death> DEATH = EventFactory.createArrayBacked(Death.class, callbacks -> (player, damageSource) -> {
        for (final var callback : callbacks) {
            callback.afterDeath(player, damageSource);
        }
    });

    /**
     * An event that is called when a player is awarded an advancement.
     */
    public static final Event<AdvancementAwarded> ADVANCMENT_AWARDED = EventFactory.createArrayBacked(AdvancementAwarded.class, callbacks -> (player, advancement) -> {
        for (final var callback : callbacks) {
            callback.advancementAwarded(player, advancement);
        }
    });

    @FunctionalInterface
    public interface Death {
        /**
         * Called when a player dies for realsies.
         *
         * @param player the player
         * @param damageSource the fatal damage damageSource
         */
        void afterDeath(ServerPlayer player, DamageSource damageSource);
    }

    @FunctionalInterface
    public interface AdvancementAwarded {
        /**
         * Called when a player dies for realsies.
         *
         * @param player the player
         * @param advancement the awarded advancment
         */
        void advancementAwarded(ServerPlayer player, Advancement advancement);
    }

    @FunctionalInterface
    public interface ChatMessageSent {
        /**
         * Called when a player sends a chat message.
         *
         * @param player the player
         * @param message the message
         * @param textComponent the message as a component?
         */
        void onMessage(ServerPlayer player, String message, MutableComponent textComponent);
    }
}
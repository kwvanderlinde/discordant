package com.kwvanderlinde.discordant.mc.mixin;

import com.kwvanderlinde.discordant.mc.events.PlayerEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.FilteredText;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ChatMixins {

    @Shadow
    public ServerPlayer player;

    @Inject(method = "broadcastChatMessage(Lnet/minecraft/server/network/FilteredText;)V", at = @At(value = "INVOKE", target = "net/minecraft/server/players/PlayerList.broadcastChatMessage (Lnet/minecraft/server/network/FilteredText;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/resources/ResourceKey;)V", ordinal = 0, shift = At.Shift.BEFORE))
    private void onPlayerMessageEvent(FilteredText<PlayerChatMessage> filteredText, CallbackInfo ci) {
        String msg = filteredText.raw().signedContent().getString();
        MutableComponent text = Component.translatable("chat.type.text", this.player.getDisplayName(), msg);
        PlayerEvents.CHAT_MESSAGE_SENT.invoker().onMessage(player, msg, text);
    }
}

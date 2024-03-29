package com.kwvanderlinde.discordant.mc.mixin;

import com.kwvanderlinde.discordant.mc.IServerPlayer;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixins extends Player implements IServerPlayer {
    @Shadow
    protected abstract boolean acceptsSystemMessages(boolean bl);

    @Shadow
    protected abstract boolean acceptsChatMessages();

    private boolean notifySound = true;

    public ServerPlayerMixins(Level level, BlockPos blockPos, float f, GameProfile gameProfile) {
        super(level, blockPos, f, gameProfile);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    public void readUserDiscordSettings(CompoundTag compoundTag, CallbackInfo ci) {
        if (compoundTag.contains("discordantData")) {
            CompoundTag tag = compoundTag.getCompound("discordantData");
            this.notifySound = tag.getBoolean("soundnotify");
        }
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    public void saveUserDiscordSettings(CompoundTag compoundTag, CallbackInfo ci) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("soundnotify", notifySound);
        compoundTag.put("discordantData", tag);
    }

    @Override
    public boolean getNotifyState() {
        return notifySound;
    }

    @Override
    public void setNotifyState(boolean bl) {
        this.notifySound = bl;
    }

    @Override
    public boolean isAcceptingChatMessages() {
        return acceptsChatMessages();
    }


}

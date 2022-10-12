package com.kwvanderlinde.discordant.mc.discord;

import com.kwvanderlinde.discordant.core.Discordant;
import com.kwvanderlinde.discordant.core.modinterfaces.Advancement;
import com.kwvanderlinde.discordant.core.modinterfaces.Events;
import com.kwvanderlinde.discordant.core.modinterfaces.Integration;
import com.kwvanderlinde.discordant.core.modinterfaces.Player;
import com.kwvanderlinde.discordant.core.modinterfaces.Server;
import com.kwvanderlinde.discordant.mc.DiscordantCommands;
import com.kwvanderlinde.discordant.mc.ProfileLinkCommand;
import com.kwvanderlinde.discordant.mc.events.PlayerEvents;
import com.kwvanderlinde.discordant.mc.language.ServerLanguage;
import com.kwvanderlinde.discordant.mc.messages.ComponentRenderer;
import com.kwvanderlinde.discordant.mc.mixin.ServerLoginPacketListenerImpl_GameProfileAccessor;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerLoginConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.dedicated.DedicatedServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Stream;

public class DiscordantModInitializer implements DedicatedServerModInitializer {
    public static Logger logger = LogManager.getLogger("Discordant");

    public static Discordant core;
    public static DedicatedServer server;
    private static final ServerLanguage language = new ServerLanguage();

    private static final class ServerAdapter implements Server {
        private final net.minecraft.server.MinecraftServer server;

        public ServerAdapter(net.minecraft.server.MinecraftServer server) {
            this.server = server;
        }

        @Override
        public int getTickCount() {
            return server.getTickCount();
        }

        @Override
        public int getPlayerCount() {
            return server.getPlayerCount();
        }

        @Override
        public int getMaxPlayers() {
            return server.getMaxPlayers();
        }

        // TODO Accept a UUID instead.
        @Override
        public Player getPlayer(UUID uuid) {
            final var internalPlayer = server.getPlayerList().getPlayer(uuid);
            if (internalPlayer == null) {
                return null;
            }
            return new PlayerAdapter(internalPlayer.getUUID(), internalPlayer.getScoreboardName());
        }

        @Override
        public Stream<Player> getAllPlayers() {
            return server.getPlayerList().getPlayers().stream().map(p -> new PlayerAdapter(
                    p.getUUID(),
                    p.getScoreboardName()
            ));
        }

        @Override
        public String motd() {
            return server.getMotd();
        }
    }

    private record PlayerAdapter(UUID uuid, String name) implements Player {
    }


    private record AdvancementAdapter(String name, String title, String description) implements Advancement {
    }

    private static final class EventsAdapter implements Events {
        @Override
        public void onServerStarted(ServerStartedHandler handler) {
            ServerLifecycleEvents.SERVER_STARTED.register(server -> {
                // TODO Try to eliminate this property write.
                DiscordantModInitializer.server = (DedicatedServer) server;
                handler.started(new ServerAdapter(server));
            });
        }

        @Override
        public void onServerStopping(ServerStoppingHandler handler) {
            ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
                handler.stopping(new ServerAdapter(server));
            });
        }

        @Override
        public void onServerStopped(ServerStoppedHandler handler) {
            ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
                handler.stopped(new ServerAdapter(server));
            });
        }

        @Override
        public void onTickStart(TickStartHandler handler) {
            ServerTickEvents.START_SERVER_TICK.register(server -> {
                handler.tickStart(new ServerAdapter(server));
            });
        }

        @Override
        public void onTickEnd(TickEndHandler handler) {
            ServerTickEvents.END_SERVER_TICK.register(server -> {
                handler.tickEnd(new ServerAdapter(server));
            });
        }

        @Override
        public void onPlayerSentMessage(PlayerMessageSendHandler handler) {
            PlayerEvents.CHAT_MESSAGE_SENT.register((player, msg, textComponent) -> {
                handler.messageSent(
                        new PlayerAdapter(player.getUUID(), player.getScoreboardName()),
                        msg,
                        textComponent.getString()
                );
            });
        }

        @Override
        public void onPlayerJoinAttempt(PlayerJoinAttemptHandler handler) {
            ServerLoginConnectionEvents.QUERY_START.register((netHandler, server, sender, synchronizer) -> {
                final var profile = ((ServerLoginPacketListenerImpl_GameProfileAccessor) netHandler).getGameProfile();
                handler.joinAttempted(
                        new PlayerAdapter(profile.getId(), profile.getName()),
                        reason -> netHandler.disconnect(reason.reduce(ComponentRenderer.instance()))
                );
            });
        }

        @Override
        public void onPlayerJoin(PlayerJoinHandler handler) {
            ServerPlayConnectionEvents.JOIN.register((netHandler, sender, server) -> {
                if (!netHandler.getConnection().isConnected()) {
                    // Player connection must have been rejected.
                    return;
                }

                handler.joined(
                        new PlayerAdapter(netHandler.getPlayer().getUUID(), netHandler.getPlayer().getScoreboardName())
                );
            });
        }

        @Override
        public void onPlayerDisconnect(PlayerDisconnectHandler handler) {
            ServerPlayConnectionEvents.DISCONNECT.register((netHandler, server) -> {
                handler.disconnected(
                        new PlayerAdapter(netHandler.getPlayer().getUUID(), netHandler.getPlayer().getScoreboardName())
                );
            });
        }

        @Override
        public void onPlayerDeath(PlayerDeathHandler handler) {
            PlayerEvents.DEATH.register((player, damageSource) -> {
                handler.died(
                        new PlayerAdapter(player.getUUID(), player.getScoreboardName()),
                        damageSource.getLocalizedDeathMessage(player).getString()
                );
            });
        }

        @Override
        public void onPlayerAdvancement(PlayerAdvancementHandler handler) {
            PlayerEvents.ADVANCMENT_AWARDED.register((player, advancement) -> {
                handler.advancementAwarded(
                        new PlayerAdapter(player.getUUID(), player.getScoreboardName()),
                        new AdvancementAdapter(
                                advancement.getDisplay().getFrame().getName(),
                                advancement.getDisplay().getTitle().getString(),
                                advancement.getDisplay().getDescription().getString()
                        )
                );
            });
        }
    }

    @Override
    public void onInitializeServer() {
        core = new Discordant(new Integration() {
            @Override
            public Path getConfigRoot() {
                return FabricLoader.getInstance().getConfigDir();
            }

            @Override
            public Server getServer() {
                return new ServerAdapter(server);
            }

            @Override
            public void enableBaseCommands() {
                CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
                    DiscordantCommands.register(dispatcher);
                });
            }

            @Override
            public void enableLinkingCommands() {
                CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
                    ProfileLinkCommand.register(dispatcher);
                });
            }

            @Override
            public Events events() {
                return new EventsAdapter();
            }
        });

        final var config = core.getConfig();
        language.loadAllLanguagesIncludingModded(config.targetLocalization, config.isBidirectional);
    }
}

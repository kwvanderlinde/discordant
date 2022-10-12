package com.kwvanderlinde.discordant.core.discord.api;

import com.kwvanderlinde.discordant.core.Discordant;
import com.kwvanderlinde.discordant.core.ServerCache;
import com.kwvanderlinde.discordant.core.config.DiscordantConfig;
import com.kwvanderlinde.discordant.core.discord.DiscordListener;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import okhttp3.OkHttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Timer;
import java.util.TimerTask;

public class JdaDiscordApi implements DiscordApi {
    private static final Logger logger = LogManager.getLogger(JdaDiscordApi.class);

    private final @Nonnull JDA jda;
    private final @Nonnull DiscordantConfig config;
    private final @Nonnull String botName;
    private final @Nullable TextChannel chatChannel;
    private final @Nullable TextChannel consoleChannel;
    private final @Nullable Guild guild;
    private boolean handleRateLimitations = true;
    // TODO Don't bother. Instead, replace this impl with a dummy impl.
    private boolean stopped = false;

    public JdaDiscordApi(@Nonnull Discordant discordant, @Nonnull ServerCache cache) throws InterruptedException {
        this.config = discordant.getConfig();

        jda = JDABuilder.createDefault(config.discord.token)
                        .setHttpClient(new OkHttpClient.Builder().build())
                        .setMemberCachePolicy(MemberCachePolicy.ALL)
                        .enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.MESSAGE_CONTENT)
                        .addEventListeners(new Object[]{new DiscordListener(this, discordant, config)})
                        .build();
        jda.awaitReady();

        if (!config.discord.serverId.isEmpty()) {
            guild = jda.getGuildById(config.discord.serverId);
            if (guild != null) {
                guild.loadMembers();
            }
        }
        else {
            guild = null;
        }
        botName = jda.getSelfUser().getName();
        chatChannel = jda.getTextChannelById(config.discord.chatChannelId);
        consoleChannel = jda.getTextChannelById(config.discord.consoleChannelId);
    }

    @Override
    public void sendMessage(@Nonnull MessageChannel ch, @Nonnull String msg) {
        if (!stopped) {
            ch.sendMessage(msg).submit(handleRateLimitations);
        }
    }

    @Override
    public void sendEmbed(@Nonnull MessageEmbed e) {
        if (!stopped && chatChannel != null) {
            chatChannel.sendMessageEmbeds(e).submit(handleRateLimitations);
        }
    }

    @Override
    public void sendEmbed(@Nonnull MessageChannel ch, @Nonnull MessageEmbed e) {
        if (!stopped) {
            ch.sendMessageEmbeds(e).submit(handleRateLimitations);
        }
    }

    @Override
    public void close() {
        stopped = true;
        // Give JDA a short time to finish up. But don't let it sit there forever, it's not worth it.
        jda.shutdown();

        final var timer = new Timer(true);
        timer.schedule(
                new TimerTask() {
                    @Override
                    public void run() {
                        try {
                            logger.info("JDA took too long to shut down. Cancelling requests");
                            final var numberOfRequests = jda.cancelRequests();
                            logger.info("Cancelled {} requests", numberOfRequests);
                            jda.shutdownNow();
                        }
                        finally {
                            timer.cancel();
                        }
                    }
                },
                3000
        );
    }

    @Override
    @Nonnull
    public String getBotName() {
        return botName;
    }

    @Override
    @Nullable
    public Guild getGuild() {
        return guild;
    }

    @Override
    public void postConsoleMessage(@Nonnull String msg) {
        if (!stopped && config.enableLogsForwarding && consoleChannel != null) {
            if (msg.length() > 1999) {
                msg = msg.substring(0, 1999);
            }
            sendMessage(consoleChannel, msg);
        }
    }

    @Override
    public void setTopic(@Nonnull String msg) {
        if (!stopped && chatChannel != null) {
            chatChannel.getManager().setTopic(msg).submit(handleRateLimitations);
        }
    }
}
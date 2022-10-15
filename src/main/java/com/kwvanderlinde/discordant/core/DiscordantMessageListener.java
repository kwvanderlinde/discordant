package com.kwvanderlinde.discordant.core;

import com.kwvanderlinde.discordant.core.discord.api.DiscordApi;
import com.kwvanderlinde.discordant.core.discord.api.MessageListener;
import com.kwvanderlinde.discordant.core.messages.SemanticMessage;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class DiscordantMessageListener implements MessageListener {
    private static final Logger logger = LogManager.getLogger(DiscordantMessageListener.class);
    private final Pattern pattern = Pattern.compile("(?<=!\\+).+?(?=!\\+|$|\\s)");
    private final Pattern pattern2 = Pattern.compile("(?<=<@).+?(?=>)");

    private final Discordant discordant;
    private final DiscordApi discordApi;

    public DiscordantMessageListener(Discordant discordant, DiscordApi discordApi) {
        this.discordant = discordant;
        this.discordApi = discordApi;
    }

    @Override
    public void onChatInput(MessageReceivedEvent e, String message) {
        if (!message.isEmpty()) {
            if (!message.startsWith("!@") && message.startsWith("!")) {
                handleCommandInput(e, message.substring(1));
            }
            else {
                handleChatMessage(e, message);
            }
        }
    }

    @Override
    public void onConsoleInput(MessageReceivedEvent e, String message) {
        logger.info("Discord user " + e.getAuthor().getName() + " running command " + message);
        discordant.getServer().runCommand(message);
    }

    @Override
    public void onBotPmInput(MessageReceivedEvent e, String message) {
        final var config = discordant.getConfig();
        if (!config.linking.enabled) {
            return;
        }

        discordant.verifyLinkedProfile(e.getChannel(), e.getAuthor(), message);
    }


    private void handleCommandInput(MessageReceivedEvent event, String command) {
        final var config = discordant.getConfig();
        final var server = discordant.getServer();

        if (command.startsWith("list")) {
            final var players = server.getAllPlayers().toList();
            if (players.isEmpty()) {
                final var message = config.discord.messages.noPlayers
                        .instantiate(discordant.serverScope(server));
                discordApi.sendEmbed(Discordant.buildMessageEmbed(message).build());
            }
            else {
                final var message = config.discord.messages.onlinePlayers
                        .instantiate(discordant.serverScope(server));

                discordApi.sendEmbed(Discordant.buildMessageEmbed(message).build());
            }
        }
    }

    public void handleChatMessage(MessageReceivedEvent e, String message) {
        Member member = e.getMember();
        final Set<String> playerNamesToNotify = new HashSet<>();
        if (message.contains("!+")) {
            playerNamesToNotify.addAll(pattern.matcher(message).results().map(matchResult -> matchResult.group(0).toLowerCase()).collect(Collectors.toSet()));
        }
        if (message.contains("<@")) {
            List<String> ids = pattern2.matcher(message).results().map(matchResult -> matchResult.group(0)).toList();
            for (String s : ids) {
                if (s.startsWith("!")) {
                    s = s.substring(1);
                }
                String name = discordant.getLinkedPlayerNameForDiscordId(s);
                if (discordant.getConfig().linking.enabled && discordant.getConfig().enableMentions && name != null) {
                    playerNamesToNotify.add(name.toLowerCase());
                    message = message.replaceAll("<(@.|@)" + s + ">", "@" + name);
                }
                else {
                    Member m = e.getGuild().getMemberById(s);
                    if (m != null) {
                        message = message.replaceAll("<(@.|@)" + s + ">", "@" + m.getEffectiveName());
                    }
                    else {
                        message = message.replaceAll("<(@.|@)" + s + ">", "@Unknown");
                    }
                }
            }
        }
        final var messageFinal = message;

        if (member != null) {
            String role = member.getRoles().isEmpty() ? "" : member.getRoles().get(0).getName();

            final var notifyMessage = new SemanticMessage()
                    .append(SemanticMessage.bot("[Discord] "))
                    .append(getChatComponent(role, member))
                    .append(" >> ");
            final var generalMessage = notifyMessage.copy()
                                                    .append(messageFinal);

            discordant.getServer().getAllPlayers().forEach(player -> {
                if (!playerNamesToNotify.contains(player.name().toLowerCase())) {
                    player.sendSystemMessage(generalMessage);
                }
                else {
                    final var name = player.name();
                    // TODO Use a semantic message part instead of format codes. And trim the !+.
                    final var replacement = messageFinal.replaceAll("(?i)!\\+" + name, "§a$0§r");
                    player.sendSystemMessage(notifyMessage.copy().append(replacement));
                    player.notifySound();
                }
            });
        }
    }

    private SemanticMessage.Part getChatComponent(String role, Member member) {
        return SemanticMessage.discordUser(
                member.getEffectiveName(),
                member.getUser().getAsTag(),
                role,
                member.getColorRaw()
        );
    }
}

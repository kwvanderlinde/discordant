package com.kwvanderlinde.discordant.core.linkedprofiles;

import com.kwvanderlinde.discordant.core.ReloadableComponent;
import com.kwvanderlinde.discordant.core.config.DiscordantConfig;
import com.kwvanderlinde.discordant.core.utils.Clock;
import com.kwvanderlinde.discordant.core.config.LinkingConfig;
import com.kwvanderlinde.discordant.core.modinterfaces.Profile;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Random;
import java.util.UUID;

public class LinkedProfileManager implements ReloadableComponent {
    private LinkingConfig config;
    private final Clock clock;
    private final LinkedProfileRepository linkedProfileRepository;
    private final HashMap<String, String> linkedPlayersByDiscordId = new HashMap<>();
    private final HashMap<UUID, VerificationData> pendingLinkVerification = new HashMap<>();
    private final Random r = new Random();

    public sealed interface VerificationResult permits
            InvalidToken, InvalidUuid, InvalidCode, NoPendingVerification, IncorrectCode,
            AlreadyLinked, SuccessfulLink {
    }
    public record InvalidToken(String token) implements VerificationResult {}
    public record InvalidUuid(String uuid) implements VerificationResult {}
    public record InvalidCode(String code) implements VerificationResult {}
    public record NoPendingVerification(UUID uuid) implements VerificationResult {}
    public record IncorrectCode(UUID uuid, int code) implements VerificationResult {}
    public record AlreadyLinked(LinkedProfile existingProfile) implements VerificationResult {}
    public record SuccessfulLink(LinkedProfile newProfile) implements VerificationResult {}

    public LinkedProfileManager(LinkingConfig config, Clock clock, LinkedProfileRepository linkedProfileRepository) {
        this.config = config;
        this.clock = clock;
        this.linkedProfileRepository = linkedProfileRepository;
    }

    @Override
    public void reload(DiscordantConfig newConfig) {
        // Not much to be done as we just dynamically read values from the configuration.
        config = newConfig.linking;
    }

    public void clearExpiredVerifications() {
        // Remove any expired pending verifications.
        final var currentTime = clock.getCurrentTime();
        final var iterator = pendingLinkVerification.entrySet().iterator();
        while (iterator.hasNext()) {
            final var e = iterator.next();
            VerificationData data = e.getValue();
            if (currentTime > data.validUntil()) {
                iterator.remove();
            }
        }
    }

    public boolean ensureProfileIsLinked(Profile profile) {
        final var linkedProfile = linkedProfileRepository.getByPlayerId(profile.uuid());

        if (linkedProfile == null) {
            return false;
        }

        // Reverse map so we can look up profile by discord ID.
        // TODO Is it worth handling edge case that there are existing entries? Would
        //  not be correct for them to exist, but bugs or instability may cause it.
        linkedPlayersByDiscordId.put(linkedProfile.discordId(), profile.name());
        return true;
    }

    public void unloadProfile(Profile profile) {
        final var linkedProfile = linkedProfileRepository.getByPlayerId(profile.uuid());
        if (linkedProfile != null) {
            linkedPlayersByDiscordId.remove(linkedProfile.discordId());
        }
    }

    public @Nullable String getDiscordIdForPlayerId(UUID playerId) {
        final var linkedProfile = linkedProfileRepository.getByPlayerId(playerId);
        if (linkedProfile == null) {
            return null;
        }
        return linkedProfile.discordId();
    }

    public String getLinkedPlayerNameForDiscordId(String discordId) {
        return linkedPlayersByDiscordId.get(discordId);
    }

    public String generateLinkCode(UUID uuid, String name) {
        if (pendingLinkVerification.containsKey(uuid)) {
            return pendingLinkVerification.get(uuid).token();
        }

        final var authCode = r.nextInt(100_000, 1_000_000);
        final var expiryTime = clock.getCurrentTime() + config.pendingTimeout;
        final var data = new VerificationData(name, uuid, authCode, expiryTime);
        pendingLinkVerification.put(uuid, data);

        return data.token();
    }

    public boolean removeLinkedProfile(UUID uuid) {
        LinkedProfile profile = linkedProfileRepository.getByPlayerId(uuid);
        if (profile != null) {
            linkedPlayersByDiscordId.remove(profile.discordId());
            linkedProfileRepository.delete(profile);
            return true;
        }
        return false;
    }

    public VerificationResult verifyLinkedProfile(final MessageChannel channelToRespondIn,
                                                  final User author,
                                                  final String verificationToken) {
        final var parts = verificationToken.split("\\|", 2);
        if (parts.length != 2) {
            return new InvalidToken(verificationToken);
        }

        final UUID uuid;
        try {
            uuid = UUID.fromString(parts[0]);
        }
        catch (IllegalArgumentException e) {
            return new InvalidUuid(parts[0]);
        }

        final var verificationCode = parts[1];
        if (verificationCode.length() != 6 || !verificationCode.matches("[0-9]+")) {
            return new InvalidCode(verificationCode);
        }

        final var code = Integer.parseInt(verificationCode);
        final var data = pendingLinkVerification.get(uuid);
        if (data == null) {
            return new NoPendingVerification(uuid);
        }
        if (data.code() != code) {
            return new IncorrectCode(uuid, code);
        }

        final var existingProfile = linkedProfileRepository.getByPlayerId(uuid);
        if (existingProfile == null) {
            // Profile entry does not exist yet. Create it.
            final var newLinkedProfile = new LinkedProfile(data.name(), uuid, author.getId());
            linkedProfileRepository.put(newLinkedProfile);
            pendingLinkVerification.remove(uuid);
            linkedPlayersByDiscordId.put(newLinkedProfile.discordId(), newLinkedProfile.name());

            return new SuccessfulLink(newLinkedProfile);
        }
        else {
            pendingLinkVerification.remove(uuid);
            return new AlreadyLinked(existingProfile);
        }
    }
}

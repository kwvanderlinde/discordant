package com.kwvanderlinde.discordant.core.messages.scopes;

import com.google.common.collect.ImmutableMap;
import com.kwvanderlinde.discordant.core.messages.SemanticMessage;

import javax.annotation.Nonnull;
import java.util.UUID;

public record ProfileScope(ServerScope serverScope, UUID playerUuid, String playerName) implements Scope {
    @Override
    public void addValuesTo(@Nonnull ImmutableMap.Builder<String, SemanticMessage.Part> builder) {
        serverScope.addValuesTo(builder);

        builder.put("player.uuid", SemanticMessage.literal(playerUuid.toString()));
        builder.put("player.name", SemanticMessage.literal(playerName));
    }
}

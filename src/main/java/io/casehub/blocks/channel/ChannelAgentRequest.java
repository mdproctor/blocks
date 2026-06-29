package io.casehub.blocks.channel;

import io.casehub.qhorus.api.gateway.OutboundMessage;

import java.util.UUID;

public record ChannelAgentRequest(
        UUID channelId,
        String correlationId,
        OutboundMessage message
) {}

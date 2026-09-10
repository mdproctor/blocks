package io.casehub.blocks.agentic.yaml.spec;

import org.jspecify.annotations.Nullable;

public record ChannelBindingSpec(
        @Nullable String channelId,
        String semantic) {}

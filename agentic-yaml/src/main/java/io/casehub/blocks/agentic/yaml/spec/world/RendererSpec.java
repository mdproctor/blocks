package io.casehub.blocks.agentic.yaml.spec.world;

import org.jspecify.annotations.Nullable;

public record RendererSpec(
        @Nullable Integer verbatimThreshold,
        @Nullable Integer groupedThreshold) {}

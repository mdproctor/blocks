package io.casehub.blocks.agentic.yaml.compiler;

import io.casehub.blocks.summarisation.observation.affordance.ActionDescriptor;
import io.casehub.blocks.summarisation.observation.affordance.ObservableEntity;
import io.casehub.blocks.summarisation.observation.affordance.ObservationPipeline;
import io.casehub.blocks.summarisation.observation.affordance.ObservationSection;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

public record CompiledWorld(
        List<ActionDescriptor> actions,
        Map<String, ObservableEntity> entities,
        List<ObservationSection> sections,
        @Nullable ObservationPipeline pipeline,
        @Nullable RendererThresholds rendererThresholds) {

    public record RendererThresholds(
            int verbatimThreshold,
            @Nullable Integer groupedThreshold) {}
}

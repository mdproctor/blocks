package io.casehub.blocks.agentic.yaml.spec;

import io.casehub.blocks.prompt.FewShotExample;
import io.casehub.blocks.prompt.OptimiserConfig;
import io.casehub.blocks.prompt.SafetyConfig;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public record PromptOptimisationPipelineSpec(
        PromptSignatureSpec signature,
        @Nullable PromptOptimiserSpec optimiser,
        @Nullable OptimiserConfig config,
        @Nullable SafetyConfig safety,
        @Nullable ConfidenceScorerSpec confidenceScorer,
        @Nullable List<FewShotExample> examples) {

    public PromptOptimisationPipelineSpec {
        Objects.requireNonNull(signature, "signature");
    }
}

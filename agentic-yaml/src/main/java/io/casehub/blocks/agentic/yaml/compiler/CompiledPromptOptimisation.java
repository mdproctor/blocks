package io.casehub.blocks.agentic.yaml.compiler;

import io.casehub.blocks.memory.ConfidenceScorer;
import io.casehub.blocks.prompt.FewShotExample;
import io.casehub.blocks.prompt.OptimiserConfig;
import io.casehub.blocks.prompt.PromptOptimiser;
import io.casehub.blocks.prompt.PromptSignature;
import io.casehub.blocks.prompt.SafetyConfig;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

public record CompiledPromptOptimisation(Map<String, CompiledPipeline> pipelines) {

    public record CompiledPipeline(
            PromptSignature signature,
            PromptOptimiser optimiser,
            OptimiserConfig config,
            SafetyConfig safety,
            @Nullable ConfidenceScorer confidenceScorer,
            List<FewShotExample> examples) {}
}

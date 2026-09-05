package io.casehub.blocks.summarisation.yaml.runtime;

import io.casehub.blocks.summarisation.yaml.CompiledPipeline;
import io.casehub.blocks.summarisation.yaml.PipelineCompiler;
import io.casehub.blocks.summarisation.yaml.PipelineDefinition;
import io.casehub.blocks.summarisation.yaml.SummariserFactory;
import io.casehub.blocks.summarisation.yaml.SummariserRegistry;
import io.casehub.platform.api.expression.ExpressionEngine;
import io.casehub.blocks.summarisation.yaml.builtin.CountSummariser;
import io.casehub.blocks.summarisation.yaml.builtin.PhaseDetectSummariser;
import io.casehub.blocks.summarisation.yaml.builtin.ThresholdClassifySummariser;
import io.quarkus.runtime.annotations.Recorder;

import java.util.Map;

@Recorder
public class SummarisationRecorder {

    @SuppressWarnings("unchecked")
    public SummariserRegistry createRegistry(ExpressionEngine expressionEngine) {
        var registry = new SummariserRegistry();
        registry.register("threshold-classify", (SummariserFactory)
                config -> ThresholdClassifySummariser.create(config, expressionEngine));
        registry.register("phase-detect", (SummariserFactory)
                config -> PhaseDetectSummariser.create(config, java.util.List.of()));
        registry.register("count", (SummariserFactory) config -> CountSummariser.create(config));
        return registry;
    }

    @SuppressWarnings("unchecked")
    public CompiledPipeline<Map<String, Object>> compilePipeline(
            PipelineDefinition definition,
            SummariserRegistry registry) {
        return new PipelineCompiler().compile(definition, registry, null);
    }
}

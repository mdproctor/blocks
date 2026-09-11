package io.casehub.blocks.agentic.yaml.registry;

import io.casehub.blocks.agentic.yaml.spec.ConfidenceScorerSpec;
import io.casehub.blocks.memory.ArousalScorer;
import io.casehub.blocks.memory.CompositeConfidenceScorer;
import io.casehub.blocks.memory.ConfidenceScorer;
import io.casehub.blocks.memory.SurpriseScorer;
import io.casehub.blocks.memory.WeightedScorer;

public class ConfidenceScorerRegistry {

    public ConfidenceScorer resolve(ConfidenceScorerSpec spec) {
        return switch (spec) {
            case ConfidenceScorerSpec.Arousal ignored -> new ArousalScorer();
            case ConfidenceScorerSpec.Surprise ignored -> new SurpriseScorer();
            case ConfidenceScorerSpec.Composite c -> new CompositeConfidenceScorer(
                    c.scorers().stream()
                            .map(ws -> new WeightedScorer(resolve(ws.scorer()), ws.weight()))
                            .toList());
        };
    }
}

package io.casehub.blocks.agentic.yaml.registry;

import io.casehub.blocks.agentic.yaml.spec.DiversityStrategySpec;
import io.casehub.blocks.prompt.DiversityStrategy;
import io.casehub.blocks.prompt.optimiser.OutcomeAwareDiversityStrategy;
import io.casehub.blocks.prompt.optimiser.TopNDiversityStrategy;

public class DiversityStrategyRegistry {

    public DiversityStrategy resolve(DiversityStrategySpec spec) {
        return switch (spec) {
            case DiversityStrategySpec.TopN ignored -> new TopNDiversityStrategy();
            case DiversityStrategySpec.OutcomeAware oa ->
                    new OutcomeAwareDiversityStrategy(oa.weight());
        };
    }
}

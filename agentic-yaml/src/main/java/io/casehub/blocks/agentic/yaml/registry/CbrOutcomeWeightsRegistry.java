package io.casehub.blocks.agentic.yaml.registry;

import io.casehub.blocks.agentic.yaml.spec.CbrOutcomeWeightsSpec;
import io.casehub.blocks.routing.agent.CbrOutcomeWeights;

import java.util.Map;

public class CbrOutcomeWeightsRegistry {
    public CbrOutcomeWeights resolve(CbrOutcomeWeightsSpec spec) {
        var weights = Map.copyOf(spec.weights());
        return () -> weights;
    }
}

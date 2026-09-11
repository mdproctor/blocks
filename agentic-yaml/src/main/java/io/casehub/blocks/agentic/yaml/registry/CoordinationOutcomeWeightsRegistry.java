package io.casehub.blocks.agentic.yaml.registry;

import io.casehub.blocks.agentic.yaml.spec.CoordinationOutcomeWeightsSpec;
import io.casehub.blocks.routing.agent.CoordinationOutcomeWeights;

import java.util.Map;

public class CoordinationOutcomeWeightsRegistry {
    public CoordinationOutcomeWeights resolve(CoordinationOutcomeWeightsSpec spec) {
        var weights = Map.copyOf(spec.weights());
        return () -> weights;
    }
}

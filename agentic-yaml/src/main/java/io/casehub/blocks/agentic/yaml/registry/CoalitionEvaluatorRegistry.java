package io.casehub.blocks.agentic.yaml.registry;

import io.casehub.blocks.agentic.coalition.CapabilityCoverageEvaluator;
import io.casehub.blocks.agentic.coalition.CoalitionEvaluator;
import io.casehub.blocks.agentic.yaml.spec.CoalitionEvaluatorSpec;

public class CoalitionEvaluatorRegistry {

    public CoalitionEvaluator resolve(CoalitionEvaluatorSpec spec) {
        return switch (spec) {
            case CoalitionEvaluatorSpec.CapabilityCoverage ignored ->
                    new CapabilityCoverageEvaluator();
        };
    }
}

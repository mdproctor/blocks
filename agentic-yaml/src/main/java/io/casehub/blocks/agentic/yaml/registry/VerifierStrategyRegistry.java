package io.casehub.blocks.agentic.yaml.registry;

import io.casehub.blocks.agentic.yaml.spec.VerifierStrategySpec;

public class VerifierStrategyRegistry {

    public String resolve(VerifierStrategySpec spec) {
        return switch (spec) {
            case VerifierStrategySpec.LlmEvaluation ignored -> "llm-evaluation";
            case VerifierStrategySpec.SchemaValidation ignored -> "schema-validation";
        };
    }
}

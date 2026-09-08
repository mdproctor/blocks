package io.casehub.blocks.agentic.yaml.registry;

import io.casehub.blocks.agentic.yaml.spec.ConvergencePolicySpec;
import io.casehub.blocks.conversation.ConvergencePolicy;
import io.casehub.blocks.conversation.ConvergencePolicies;

public class ConvergencePolicyRegistry {
    public ConvergencePolicy resolve(ConvergencePolicySpec spec) {
        return switch (spec) {
            case ConvergencePolicySpec.Structural s ->
                    ConvergencePolicies.structural(s.similarityThreshold(), s.staleRounds());
            case ConvergencePolicySpec.CommonGroundRatio cgr ->
                    ConvergencePolicies.commonGroundRatio(cgr.consensusThreshold(),
                            cgr.deadlockDisputeRatio());
            case ConvergencePolicySpec.Composite c ->
                    ConvergencePolicies.composite(
                            c.policies().stream().map(this::resolve)
                                    .toArray(ConvergencePolicy[]::new));
        };
    }
}

package io.casehub.blocks.agentic.yaml.registry;

import io.casehub.blocks.agentic.activation.ActivationRule;
import io.casehub.blocks.agentic.activation.MaxIterationsGuard;
import io.casehub.blocks.agentic.activation.OnExplicitDispatch;
import io.casehub.blocks.agentic.yaml.spec.ActivationSpec;

public class ActivationRuleRegistry {

    @SuppressWarnings("unchecked")
    public <T> ActivationRule<T> resolve(ActivationSpec spec) {
        return switch (spec) {
            case ActivationSpec.OnDispatch od -> new OnExplicitDispatch<>();
            case ActivationSpec.MaxIterationsGuard mig ->
                    new MaxIterationsGuard<>(mig.maxIterations());
        };
    }
}

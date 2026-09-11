package io.casehub.blocks.agentic.yaml.registry;

import io.casehub.blocks.agentic.model.ExecutionBackend;
import io.casehub.blocks.agentic.yaml.spec.ExecutionBackendSpec;

public class ExecutionBackendRegistry {

    private final EventConcurrencyPolicyRegistry policyRegistry;

    public ExecutionBackendRegistry(EventConcurrencyPolicyRegistry policyRegistry) {
        this.policyRegistry = policyRegistry;
    }

    @SuppressWarnings("unchecked")
    public <T> ExecutionBackend<T> resolve(ExecutionBackendSpec spec) {
        return switch (spec) {
            case ExecutionBackendSpec.Reactive ignored -> ExecutionBackend.reactive();
            case ExecutionBackendSpec.Choreographed c -> {
                var policy = policyRegistry.resolve(c.policy());
                yield ExecutionBackend.choreographed(policy);
            }
        };
    }
}

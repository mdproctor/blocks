package io.casehub.blocks.agentic.yaml.registry;

import io.casehub.blocks.agentic.yaml.spec.ConflictResolutionSpec;
import io.casehub.blocks.normative.ConflictResolutionStrategy;
import io.casehub.blocks.normative.EscalationResolution;
import io.casehub.blocks.normative.MostRestrictiveResolution;
import io.casehub.blocks.normative.PriorityResolution;
import io.casehub.blocks.normative.RecencyResolution;
import io.casehub.blocks.normative.SpecificityResolution;

public class ConflictResolutionRegistry {

    @SuppressWarnings("unchecked")
    public <T> ConflictResolutionStrategy<T> resolve(ConflictResolutionSpec spec) {
        return switch (spec) {
            case ConflictResolutionSpec.Priority p -> new PriorityResolution<>();
            case ConflictResolutionSpec.Specificity s -> new SpecificityResolution<>();
            case ConflictResolutionSpec.Recency r -> new RecencyResolution<>();
            case ConflictResolutionSpec.MostRestrictive mr ->
                    (ConflictResolutionStrategy<T>) (ConflictResolutionStrategy<?>) new MostRestrictiveResolution();
            case ConflictResolutionSpec.Escalation e ->
                    (ConflictResolutionStrategy<T>) new EscalationResolution<>(e.escalationDecision());
        };
    }
}

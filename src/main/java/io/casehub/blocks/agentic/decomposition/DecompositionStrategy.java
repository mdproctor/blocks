package io.casehub.blocks.agentic.decomposition;

import io.casehub.blocks.agentic.plan.ExecutionPlan;
import io.smallrye.mutiny.Uni;

public interface DecompositionStrategy<T> {
    Uni<ExecutionPlan<T>> decompose(TaskNode<T> compound,
                                     DecompositionContext<T> context);
}

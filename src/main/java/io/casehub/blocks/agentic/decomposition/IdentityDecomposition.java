package io.casehub.blocks.agentic.decomposition;

import io.casehub.blocks.agentic.plan.ExecutionPlan;
import io.smallrye.mutiny.Uni;

public class IdentityDecomposition<T> implements DecompositionStrategy<T> {

    @Override
    public Uni<ExecutionPlan<T>> decompose(TaskNode<T> node,
                                            DecompositionContext<T> context) {
        return switch (node) {
            case TaskNode.LeafTask<T> leaf -> Uni.createFrom().item(ExecutionPlan.singleton(leaf));
            case TaskNode.CompoundTask<T> compound -> throw new UnsupportedOperationException(
                "IdentityDecomposition cannot decompose compound tasks — "
                + "it is a placeholder for non-HTN builders");
        };
    }
}

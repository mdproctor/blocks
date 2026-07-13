package io.casehub.blocks.agentic.decomposition;

import io.casehub.blocks.agentic.plan.ExecutionPlan;
import io.smallrye.mutiny.Uni;

public class StaticDecomposition<T> implements DecompositionStrategy<T> {

    @Override
    public Uni<ExecutionPlan<T>> decompose(TaskNode<T> compound,
                                           DecompositionContext<T> context) {
        if (compound instanceof TaskNode.CompoundTask<T> ct) {
            for (var method : ct.methods()) {
                if (method.guard().test(context.state())) {
                    return method.strategy().decompose(compound, context);
                }
            }
            return Uni.createFrom().failure(
                    new NoMethodMatchedException(ct.name()));
        }
        return Uni.createFrom().item(ExecutionPlan.singleton((TaskNode.LeafTask<T>) compound));
    }
}

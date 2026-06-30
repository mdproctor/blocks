package io.casehub.blocks.agentic.decomposition;

import io.smallrye.mutiny.Uni;

import java.util.List;

public class StaticDecomposition<T> implements DecompositionStrategy<T> {

    @Override
    public Uni<List<TaskNode<T>>> decompose(TaskNode<T> compound,
                                            DecompositionContext<T> context) {
        if (compound instanceof TaskNode.CompoundTask<T> ct) {
            for (var method : ct.methods()) {
                if (method.guard().test(context.state())) {
                    return method.strategy().decompose(compound, context);
                }
            }
            return Uni.createFrom().item(List.of());
        }
        return Uni.createFrom().item(List.of(compound));
    }
}

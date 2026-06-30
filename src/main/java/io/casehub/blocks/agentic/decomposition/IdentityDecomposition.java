package io.casehub.blocks.agentic.decomposition;

import io.smallrye.mutiny.Uni;

import java.util.List;

public class IdentityDecomposition<T> implements DecompositionStrategy<T> {

    @Override
    public Uni<List<TaskNode<T>>> decompose(TaskNode<T> compound,
                                            DecompositionContext<T> context) {
        return Uni.createFrom().item(List.of(compound));
    }
}

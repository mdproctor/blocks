package io.casehub.blocks.agentic.decomposition;

import io.smallrye.mutiny.Uni;

import java.util.List;

public interface DecompositionStrategy<T> {
    Uni<List<TaskNode<T>>> decompose(TaskNode<T> compound,
                                     DecompositionContext<T> context);
}

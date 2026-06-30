package io.casehub.blocks.agentic.decomposition;

import io.casehub.blocks.agentic.AgentRef;

import java.util.List;
import java.util.function.Predicate;

public final class Decomposition {
    private Decomposition() {}

    public static <T> IdentityDecomposition<T> none() {
        return new IdentityDecomposition<>();
    }

    public static <T> StaticDecomposition<T> staticTree() {
        return new StaticDecomposition<>();
    }

    public static <T> DecompositionMethod<T> method(Predicate<T> guard,
                                                    DecompositionStrategy<T> strategy) {
        return new DecompositionMethod<>(guard, strategy);
    }

    @SafeVarargs
    public static <T> DecompositionStrategy<T> sequence(TaskNode<T>... tasks) {
        var list = List.of(tasks);
        return (compound, ctx) -> io.smallrye.mutiny.Uni.createFrom().item(list);
    }

    public static <T> TaskNode.PrimitiveTask<T> primitive(AgentRef agent) {
        return new TaskNode.PrimitiveTask<>(agent, null, null);
    }

    public static <T> TaskNode.CompoundTask<T> compound(String name,
                                                        List<DecompositionMethod<T>> methods) {
        return new TaskNode.CompoundTask<>(name, methods);
    }
}

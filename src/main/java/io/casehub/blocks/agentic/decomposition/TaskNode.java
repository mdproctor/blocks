package io.casehub.blocks.agentic.decomposition;

import io.casehub.blocks.agentic.AgentRef;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

public sealed interface TaskNode<T>
        permits TaskNode.PrimitiveTask, TaskNode.CompoundTask {

    record PrimitiveTask<T>(AgentRef agent, Predicate<T> precondition,
                            Consumer<T> effect) implements TaskNode<T> {}

    record CompoundTask<T>(String name, List<DecompositionMethod<T>> methods)
            implements TaskNode<T> {
        public CompoundTask { methods = List.copyOf(methods); }
    }
}

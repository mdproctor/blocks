package io.casehub.blocks.agentic.decomposition;

import io.casehub.api.model.ExecutorRef;
import io.casehub.api.model.TaskDescriptor;
import io.casehub.api.model.TaskStatus;
import io.casehub.blocks.agentic.AgentRef;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

public sealed interface TaskNode<T>
        permits TaskNode.LeafTask, TaskNode.CompoundTask {

    sealed interface LeafTask<T> extends TaskNode<T>, TaskDescriptor
            permits TaskNode.PrimitiveTask, TaskNode.PlannedTask {
        AgentRef agent();

        @Nullable String description();

        @Override
        default ExecutorRef executor() {return agent();}
    }

    record PrimitiveTask<T>(String id, Instant createdAt,
                            @Nullable String description, AgentRef agent,
                            @Nullable Predicate<T> precondition,
                            @Nullable Consumer<T> effect) implements LeafTask<T> {
        public PrimitiveTask {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(createdAt, "createdAt");
            Objects.requireNonNull(agent, "agent");
        }

        @Override
        public TaskStatus status() {return TaskStatus.PENDING;}
    }

    record CompoundTask<T>(String name, List<DecompositionMethod<T>> methods)
            implements TaskNode<T> {
        public CompoundTask {
            Objects.requireNonNull(name, "name");
            methods = List.copyOf(methods);
        }
    }

    record PlannedTask<T>(String id, Instant createdAt,
                          String description, AgentRef agent,
                          @Nullable String rationale) implements LeafTask<T> {
        public PlannedTask {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(createdAt, "createdAt");
            Objects.requireNonNull(description, "description");
            Objects.requireNonNull(agent, "agent");
        }

        @Override
        public TaskStatus status() {return TaskStatus.PENDING;}
    }
}

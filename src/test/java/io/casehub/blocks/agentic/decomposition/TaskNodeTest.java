package io.casehub.blocks.agentic.decomposition;

import io.casehub.blocks.agentic.AgentRef;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class TaskNodeTest {

    @Nested
    class PrimitiveTaskVariant {
        @Test
        void carriesAgentAndOptionalPreconditionAndEffect() {
            var agent = AgentRef.external(s -> CompletableFuture.completedFuture(null));
            var task = new TaskNode.PrimitiveTask<String>(agent, s -> true, s -> {});
            assertThat(task.agent()).isEqualTo(agent);
            assertThat(task.precondition().test("anything")).isTrue();
        }

        @Test
        void nullPreconditionDefaultsToTrue() {
            var agent = AgentRef.external(s -> CompletableFuture.completedFuture(null));
            var task = new TaskNode.PrimitiveTask<String>(agent, null, null);
            assertThat(task.precondition()).isNull();
        }
    }

    @Nested
    class CompoundTaskVariant {
        @Test
        void carriesNameAndMethods() {
            var method = new DecompositionMethod<String>(s -> true, new IdentityDecomposition<>());
            var task = new TaskNode.CompoundTask<String>("analyse", List.of(method));
            assertThat(task.name()).isEqualTo("analyse");
            assertThat(task.methods()).hasSize(1);
        }

        @Test
        void methodsListIsDefensivelyCopied() {
            var methods = new java.util.ArrayList<DecompositionMethod<String>>();
            methods.add(new DecompositionMethod<>(s -> true, new IdentityDecomposition<>()));
            var task = new TaskNode.CompoundTask<>("t", methods);
            methods.clear();
            assertThat(task.methods()).hasSize(1);
        }
    }

    @Test
    void sealedInterfacePermitsTwoVariants() {
        assertThat(TaskNode.class.getPermittedSubclasses()).hasSize(2);
    }
}

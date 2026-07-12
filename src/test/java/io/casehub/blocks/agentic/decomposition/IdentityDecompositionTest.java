package io.casehub.blocks.agentic.decomposition;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.plan.ExecutionPlan;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentityDecompositionTest {

    @Test
    void returnsPrimitiveAsSingletonPlan() {
        var agent = AgentRef.external(s -> CompletableFuture.completedFuture(null));
        var primitive = new TaskNode.PrimitiveTask<String>(null, agent, null, null);
        var decomp = new IdentityDecomposition<String>();
        var ctx = new DecompositionContext<>("state", List.of(), 0);

        ExecutionPlan<String> result = decomp.decompose(primitive, ctx).await().indefinitely();
        assertThat(result.nodes()).hasSize(1);
        assertThat(result.nodes().values().iterator().next().task()).isSameAs(primitive);
    }

    @Test
    void compoundTask_throws() {
        var decomp = new IdentityDecomposition<String>();
        var compound = new TaskNode.CompoundTask<String>("test", List.of());
        var ctx = new DecompositionContext<>("state", List.of(), 0);

        assertThatThrownBy(() -> decomp.decompose(compound, ctx).await().indefinitely())
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessageContaining("cannot decompose compound tasks");
    }
}

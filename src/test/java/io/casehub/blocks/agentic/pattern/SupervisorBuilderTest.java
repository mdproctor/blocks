package io.casehub.blocks.agentic.pattern;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import io.casehub.blocks.agentic.model.ExecutionResult;
import io.casehub.blocks.agentic.routing.FirstMatchRouting;
import io.casehub.blocks.agentic.termination.MaxIterationsTermination;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static io.casehub.blocks.agentic.pattern.Patterns.supervisor;
import static org.assertj.core.api.Assertions.assertThat;

class SupervisorBuilderTest {

    @Test
    void buildsValidModelWithDefaults() {
        var agent = AgentRef.external((Object input) ->
                CompletableFuture.completedFuture(AgentResult.success(null, "done")));
        var model = supervisor()
                .agents(agent)
                .terminate(new MaxIterationsTermination<>(1))
                .build();

        assertThat(model.routing()).isNotNull();
        assertThat(model.decomposition()).isNotNull();
        assertThat(model.activation()).isNotNull();
        assertThat(model.aggregation()).isNotNull();
        assertThat(model.termination()).isInstanceOf(MaxIterationsTermination.class);
    }

    @Test
    void executesEndToEnd() {
        var agent = AgentRef.external((Object input) ->
                CompletableFuture.completedFuture(AgentResult.success(null, "output")));
        var result = supervisor()
                .agents(agent)
                .terminate(new MaxIterationsTermination<>(2))
                .execute("state").await().indefinitely();

        assertThat(result).isInstanceOf(ExecutionResult.Completed.class);
    }

    @Test
    void overridesRouting() {
        var agent = AgentRef.external((Object input) ->
                CompletableFuture.completedFuture(AgentResult.success(null, "output")));
        var model = supervisor()
                .agents(agent)
                .route(new FirstMatchRouting<>(c -> true))
                .terminate(new MaxIterationsTermination<>(1))
                .build();

        assertThat(model.routing()).isInstanceOf(FirstMatchRouting.class);
    }
}

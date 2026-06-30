package io.casehub.blocks.agentic.pattern;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import io.casehub.blocks.agentic.model.ExecutionResult;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static io.casehub.blocks.agentic.pattern.Patterns.debate;
import static org.assertj.core.api.Assertions.assertThat;

class DebateBuilderTest {

    @Test
    void executesDebateRounds() {
        var critic = AgentRef.external((Object i) ->
                CompletableFuture.completedFuture(AgentResult.success(null, "critique")));
        var advocate = AgentRef.external((Object i) ->
                CompletableFuture.completedFuture(AgentResult.success(null, "support")));

        var result = debate()
                .debaters(critic, advocate)
                .maxRounds(3)
                .execute("state").await().indefinitely();

        assertThat(result).isInstanceOf(ExecutionResult.Completed.class);
    }
}

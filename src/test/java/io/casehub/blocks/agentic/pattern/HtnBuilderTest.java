package io.casehub.blocks.agentic.pattern;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static io.casehub.blocks.agentic.pattern.Patterns.htn;
import static org.assertj.core.api.Assertions.assertThat;

class HtnBuilderTest {

    @Test
    void buildsValidModel() {
        var agent = AgentRef.external((Object i) ->
                CompletableFuture.completedFuture(AgentResult.success(null, "done")));

        var model = htn()
                .agents(agent)
                .build();

        assertThat(model.routing()).isNotNull();
        assertThat(model.decomposition()).isNotNull();
    }
}

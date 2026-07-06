package io.casehub.blocks.routing.agent;

import io.casehub.api.spi.routing.AgentCandidate;
import io.casehub.api.spi.routing.AgentRoutingContext;
import org.jspecify.annotations.Nullable;

import java.util.List;

public interface RoutingPromptSection {
    @Nullable String render(AgentRoutingContext context, List<AgentCandidate> eligible);
}

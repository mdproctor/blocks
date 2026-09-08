package io.casehub.blocks.agentic.yaml.registry;

import io.casehub.blocks.agentic.routing.FirstMatchRouting;
import io.casehub.blocks.agentic.routing.RoundRobinRouting;
import io.casehub.blocks.agentic.routing.RoutingStrategy;
import io.casehub.blocks.agentic.routing.SelectAllRouting;
import io.casehub.blocks.agentic.routing.SequentialRouting;
import io.casehub.blocks.agentic.yaml.spec.RoutingSpec;
import io.casehub.platform.api.expression.ExpressionEngine;
import org.jspecify.annotations.Nullable;

public class RoutingStrategyRegistry {

    @SuppressWarnings("unchecked")
    public <T> RoutingStrategy<T> resolve(RoutingSpec spec, @Nullable ExpressionEngine engine) {
        return switch (spec) {
            case RoutingSpec.FirstMatch fm -> new FirstMatchRouting<>(c -> true);
            case RoutingSpec.RoundRobin rr -> new RoundRobinRouting<>();
            case RoutingSpec.Sequential seq -> new SequentialRouting<>();
            case RoutingSpec.LlmSelected llm ->
                    throw new UnsupportedOperationException(
                            "llm-selected routing requires AgentProvider — use CDI injection");
            case RoutingSpec.SelectAll sa -> new SelectAllRouting<>();
        };
    }
}

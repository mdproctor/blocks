package io.casehub.blocks.routing.agent;

import io.casehub.api.spi.routing.AgentRoutingContext;
import org.jspecify.annotations.Nullable;

import java.util.Map;

public interface RoutingFeatureExtractor {
    Map<String, Object> extractFeatures(AgentRoutingContext context);
    @Nullable String extractProblem(AgentRoutingContext context);
}

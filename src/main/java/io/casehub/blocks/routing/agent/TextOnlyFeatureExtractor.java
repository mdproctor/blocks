package io.casehub.blocks.routing.agent;

import io.casehub.api.spi.routing.AgentRoutingContext;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import org.jspecify.annotations.Nullable;

import java.util.Map;

@ApplicationScoped
@DefaultBean
public class TextOnlyFeatureExtractor implements RoutingFeatureExtractor {

    @Override
    public Map<String, Object> extractFeatures(AgentRoutingContext context) {
        return Map.of();
    }

    @Override
    public @Nullable String extractProblem(AgentRoutingContext context) {
        if (context.caseContext() == null || context.caseContext().isNull()) {
            return null;
        }
        // Use asText() for TextNode, toString() for structured nodes
        String text = context.caseContext().isTextual()
            ? context.caseContext().asText()
            : context.caseContext().toString();
        return text.isBlank() ? null : text;
    }
}

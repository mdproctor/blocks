package io.casehub.blocks.agentic.yaml.registry;

import io.casehub.blocks.agentic.model.EventConcurrencyPolicy;
import io.casehub.blocks.agentic.yaml.spec.EventConcurrencyPolicySpec;

public class EventConcurrencyPolicyRegistry {

    public EventConcurrencyPolicy resolve(EventConcurrencyPolicySpec spec) {
        return switch (spec) {
            case EventConcurrencyPolicySpec.Serialize ignored ->
                    EventConcurrencyPolicy.serialize();
            case EventConcurrencyPolicySpec.Coalesce c ->
                    c.window() != null
                            ? EventConcurrencyPolicy.coalesce(c.window())
                            : EventConcurrencyPolicy.coalesce();
            case EventConcurrencyPolicySpec.CoalesceBySource ignored ->
                    EventConcurrencyPolicy.coalesceBySource();
        };
    }
}

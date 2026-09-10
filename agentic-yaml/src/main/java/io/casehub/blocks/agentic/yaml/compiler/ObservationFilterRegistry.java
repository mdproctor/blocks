package io.casehub.blocks.agentic.yaml.compiler;

import io.casehub.blocks.summarisation.observation.affordance.ObservationFilter;
import io.casehub.blocks.summarisation.observation.affordance.PerceptionFilter;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class ObservationFilterRegistry {

    private final Map<String, Supplier<ObservationFilter>> factories = new HashMap<>();

    public ObservationFilterRegistry() {
        register("perception", PerceptionFilter::new);
    }

    public void register(String name, Supplier<ObservationFilter> factory) {
        factories.put(name, factory);
    }

    public ObservationFilter resolve(String name) {
        var factory = factories.get(name);
        if (factory == null) {
            throw new IllegalArgumentException("Unknown observation filter type: " + name);
        }
        return factory.get();
    }
}

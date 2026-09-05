package io.casehub.blocks.summarisation.yaml;

import io.casehub.blocks.summarisation.LevelEvent;
import io.casehub.blocks.summarisation.Summariser;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SummariserRegistry {

    private final Map<String, SummariserFactory<?>> factories = new ConcurrentHashMap<>();

    public SummariserRegistry() {
        register("pass-through", config -> Summariser.ofSync(batch ->
                batch.stream().map(LevelEvent::payload).toList()));
    }

    public void register(String typeId, SummariserFactory<?> factory) {
        factories.put(typeId, factory);
    }

    @SuppressWarnings("unchecked")
    public <IN, OUT> Summariser<IN, OUT> create(String typeId, Map<String, Object> config) {
        var factory = factories.get(typeId);
        if (factory == null) {
            throw new IllegalArgumentException("Unknown summariser type: " + typeId
                    + ". Registered types: " + factories.keySet());
        }
        return (Summariser<IN, OUT>) factory.create(config);
    }

    public boolean hasType(String typeId) {
        return factories.containsKey(typeId);
    }
}

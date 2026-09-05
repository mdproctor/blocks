package io.casehub.blocks.summarisation.yaml;

import io.casehub.blocks.summarisation.Summariser;

import java.util.Map;

@FunctionalInterface
public interface SummariserFactory<E> {
    Summariser<E, ?> create(Map<String, Object> config);
}

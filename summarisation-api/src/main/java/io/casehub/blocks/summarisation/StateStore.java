package io.casehub.blocks.summarisation;

import org.jspecify.annotations.Nullable;

public interface StateStore<S> {
    @Nullable S load(String partitionKey);

    void store(String partitionKey, S state);
}

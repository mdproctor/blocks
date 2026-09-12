package io.casehub.blocks.summarisation;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public interface Tickable {
    CompletionStage<Void> tick(long now);

    default CompletionStage<Void> flush() {
        return CompletableFuture.completedFuture(null);
    }
}

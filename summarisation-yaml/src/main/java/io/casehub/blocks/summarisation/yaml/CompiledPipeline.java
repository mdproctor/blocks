package io.casehub.blocks.summarisation.yaml;

import io.casehub.blocks.summarisation.EventStreamBus;
import io.casehub.blocks.summarisation.SummarisationRunner;

import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

public class CompiledPipeline<IN> {

    private final String name;
    private final EventStreamBus<IN> inputBus;
    private final List<SummarisationRunner<?, ?>> runners;
    private final Map<String, EventStreamBus<?>> outputBuses;

    CompiledPipeline(String name,
                     EventStreamBus<IN> inputBus,
                     List<SummarisationRunner<?, ?>> runners,
                     Map<String, EventStreamBus<?>> outputBuses) {
        this.name = name;
        this.inputBus = inputBus;
        this.runners = List.copyOf(runners);
        this.outputBuses = Map.copyOf(outputBuses);
    }

    public String name() { return name; }

    public EventStreamBus<IN> inputBus() { return inputBus; }

    @SuppressWarnings("unchecked")
    public <E> EventStreamBus<E> outputBus(String levelName) {
        var bus = outputBuses.get(levelName);
        if (bus == null) {
            throw new IllegalArgumentException("No output bus for level: " + levelName
                    + ". Available: " + outputBuses.keySet());
        }
        return (EventStreamBus<E>) bus;
    }

    public CompletionStage<Void> tick(long now) {
        var futures = runners.stream()
                .map(r -> r.tick(now).toCompletableFuture())
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(futures);
    }

    public CompletionStage<Void> flush() {
        var futures = runners.stream()
                .map(r -> r.flush().toCompletableFuture())
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(futures);
    }
}

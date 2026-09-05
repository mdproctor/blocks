package io.casehub.blocks.summarisation.yaml;

import io.casehub.blocks.summarisation.EventLevel;
import io.casehub.blocks.summarisation.EventStreamBus;
import io.casehub.blocks.summarisation.LevelEvent;
import io.cloudevents.CloudEvent;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PipelineCompilerTest {

    static final EventLevel INPUT_LEVEL = new EventLevel("input", 0);

    @Test
    void compiles_singleLevelPassThrough() {
        var def = new PipelineDefinition("test",
                new SourceDefinition(null, null),
                List.of(new LevelDefinition("out",
                        new GroupingDefinition.Windowed(null, 2),
                        new SummariserDefinition("pass-through", Map.of()),
                        null, List.of())));

        var registry = new SummariserRegistry();
        var pipeline = new PipelineCompiler().compile(def, registry, null);

        var output = new ArrayList<LevelEvent<?>>();
        pipeline.outputBus("out").subscribe(e -> true, output::add);

        pipeline.inputBus().publish(new LevelEvent<>(Map.of("a", (Object) 1), 100L, INPUT_LEVEL, null));
        pipeline.inputBus().publish(new LevelEvent<>(Map.of("b", (Object) 2), 200L, INPUT_LEVEL, null));
        pipeline.tick(300L).toCompletableFuture().join();

        assertThat(pipeline.name()).isEqualTo("test");
        assertThat(output).hasSize(2);
    }

    @Test
    void compiles_multiLevelPipeline() {
        var def = new PipelineDefinition("multi",
                new SourceDefinition(null, null),
                List.of(
                        new LevelDefinition("l1",
                                new GroupingDefinition.Windowed(null, 2),
                                new SummariserDefinition("pass-through", Map.of()),
                                null, List.of()),
                        new LevelDefinition("l2",
                                new GroupingDefinition.Windowed(null, 2),
                                new SummariserDefinition("pass-through", Map.of()),
                                null, List.of())));

        var registry = new SummariserRegistry();
        var pipeline = new PipelineCompiler().compile(def, registry, null);

        var output = new ArrayList<LevelEvent<?>>();
        pipeline.outputBus("l2").subscribe(e -> true, output::add);

        pipeline.inputBus().publish(new LevelEvent<>(Map.of("a", (Object) 1), 100L, INPUT_LEVEL, null));
        pipeline.inputBus().publish(new LevelEvent<>(Map.of("b", (Object) 2), 200L, INPUT_LEVEL, null));
        pipeline.tick(300L).toCompletableFuture().join();

        // L1 produces 2 events, but L2 needs 2 to trigger — tick again
        pipeline.tick(400L).toCompletableFuture().join();

        assertThat(output).hasSize(2);
    }

    @Test
    void compiles_withCloudEventEmission() {
        var emitted = new ArrayList<CloudEvent>();

        var def = new PipelineDefinition("emitting",
                new SourceDefinition(null, null),
                List.of(new LevelDefinition("l1",
                        new GroupingDefinition.Windowed(null, 2),
                        new SummariserDefinition("pass-through", Map.of()),
                        new EmitDefinition("io.test.output.v1"),
                        List.of())));

        var registry = new SummariserRegistry();
        var pipeline = new PipelineCompiler().compile(def, registry,
                emitted::add);

        pipeline.inputBus().publish(new LevelEvent<>(Map.of("x", (Object) "y"), 100L, INPUT_LEVEL, "t1"));
        pipeline.inputBus().publish(new LevelEvent<>(Map.of("z", (Object) "w"), 200L, INPUT_LEVEL, "t1"));
        pipeline.tick(300L).toCompletableFuture().join();

        assertThat(emitted).hasSize(2);
        assertThat(emitted.get(0).getType()).isEqualTo("io.test.output.v1");
        assertThat(emitted.get(0).getExtension("tenancyid")).isEqualTo("t1");
    }

    @Test
    void flush_drainsRemainingEvents() {
        var def = new PipelineDefinition("flush-test",
                new SourceDefinition(null, null),
                List.of(new LevelDefinition("out",
                        new GroupingDefinition.Windowed(null, 100),
                        new SummariserDefinition("pass-through", Map.of()),
                        null, List.of())));

        var registry = new SummariserRegistry();
        var pipeline = new PipelineCompiler().compile(def, registry, null);

        var output = new ArrayList<LevelEvent<?>>();
        pipeline.outputBus("out").subscribe(e -> true, output::add);

        pipeline.inputBus().publish(new LevelEvent<>(Map.of("a", (Object) 1), 100L, INPUT_LEVEL, null));
        pipeline.tick(200L).toCompletableFuture().join();
        assertThat(output).isEmpty();

        pipeline.flush().toCompletableFuture().join();
        assertThat(output).hasSize(1);
    }

    @Test
    void compiles_windowedWithAge() {
        var def = new PipelineDefinition("aged",
                new SourceDefinition(null, null),
                List.of(new LevelDefinition("out",
                        new GroupingDefinition.Windowed(100L, null),
                        new SummariserDefinition("pass-through", Map.of()),
                        null, List.of())));

        var registry = new SummariserRegistry();
        var pipeline = new PipelineCompiler().compile(def, registry, null);

        var output = new ArrayList<LevelEvent<?>>();
        pipeline.outputBus("out").subscribe(e -> true, output::add);

        pipeline.inputBus().publish(new LevelEvent<>(Map.of("a", (Object) 1), 100L, INPUT_LEVEL, null));
        pipeline.tick(100L).toCompletableFuture().join();
        assertThat(output).isEmpty();

        pipeline.tick(250L).toCompletableFuture().join();
        assertThat(output).hasSize(1);
    }
}

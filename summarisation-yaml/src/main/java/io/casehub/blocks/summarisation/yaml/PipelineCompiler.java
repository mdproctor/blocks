package io.casehub.blocks.summarisation.yaml;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.blocks.summarisation.EventLevel;
import io.casehub.blocks.summarisation.EventStreamBus;
import io.casehub.blocks.summarisation.SummarisationRunner;
import io.casehub.blocks.summarisation.Summariser;
import io.casehub.blocks.summarisation.WindowPolicy;
import io.casehub.platform.api.expression.ExpressionEngine;
import io.casehub.blocks.summarisation.cloudevents.CloudEventEmitter;
import io.casehub.blocks.summarisation.cloudevents.EventSink;
import io.cloudevents.CloudEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;

public class PipelineCompiler {

    private static final ObjectMapper JSON = new ObjectMapper();


    @SuppressWarnings("unchecked")
    public <IN> CompiledPipeline<IN> compile(PipelineDefinition definition,
                                             SummariserRegistry registry,
                                             @Nullable EventSink<CloudEvent> emitterSink) {
        return compile(definition, registry, emitterSink, null);
    }

    @SuppressWarnings("unchecked")
    public <IN> CompiledPipeline<IN> compile(PipelineDefinition definition,
                                             SummariserRegistry registry,
                                             @Nullable EventSink<CloudEvent> emitterSink,
                                             @Nullable ExpressionEngine expressionEngine) {
        var inputBus     = new EventStreamBus<Object>();
        var levelRunners = new ArrayList<CompiledPipeline.LevelRunner>();
        var outputBuses  = new LinkedHashMap<String, EventStreamBus<?>>();

        EventStreamBus<Object> currentInput = inputBus;

        for (int i = 0; i < definition.levels().size(); i++) {
            var level       = definition.levels().get(i);
            var outputLevel = new EventLevel(level.name(), i + 1);
            var outputBus   = new EventStreamBus<Object>();

            Summariser<Object, Object> summariser = registry.create(
                    level.summariser().type(), level.summariser().config());

            if (level.grouping() instanceof GroupingDefinition.Keyed k) {
                if (expressionEngine == null) {
                    throw new IllegalStateException("Keyed grouping requires ExpressionEngine");
                }
                var keyCompiled = expressionEngine.compile(k.keyExpression(), Object.class, Object.class);
                java.util.function.Function<io.casehub.blocks.summarisation.LevelEvent<Object>, Object> keyExtractor =
                        event -> keyCompiled.eval(event.payload());
                var completionCompiled = expressionEngine.compile(k.completionExpression(), Object.class, Object.class);
                java.util.function.Predicate<java.util.List<io.casehub.blocks.summarisation.LevelEvent<Object>>> completionTest =
                        events -> Boolean.TRUE.equals(completionCompiled.eval(events));
                var keyedRunner = new io.casehub.blocks.summarisation.KeyedSummarisationRunner<>(
                        keyExtractor, completionTest, k.staleTimeout(),
                        summariser, outputBus, outputLevel);
                currentInput.subscribe(e -> true, keyedRunner::collect);
                levelRunners.add(new CompiledPipeline.LevelRunner(
                        now -> keyedRunner.tick(now), keyedRunner::flush));
            } else {
                WindowPolicy policy = toWindowPolicy(level.grouping());
                var          runner = new SummarisationRunner<>(policy, summariser, outputBus, outputLevel);
                currentInput.subscribe(e -> true, runner::collect);
                levelRunners.add(new CompiledPipeline.LevelRunner(
                        now -> runner.tick(now), runner::flush));
            }

            if (level.emit() != null && emitterSink != null) {
                new CloudEventEmitter<>(outputBus, emitterSink,
                                        level.emit().cloudEventType(),
                                        payload -> {
                                            try {
                                                return JSON.writeValueAsBytes(payload);
                                            } catch (JsonProcessingException e) {throw new RuntimeException(e);}
                                        });
            }

            outputBuses.put(level.name(), outputBus);
            currentInput = outputBus;
        }

        return (CompiledPipeline<IN>) (CompiledPipeline<?>) new CompiledPipeline<>(
                definition.name(), inputBus, levelRunners, outputBuses);
    }

    private WindowPolicy toWindowPolicy(GroupingDefinition grouping) {
        if (grouping instanceof GroupingDefinition.Windowed w) {
            if (w.count() != null && w.age() != null) {
                return WindowPolicy.of(w.age(), w.count());
            }
            if (w.count() != null) {
                return WindowPolicy.ofCount(w.count());
            }
            if (w.age() != null) {
                return WindowPolicy.ofAge(w.age());
            }
            throw new IllegalArgumentException("Windowed grouping requires count and/or age");
        }
        throw new UnsupportedOperationException("Keyed grouping not yet implemented");
    }
}

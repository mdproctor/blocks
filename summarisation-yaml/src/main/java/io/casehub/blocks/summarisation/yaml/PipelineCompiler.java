package io.casehub.blocks.summarisation.yaml;

import io.casehub.blocks.summarisation.EventLevel;
import io.casehub.blocks.summarisation.EventStreamBus;
import io.casehub.blocks.summarisation.SummarisationRunner;
import io.casehub.blocks.summarisation.Summariser;
import io.casehub.blocks.summarisation.WindowPolicy;
import io.casehub.blocks.summarisation.cloudevents.CloudEventEmitter;
import io.casehub.blocks.summarisation.cloudevents.EventSink;
import io.cloudevents.CloudEvent;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PipelineCompiler {

    private static final ObjectMapper JSON = new ObjectMapper();

    @SuppressWarnings("unchecked")
    public <IN> CompiledPipeline<IN> compile(PipelineDefinition definition,
                                              SummariserRegistry registry,
                                              @Nullable EventSink<CloudEvent> emitterSink) {
        var inputBus = new EventStreamBus<Object>();
        var runners = new ArrayList<SummarisationRunner<?, ?>>();
        var outputBuses = new LinkedHashMap<String, EventStreamBus<?>>();

        EventStreamBus<Object> currentInput = inputBus;

        for (int i = 0; i < definition.levels().size(); i++) {
            var level = definition.levels().get(i);
            var outputLevel = new EventLevel(level.name(), i + 1);
            var outputBus = new EventStreamBus<Object>();

            Summariser<Object, Object> summariser = registry.create(
                    level.summariser().type(), level.summariser().config());

            WindowPolicy policy = toWindowPolicy(level.grouping());
            var runner = new SummarisationRunner<>(policy, summariser, outputBus, outputLevel);

            currentInput.subscribe(e -> true, runner::collect);

            if (level.emit() != null && emitterSink != null) {
                new CloudEventEmitter<>(outputBus, emitterSink,
                        level.emit().cloudEventType(),
                        payload -> {
                            try { return JSON.writeValueAsBytes(payload); }
                            catch (JsonProcessingException e) { throw new RuntimeException(e); }
                        });
            }

            runners.add(runner);
            outputBuses.put(level.name(), outputBus);
            currentInput = outputBus;
        }

        return (CompiledPipeline<IN>) (CompiledPipeline<?>) new CompiledPipeline<>(
                definition.name(), inputBus, runners, outputBuses);
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

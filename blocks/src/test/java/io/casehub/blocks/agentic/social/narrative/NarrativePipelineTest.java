package io.casehub.blocks.agentic.social.narrative;

import io.casehub.blocks.memory.ReflectionEntry;
import io.casehub.blocks.memory.ReflectionQueryStore;
import io.casehub.blocks.summarisation.EventStreamBus;
import io.casehub.blocks.summarisation.LevelEvent;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.agent.AgentSessionInit;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NarrativePipelineTest {

    private static final String VALID_RESPONSE = """
            {
              "newEpisodes": [
                {
                  "description": "Discovered new approach to problem solving",
                  "emotionalValence": 0.6,
                  "thematicTags": ["learning"],
                  "fromReflections": [0]
                }
              ],
              "themes": [
                {
                  "label": "learner",
                  "salience": 0.7,
                  "thematicTags": ["learning"],
                  "axisWeights": { "CURIOSITY": 0.4 }
                }
              ]
            }
            """;

    static class TestAgentProvider implements AgentProvider {
        private final String response;

        TestAgentProvider(String response) {
            this.response = response;
        }

        @Override
        public Multi<AgentEvent> invoke(AgentSessionConfig config) {
            return Multi.createFrom().items(
                    new AgentEvent.TextDelta(response),
                    new AgentEvent.InvocationComplete(
                            100, 50, 0, 0, 0, 0.001, 500L, 400L, "test", 1, false));
        }

        @Override
        public AgentSession openSession(AgentSessionInit init) {
            throw new UnsupportedOperationException();
        }
    }

    @Test
    void tick_endToEnd_adapterToSummariserToOutputBus() {
        var reflectionStore = mock(ReflectionQueryStore.class);
        var reflections = List.of(
                new ReflectionEntry("agent-1", "t1", "learned about X",
                        Instant.ofEpochMilli(100), List.of()),
                new ReflectionEntry("agent-1", "t1", "discovered Y",
                        Instant.ofEpochMilli(200), List.of()),
                new ReflectionEntry("agent-1", "t1", "mastered Z",
                        Instant.ofEpochMilli(300), List.of()),
                new ReflectionEntry("agent-1", "t1", "understood W",
                        Instant.ofEpochMilli(400), List.of()),
                new ReflectionEntry("agent-1", "t1", "explored V",
                        Instant.ofEpochMilli(500), List.of()));
        when(reflectionStore.findSince(eq("agent-1"), eq("t1"), any()))
                .thenReturn(reflections);

        var cbrStore = mock(CbrNarrativeStore.class);
        when(cbrStore.load(any(), any())).thenReturn(null);

        var agentProvider = new TestAgentProvider(VALID_RESPONSE);
        var summariser = new NarrativeContentSummariser(agentProvider,
                NarrativeConfig.defaults());

        var reflectionBus = new EventStreamBus<ReflectionEntry>();
        var narrativeBus = new EventStreamBus<NarrativeState>();

        List<NarrativeState> received = new ArrayList<>();
        narrativeBus.subscribe(e -> true, e -> received.add(e.payload()));

        var pipeline = new NarrativePipeline(
                summariser, NarrativeConfig.defaults(),
                reflectionStore, cbrStore,
                reflectionBus, narrativeBus);

        pipeline.tick("agent-1", "t1");

        assertThat(received).hasSize(1);
        var state = received.get(0);
        assertThat(state.episodes()).hasSize(1);
        assertThat(state.episodes().get(0).description())
                .isEqualTo("Discovered new approach to problem solving");
        assertThat(state.themes()).hasSize(1);
        assertThat(state.themes().get(0).label()).isEqualTo("learner");
    }

    @Test
    void tick_writesToStateStore() {
        var reflectionStore = mock(ReflectionQueryStore.class);
        var reflections = List.of(
                new ReflectionEntry("agent-1", "t1", "insight-1",
                        Instant.ofEpochMilli(100), List.of()),
                new ReflectionEntry("agent-1", "t1", "insight-2",
                        Instant.ofEpochMilli(200), List.of()),
                new ReflectionEntry("agent-1", "t1", "insight-3",
                        Instant.ofEpochMilli(300), List.of()),
                new ReflectionEntry("agent-1", "t1", "insight-4",
                        Instant.ofEpochMilli(400), List.of()),
                new ReflectionEntry("agent-1", "t1", "insight-5",
                        Instant.ofEpochMilli(500), List.of()));
        when(reflectionStore.findSince(eq("agent-1"), eq("t1"), any()))
                .thenReturn(reflections);

        var cbrStore = mock(CbrNarrativeStore.class);
        when(cbrStore.load(any(), any())).thenReturn(null);

        var agentProvider = new TestAgentProvider(VALID_RESPONSE);
        var summariser = new NarrativeContentSummariser(agentProvider,
                NarrativeConfig.defaults());

        var pipeline = new NarrativePipeline(
                summariser, NarrativeConfig.defaults(),
                reflectionStore, cbrStore,
                new EventStreamBus<>(), new EventStreamBus<>());

        pipeline.tick("agent-1", "t1");

        verify(cbrStore).store(any(NarrativeState.class));
    }

    @Test
    void tick_insufficientReflections_noOutput() {
        var reflectionStore = mock(ReflectionQueryStore.class);
        when(reflectionStore.findSince(eq("agent-1"), eq("t1"), any()))
                .thenReturn(List.of(
                        new ReflectionEntry("agent-1", "t1", "one",
                                Instant.ofEpochMilli(100), List.of())));

        var existingState = new NarrativeState("agent-1", "t1",
                NarrativeScope.INDIVIDUAL, List.of(
                new IndividualEpisode("ep1", Instant.EPOCH, null,
                        List.of(), "existing", 0.5, List.of())),
                Instant.now(), 5);

        var cbrStore = mock(CbrNarrativeStore.class);
        when(cbrStore.load("agent-1", "t1")).thenReturn(existingState);

        var agentProvider = new TestAgentProvider(VALID_RESPONSE);
        var summariser = new NarrativeContentSummariser(agentProvider,
                NarrativeConfig.defaults());

        var narrativeBus = new EventStreamBus<NarrativeState>();
        List<NarrativeState> received = new ArrayList<>();
        narrativeBus.subscribe(e -> true, e -> received.add(e.payload()));

        var pipeline = new NarrativePipeline(
                summariser, NarrativeConfig.defaults(),
                reflectionStore, cbrStore,
                new EventStreamBus<>(), narrativeBus);

        pipeline.tick("agent-1", "t1");

        assertThat(received).as("emission policy blocks — below count threshold")
                .isEmpty();
    }

    @Test
    void narrativeBus_exposedForSubscription() {
        var reflectionStore = mock(ReflectionQueryStore.class);
        var cbrStore = mock(CbrNarrativeStore.class);
        var agentProvider = new TestAgentProvider(VALID_RESPONSE);
        var summariser = new NarrativeContentSummariser(agentProvider,
                NarrativeConfig.defaults());

        var pipeline = new NarrativePipeline(
                summariser, NarrativeConfig.defaults(),
                reflectionStore, cbrStore,
                new EventStreamBus<>(), new EventStreamBus<>());

        assertThat(pipeline.narrativeBus()).isNotNull();
    }
}

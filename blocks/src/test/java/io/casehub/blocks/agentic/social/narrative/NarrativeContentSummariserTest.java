package io.casehub.blocks.agentic.social.narrative;

import io.casehub.blocks.memory.ReflectionEntry;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.agent.AgentSessionInit;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NarrativeContentSummariserTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-09-12T12:00:00Z");
    private static final Instant EARLIER = Instant.parse("2026-09-12T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    private TestAgentProvider agentProvider;
    private NarrativeContentSummariser summariser;

    static class TestAgentProvider implements AgentProvider {
        private String response;
        int invocationCount = 0;
        String lastUserPrompt;
        boolean shouldThrow = false;

        TestAgentProvider(String response) {
            this.response = response;
        }

        void setResponse(String response) {
            this.response = response;
        }

        @Override
        public Multi<AgentEvent> invoke(AgentSessionConfig config) {
            invocationCount++;
            lastUserPrompt = config.userPrompt();
            if (shouldThrow) {
                return Multi.createFrom().failure(new RuntimeException("LLM unavailable"));
            }
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

    private static final String VALID_RESPONSE = """
            {
              "newEpisodes": [
                {
                  "description": "Helped team through production outage",
                  "emotionalValence": 0.7,
                  "thematicTags": ["crisis", "teamwork"],
                  "fromReflections": [0]
                }
              ],
              "themes": [
                {
                  "label": "crisis-helper",
                  "salience": 0.8,
                  "thematicTags": ["crisis"],
                  "axisWeights": {
                    "AFFILIATION": 0.5,
                    "COMPETENCE": 0.3
                  }
                }
              ]
            }
            """;

    @BeforeEach
    void setUp() {
        agentProvider = new TestAgentProvider(VALID_RESPONSE);
        summariser = new NarrativeContentSummariser(agentProvider,
                NarrativeConfig.defaults(), CLOCK);
    }

    private ReflectionEntry reflection(String insight) {
        return new ReflectionEntry("agent-1", "tenant-1", insight,
                EARLIER, List.of("case-1"));
    }

    private NarrativeState existingState(List<NarrativeFragment> fragments) {
        return new NarrativeState("agent-1", "tenant-1",
                NarrativeScope.INDIVIDUAL, fragments,
                Instant.parse("2026-09-12T09:00:00Z"), 5);
    }

    @Test
    void summarise_firstSynthesis_noExistingState() {
        var items = List.of(reflection("resolved outage"));
        var result = summariser.summarise(items, null).toCompletableFuture().join();

        assertThat(result).isNotNull();
        assertThat(result.episodes()).hasSize(1);
        assertThat(result.episodes().get(0).description())
                .isEqualTo("Helped team through production outage");
        assertThat(result.themes()).hasSize(1);
        assertThat(result.themes().get(0).label()).isEqualTo("crisis-helper");
        assertThat(result.scopeId()).isEqualTo("agent-1");
        assertThat(result.tenantId()).isEqualTo("tenant-1");
    }

    @Test
    void summarise_incrementalMerge_preservesExistingEpisodes() {
        var existing = new IndividualEpisode("ep-old", Instant.EPOCH, null,
                List.of(), "old episode", 0.5, List.of());
        var state = existingState(List.of(existing));

        var items = List.of(reflection("new insight"));
        var result = summariser.summarise(items, state).toCompletableFuture().join();

        assertThat(result.episodes()).hasSize(2);
        assertThat(result.episodes().get(0).id()).isEqualTo("ep-old");
        assertThat(result.episodes().get(1).description())
                .isEqualTo("Helped team through production outage");
    }

    @Test
    void summarise_themesFullyReDerived() {
        var oldTheme = new DerivedTheme("old-id", Instant.EPOCH, null,
                List.of(), "old-theme", 0.5,
                java.util.Map.of(), List.of());
        var state = existingState(List.of(oldTheme));

        var items = List.of(reflection("new insight"));
        var result = summariser.summarise(items, state).toCompletableFuture().join();

        assertThat(result.themes()).hasSize(1);
        assertThat(result.themes().get(0).label()).isEqualTo("crisis-helper");
    }

    @Test
    void summarise_llmFailure_returnsPrevious() {
        agentProvider.shouldThrow = true;
        var state = existingState(List.of());

        var items = List.of(reflection("some insight"));
        var result = summariser.summarise(items, state).toCompletableFuture().join();

        assertThat(result).isSameAs(state);
    }

    @Test
    void summarise_parseFailure_returnsPrevious() {
        agentProvider.setResponse("not valid json at all");
        var state = existingState(List.of());

        var items = List.of(reflection("some insight"));
        var result = summariser.summarise(items, state).toCompletableFuture().join();

        assertThat(result).isSameAs(state);
    }

    @Test
    void summarise_emptyItems_returnsPrevious() {
        var state = existingState(List.of());
        var result = summariser.summarise(List.of(), state).toCompletableFuture().join();

        assertThat(result).isSameAs(state);
        assertThat(agentProvider.invocationCount).isZero();
    }

    @Test
    void summarise_reflectionIndexMapping() {
        var r0 = new ReflectionEntry("agent-1", "tenant-1", "insight-0",
                EARLIER, List.of("src-a", "src-b"));
        var r1 = new ReflectionEntry("agent-1", "tenant-1", "insight-1",
                EARLIER, List.of("src-c"));
        var items = List.of(r0, r1);

        var result = summariser.summarise(items, null).toCompletableFuture().join();

        assertThat(result.episodes().get(0).sourceReflectionIds())
                .containsExactly("src-a", "src-b");
    }

    @Test
    void summarise_maxReflectionsCapped() {
        var config = new NarrativeConfig(NarrativeSynthesisGate.defaults(),
                20, 10, 0.1, 2, "domain", "case-type");
        var capped = new NarrativeContentSummariser(agentProvider, config, CLOCK);

        var items = List.of(
                reflection("a"), reflection("b"), reflection("c"));
        capped.summarise(items, null).toCompletableFuture().join();

        assertThat(agentProvider.lastUserPrompt)
                .contains("0. a")
                .contains("1. b")
                .doesNotContain("2. c");
    }

    @Test
    void summarise_emptyResult_returnsPrevious() {
        agentProvider.setResponse("""
                { "newEpisodes": [], "themes": [] }
                """);
        var state = existingState(List.of());
        var result = summariser.summarise(List.of(reflection("x")), state)
                .toCompletableFuture().join();

        assertThat(result).isSameAs(state);
    }

    @Test
    void summarise_noPruning_allEpisodesKept() {
        var config = new NarrativeConfig(NarrativeSynthesisGate.defaults(),
                1, 10, 0.1, 100, "domain", "case-type");
        var small = new NarrativeContentSummariser(agentProvider, config, CLOCK);

        var existing = new IndividualEpisode("ep-old", Instant.EPOCH, null,
                List.of(), "old", 0.5, List.of());
        var state = existingState(List.of(existing));

        var result = small.summarise(List.of(reflection("x")), state)
                .toCompletableFuture().join();

        assertThat(result.episodes()).hasSize(2);
    }

    @Test
    void summarise_groupEpisodesIncludedInTagMatching() {
        var groupEpisode = new GroupEpisode("gep1", Instant.EPOCH, Instant.EPOCH,
                List.of("crisis"), "group desc", 0.3,
                java.util.Set.of("member1"), java.util.Map.of(), 0.5);
        var state = existingState(List.of(groupEpisode));

        var result = summariser.summarise(List.of(reflection("x")), state)
                .toCompletableFuture().join();

        assertThat(result.groupEpisodes()).hasSize(1);
        assertThat(result.groupEpisodes().get(0).id()).isEqualTo("gep1");
        var crisisTheme = result.themes().stream()
                .filter(t -> t.label().equals("crisis-helper"))
                .findFirst().orElseThrow();
        assertThat(crisisTheme.supportingFragmentIds()).contains("gep1");
    }
}

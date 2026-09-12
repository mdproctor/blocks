package io.casehub.blocks.agentic.social.narrative;

import io.casehub.blocks.memory.ReflectionEntry;
import io.casehub.blocks.summarisation.EventLevel;
import io.casehub.blocks.summarisation.LevelEvent;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NarrativeEmissionPolicyTest {

    private static final EventLevel LEVEL = new EventLevel("test", 0);
    private final NarrativeSynthesisGate gate =
            new NarrativeSynthesisGate(3, 0.3, Duration.ofMinutes(60));

    private LevelEvent<ReflectionEntry> event(String insight) {
        return new LevelEvent<>(
                new ReflectionEntry("a1", "t1", insight,
                        Instant.ofEpochMilli(100), List.of()),
                100, LEVEL, "t1");
    }

    @Test
    void nullState_firstSynthesis_alwaysEmits() {
        var policy = new NarrativeEmissionPolicy(gate);
        assertThat(policy.shouldEmit(List.of(event("x")), null, 100))
                .isTrue();
    }

    @Test
    void emptyBuffer_neverEmits() {
        var policy = new NarrativeEmissionPolicy(gate);
        assertThat(policy.shouldEmit(List.of(), null, 100)).isFalse();
    }

    @Test
    void belowCountThreshold_doesNotEmit() {
        var policy = new NarrativeEmissionPolicy(gate);
        var state = narrativeState(Instant.ofEpochMilli(50));
        var buffered = List.of(event("a"), event("b"));
        assertThat(policy.shouldEmit(buffered, state, 100)).isFalse();
    }

    @Test
    void meetsCountThreshold_withNovelty_emits() {
        var policy = new NarrativeEmissionPolicy(gate);
        var state = narrativeState(Instant.ofEpochMilli(50));
        var buffered = List.of(
                event("completely novel content alpha"),
                event("entirely different beta"),
                event("brand new gamma"));
        assertThat(policy.shouldEmit(buffered, state, 100)).isTrue();
    }

    @Test
    void meetsCountThreshold_lowNovelty_doesNotEmit() {
        var policy = new NarrativeEmissionPolicy(gate);
        var state = narrativeStateWithDescription(
                Instant.ofEpochMilli(50), "existing episode description");
        var buffered = List.of(
                event("existing episode description"),
                event("existing episode description"),
                event("existing episode description"));
        assertThat(policy.shouldEmit(buffered, state, 100)).isFalse();
    }

    @Test
    void quietPeriodBypass_emitsRegardlessOfCount() {
        var policy = new NarrativeEmissionPolicy(gate);
        var longAgo = Instant.ofEpochMilli(0);
        var state = narrativeState(longAgo);
        long now = Duration.ofMinutes(61).toMillis();
        assertThat(policy.shouldEmit(List.of(event("x")), state, now))
                .isTrue();
    }

    @Test
    void quietPeriodNotReached_countsStillApply() {
        var policy = new NarrativeEmissionPolicy(gate);
        var recent = Instant.ofEpochMilli(50);
        var state = narrativeState(recent);
        assertThat(policy.shouldEmit(List.of(event("x")), state, 100))
                .isFalse();
    }

    private NarrativeState narrativeState(Instant synthesisedAt) {
        return narrativeStateWithDescription(synthesisedAt, "existing episode description");
    }

    private NarrativeState narrativeStateWithDescription(
            Instant synthesisedAt, String description) {
        var episode = new IndividualEpisode("ep1", Instant.EPOCH, null,
                List.of(), description, 0.5, List.of());
        return new NarrativeState("a1", "t1", NarrativeScope.INDIVIDUAL,
                List.of(episode), synthesisedAt, 5);
    }
}

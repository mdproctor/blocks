package io.casehub.blocks.summarisation.examples.decision;

import io.casehub.blocks.summarisation.ContentSummariser;
import io.casehub.blocks.summarisation.EventLevel;
import io.casehub.blocks.summarisation.EventStreamBus;
import io.casehub.blocks.summarisation.KeyedSummarisationRunner;
import io.casehub.blocks.summarisation.LevelEvent;
import io.casehub.blocks.summarisation.Summariser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class DecisionNarrativePipelineExampleTest {

    sealed interface TradingSignal {
        String tradeId();
        String step();
        Instant timestamp();
    }
    record AnalystRouting(String tradeId, String step, Instant timestamp,
                          String selectedAnalyst, double score) implements TradingSignal {}
    record HistoricalMatch(String tradeId, String step, Instant timestamp,
                           int matchCount, double topSimilarity) implements TradingSignal {}
    record TradeResult(String tradeId, String step, Instant timestamp,
                       String outcome, Duration elapsed) implements TradingSignal {}

    record TradeStepSummary(String tradeId, String step,
                            List<Map<String, String>> signalFacts, Instant from, Instant to) {}
    record TradeNarrative(String tradeId, List<String> steps, String explanation,
                          double confidence, Instant producedAt) {}

    static final EventLevel L0 = new EventLevel("trading-signals", 0);
    static final EventLevel L1 = new EventLevel("step-summaries", 1);
    static final EventLevel L2 = new EventLevel("narratives", 2);

    EventStreamBus<TradingSignal> signalBus;
    EventStreamBus<TradeStepSummary> stepBus;
    EventStreamBus<TradeNarrative> narrativeBus;
    KeyedSummarisationRunner<String, TradingSignal, TradeStepSummary> l1;
    KeyedSummarisationRunner<String, TradeStepSummary, TradeNarrative> l2;

    List<TradeStepSummary> capturedSteps;
    List<TradeNarrative> capturedNarratives;

    @BeforeEach
    void setUp() {
        signalBus = new EventStreamBus<>();
        stepBus = new EventStreamBus<>();
        narrativeBus = new EventStreamBus<>();

        capturedSteps = new ArrayList<>();
        capturedNarratives = new ArrayList<>();

        stepBus.subscribe(e -> true, e -> capturedSteps.add(e.payload()));
        narrativeBus.subscribe(e -> true, e -> capturedNarratives.add(e.payload()));

        Summariser.SyncSummariser<TradingSignal, TradeStepSummary> heuristic = batch -> {
            if (batch.isEmpty()) return List.of();
            var first = batch.get(0).payload();
            var facts = batch.stream().map(e -> switch (e.payload()) {
                case AnalystRouting r -> Map.of("type", "routing", "analyst", r.selectedAnalyst(), "score", String.valueOf(r.score()));
                case HistoricalMatch h -> Map.of("type", "historical", "matches", String.valueOf(h.matchCount()), "similarity", String.valueOf(h.topSimilarity()));
                case TradeResult t -> Map.of("type", "result", "outcome", t.outcome(), "elapsed", t.elapsed().toString());
            }).toList();
            return List.of(new TradeStepSummary(first.tradeId(), first.step(), facts,
                    batch.get(0).payload().timestamp(), batch.get(batch.size() - 1).payload().timestamp()));
        };

        ContentSummariser<TradeStepSummary, TradeNarrative> narrative = (items, previous) -> {
            var steps = new ArrayList<String>();
            if (previous != null) steps.addAll(previous.steps());
            items.forEach(s -> steps.add(s.step()));
            var sb = new StringBuilder();
            if (previous != null) sb.append(previous.explanation()).append(" ");
            items.forEach(s -> sb.append("Step ").append(s.step()).append(": ")
                    .append(s.signalFacts().size()).append(" signals. "));
            return CompletableFuture.completedFuture(
                    new TradeNarrative(items.get(0).tradeId(), steps, sb.toString().strip(), 0.85, Instant.now()));
        };

        l1 = new KeyedSummarisationRunner<>(
                e -> e.payload().tradeId() + ":" + e.payload().step(),
                group -> group.stream().anyMatch(e -> e.payload() instanceof TradeResult),
                5_000L,
                Summariser.ofSync(heuristic),
                stepBus, L1);

        l2 = new KeyedSummarisationRunner<>(
                e -> e.payload().tradeId(),
                group -> group.size() >= 1,
                10_000L,
                narrative.asSummariser(),
                narrativeBus, L2);

        signalBus.subscribe(e -> true, l1::collect);
        stepBus.subscribe(e -> true, l2::collect);
    }

    @Test
    void singleTrade_threeSteps_producesIncrementalNarrative() {
        var t = Instant.parse("2026-09-11T10:00:00Z");

        emit("trade-1", "select-analyst", t, new AnalystRouting("trade-1", "select-analyst", t, "analyst-3", 0.87));
        emit("trade-1", "select-analyst", t, new HistoricalMatch("trade-1", "select-analyst", t.plusMillis(10), 5, 0.92));
        emit("trade-1", "select-analyst", t, new TradeResult("trade-1", "select-analyst", t.plusMillis(20), "ROUTED", Duration.ofMillis(20)));
        tick(t);

        assertThat(capturedSteps).hasSize(1);
        assertThat(capturedSteps.get(0).signalFacts()).hasSize(3);

        tick(t.plusMillis(1));
        assertThat(capturedNarratives).hasSize(1);

        emit("trade-1", "execute", t.plusSeconds(1), new TradeResult("trade-1", "execute", t.plusSeconds(2), "FILLED", Duration.ofSeconds(1)));
        tick(t.plusSeconds(2));
        tick(t.plusSeconds(2).plusMillis(1));

        assertThat(capturedNarratives).hasSize(2);
        assertThat(capturedNarratives.get(1).steps()).containsExactly("select-analyst", "execute");
    }

    @Test
    void multipleTrades_isolatedPipelines() {
        var t = Instant.parse("2026-09-11T10:00:00Z");

        emit("trade-1", "step1", t, new TradeResult("trade-1", "step1", t, "OK", Duration.ofMillis(10)));
        emit("trade-2", "step1", t, new TradeResult("trade-2", "step1", t, "OK", Duration.ofMillis(10)));
        tick(t);
        tick(t.plusMillis(1));

        assertThat(capturedNarratives).hasSize(2);
        var tradeIds = capturedNarratives.stream().map(TradeNarrative::tradeId).toList();
        assertThat(tradeIds).containsExactlyInAnyOrder("trade-1", "trade-2");
    }

    @Test
    void multiTenant_signalsDontCrossContaminate() {
        var t = Instant.parse("2026-09-11T10:00:00Z");

        signalBus.publish(new LevelEvent<>(
                new TradeResult("trade-1", "step1", t, "OK", Duration.ofMillis(10)),
                t.toEpochMilli(), L0, "tenant-A"));
        signalBus.publish(new LevelEvent<>(
                new TradeResult("trade-2", "step1", t, "OK", Duration.ofMillis(10)),
                t.toEpochMilli(), L0, "tenant-B"));

        tick(t);
        tick(t.plusMillis(1));

        assertThat(capturedNarratives).hasSize(2);
    }

    @Test
    void staleTimeout_firesForIncompleteGroups() {
        var t = Instant.parse("2026-09-11T10:00:00Z");

        emit("trade-1", "step1", t, new AnalystRouting("trade-1", "step1", t, "analyst-1", 0.5));
        l1.tick(t.toEpochMilli() + 6_000);

        assertThat(capturedSteps).hasSize(1);
    }

    @Test
    void eviction_resetsL2State() {
        var t = Instant.parse("2026-09-11T10:00:00Z");

        emit("trade-1", "step1", t, new TradeResult("trade-1", "step1", t, "OK", Duration.ofMillis(10)));
        tick(t);
        tick(t.plusMillis(1));

        l2.evictState("trade-1");

        emit("trade-1", "step2", t.plusSeconds(1), new TradeResult("trade-1", "step2", t.plusSeconds(1), "OK", Duration.ofMillis(10)));
        tick(t.plusSeconds(1));
        tick(t.plusSeconds(1).plusMillis(1));

        assertThat(capturedNarratives).hasSize(2);
        assertThat(capturedNarratives.get(1).steps()).containsExactly("step2");
    }

    private void emit(String tradeId, String step, Instant t, TradingSignal signal) {
        signalBus.publish(new LevelEvent<>(signal, t.toEpochMilli(), L0, null));
    }

    private void tick(Instant t) {
        l1.tick(t.toEpochMilli());
        l2.tick(t.toEpochMilli());
    }
}

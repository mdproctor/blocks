# Layered Event Summarisation — Design Spec

**Date:** 2026-07-07
**Issues:** #27 (extraction), #38 (domain-agnostic examples)
**Status:** Draft
**Repo:** casehubio/blocks

## Problem

quarkmind built a generic temporal abstraction framework for summarising
high-frequency event streams into progressively higher-level abstractions.
The framework lives in quarkmind under `io.casehub.blocks.summarisation`,
explicitly packaged for migration to blocks. It is pure Java — zero CDI,
zero Quarkus dependencies — and has been validated by 3 summarisers and
3 window policies across quarkmind's SC2 domain.

The framework belongs in blocks because it meets the scope criteria: it
involves LLM-driven decision-making (async `Summariser` interface designed
for LLM-backed summarisation at upper levels) and composes across foundational
platform parts (event buses, windowed accumulation, pluggable summarisation).

The extraction is blocked on two API improvements identified during
first-principles review, and on domain-agnostic example tests that prove
portability beyond SC2.

## Background

### Academic foundation

The hierarchical summarisation pattern has deep academic roots:

- **Clinical temporal abstraction** (Shahar, RESUME system, 1997+) — the
  canonical domain, with 30+ years of research on abstracting raw
  time-stamped patient data into episodes, phases, and narratives.
- **Chain of Summarization** (Ma et al., 2023, arXiv:2312.11865) — LLM
  agents for StarCraft II using single-frame compression, multi-frame
  aggregation, and CoT reasoning. Independently validates our architecture.
- **Multi-temporal-scale event detection** (MulTemS, 2025) — IoT sensor
  streams with hierarchical time-granularity detection.
- **Temporal GNNs for AML** (2024-2025) — financial transaction streams
  with multi-granularity scoring at transaction and actor levels.

### The hierarchy

```
Level 0 — Raw event stream (high frequency)
Level 1 — Classified events (threshold/pattern detection)
Level 2 — Episodes (bounded event sequences with lifecycle)
Level 3 — Phases (time-windowed classification)
Level 4 — Narratives (LLM-backed or rule-based summarisation)
```

Levels 0-2 are typically classical AI (CEP, rule-based). Levels 3-4 are
where LLM summarisation adds value. The framework is technology-split —
deterministic lower layers, pluggable `Summariser` interface for upper
layers.

## Design

### Package

```
io.casehub.blocks.summarisation
```

Follows the existing blocks pattern (`blocks.channel`, `blocks.conversation`,
`blocks.routing`, `blocks.agentic`). New top-level package under `blocks`.

### Types extracted (7 source files)

| Type | Kind | Purpose |
|------|------|---------|
| `EventLevel` | Record | `(String name, int ordinal)` — identifies a level in the hierarchy |
| `LevelEvent<E>` | Record | `(E payload, long timestamp, EventLevel level)` — typed event at a specific level |
| `WindowPolicy` | Record | `(long maxAge, int maxCount)` — dual-trigger windowing (emit on either threshold) |
| `EventAccumulator<E>` | Class | Collects events, tracks window state, `shouldEmit(now)`, `drain()`, `clear()`. Not thread-safe. |
| `EventStreamBus<E>` | Class | Predicate-based pub/sub: `subscribe(Predicate, Consumer)`, `publish(LevelEvent)`, `clear()`. Not thread-safe. |
| `Summariser<IN, OUT>` | Interface | `@FunctionalInterface`. `CompletionStage<List<OUT>> summarise(List<LevelEvent<IN>>)`. Includes `ofSync()` factory. |
| `SummarisationRunner<IN, OUT>` | Class | Wires accumulator -> summariser -> output bus. API: `collect()`, `tick()`, `clear()`, `size()`. |

### Tests extracted (3 test files)

| Test | Coverage |
|------|----------|
| `EventAccumulatorTest` | 6 tests — timestamp trigger, count trigger, dual trigger, drain, clear, empty guard |
| `EventStreamBusTest` | 4 tests — pub/sub, predicate filtering, multiple subscribers, no-subscribers |
| `SummarisationRunnerTest` | 4 tests — windowing + publication, no-emit guard, level/timestamp wrapping, clear delegation |

All plain JUnit 5 + AssertJ. No CDI, no Quarkus. Zero-dependency tests.

### API improvements (2 changes, made in quarkmind before extraction)

#### 1. Remove `EventConsumer`

**Current:** `EventConsumer<E>` interface with single method `Predicate<E> eventFilter()`.

**Problem:** No framework class references it. `EventStreamBus.subscribe()` takes
`Predicate<E>` directly. The sole consumer is quarkmind's `MomentConsumer extends
EventConsumer<GameMoment>`, which adds its own domain logic. The extends clause
provides zero value — `MomentConsumer` works identically as a standalone interface.

**Change:** Delete `EventConsumer.java` from the framework package. Update
`MomentConsumer` in quarkmind to remove the `extends` clause:

```java
// Before
public interface MomentConsumer extends EventConsumer<GameMoment> { ... }

// After
public interface MomentConsumer { ... }
```

**Impact:** quarkmind only. `MomentConsumer` retains its `eventFilter()` default
method — the method signature is unchanged, only the supertype is removed. No
other consumers exist. `EventConsumer.java` is not extracted to blocks.

#### 2. Change `SummarisationRunner.tick()` return type

**Current:** `public void tick(long now)`

**Problem:** The `CompletionStage` from `summarise()` is consumed with `thenAccept`
but no `exceptionally()` handler exists. Async errors from LLM-backed summarisers
vanish silently. The buffer is already drained when the error occurs — events are
lost AND the failure is invisible. The Chain of Summarization paper (Ma et al.)
confirms LLM summarisers are a primary use case, not an edge case.

**Change:**

```java
// Before
public void tick(long now) {
    if (!accumulator.shouldEmit(now)) return;
    var batch = accumulator.drain();
    summariser.summarise(batch).thenAccept(results -> {
        for (var payload : results) {
            outputBus.publish(new LevelEvent<>(payload, now, outputLevel));
        }
    });
}

// After
public CompletionStage<Void> tick(long now) {
    if (!accumulator.shouldEmit(now))
        return CompletableFuture.completedFuture(null);
    var batch = accumulator.drain();
    return summariser.summarise(batch).thenAccept(results -> {
        for (var payload : results) {
            outputBus.publish(new LevelEvent<>(payload, now, outputLevel));
        }
    });
}
```

**Behavioural impact:**
- Sync callers: no change. The returned `CompletionStage` is already complete
  (sync summarisers wrap in `CompletableFuture.completedFuture()`). Existing
  fire-and-forget callers continue to work — they just ignore the return value.
- Async callers: can now chain `.exceptionally()` to handle LLM failures.
- Batch loss on error is accepted: standard tumbling-window at-most-once
  semantics. The framework's job is to make failure visible, not to retry.

**Call site updates in quarkmind:**

`SummarisationLifecycle.tick()`:
```java
// Before
@Override
public void tick(long gameFrame) {
    phaseRunner.tick(gameFrame);
    arcRunner.tick(gameFrame);
}

// After — sync summarisers, stage is already complete
@Override
public void tick(long gameFrame) {
    phaseRunner.tick(gameFrame);
    arcRunner.tick(gameFrame);
}
```

No change needed — `SummarisationTickable.tick()` returns void, and the
`CompletionStage` return value is safely ignored for sync summarisers.
If `SummarisationTickable` later needs to propagate async results, the
interface can be updated then. The important thing is that `SummarisationRunner`
now exposes the information.

### Design decisions retained

These were evaluated during first-principles review and confirmed as correct:

| Decision | Rationale |
|----------|-----------|
| `long timestamp` on `LevelEvent` (not `Instant`) | Works for both game frames and epoch millis. More general. |
| `Predicate<E>` for bus subscription filter (not `Predicate<LevelEvent<E>>`) | Each bus carries one event type. Filtering on `EventLevel` within a bus is pointless. |
| No unsubscribe on `EventStreamBus` | No consumer ever removes individual subscriptions. `clear()` handles full reset. |
| No error handling in `EventStreamBus.publish()` | Callers control their own callbacks. Document, don't enforce. |
| No back-pressure mechanism | In-process streaming framework, not distributed messaging. |
| Runner owns its `EventAccumulator` (encapsulated since quarkmind#232) | Consumers configure via `WindowPolicy`, never interact with accumulator directly. Direct accumulator use is a separate pattern (Pattern B). |
| `CompletionStage` for async (not `Uni`) | Pure Java, zero framework dependency. Consumers convert with `Uni.createFrom().completionStage()` if needed. |
| `CopyOnWriteArrayList` for bus subscriptions | Safe iteration during publish (callback-triggered subscribe won't CME). Overall class documented as not thread-safe. |

### Dependencies

No new compile dependencies. The framework is pure Java — `CompletionStage`
and `CompletableFuture` are JDK. Blocks' existing test dependencies (JUnit 5,
AssertJ) are sufficient for all tests including examples.

### Example domains (#38)

Two domain examples prove the framework is genuinely portable. Both live
in the test source root under an `examples` package:

```
src/test/java/io/casehub/blocks/summarisation/examples/
    clinical/    — clinical temporal abstraction
    logistics/   — logistics hub monitoring
```

#### Clinical temporal abstraction

Based on Shahar's taxonomy — the canonical academic domain for temporal
abstraction. Maps directly to our level hierarchy.

**Domain records:**

| Record | Level | Fields |
|--------|-------|--------|
| `VitalReading` | L1 input | `type` (HR, BP, SpO2, RR, TEMP), `value`, `unit` |
| `ClinicalEvent` | L2 output | `category` (TACHYCARDIA, HYPOXEMIA, HYPERTENSION, ...), `severity` (MILD, MODERATE, CRITICAL), `description`, `rationale` |
| `CarePhase` | L3 output | `phase` (STABLE_MONITORING, ACUTE_DETERIORATION, RECOVERY, ...), `durationSeconds`, `rationale` |
| `ClinicalNarrative` | L4 output | `summary`, `timestamp` |

**Pipeline:**

```
VitalReading bus (L1)
    → ClinicalEventSummariser (sync, rule-based: threshold classification)
    → WindowPolicy(maxCount=5)
    → ClinicalEvent bus (L2)
        → CarePhaseSummariser (sync, rule-based: windowed phase classification)
        → WindowPolicy(maxAge=900_000)  // 15 minutes
        → CarePhase bus (L3)
            → NarrativeSummariser (async, simulates LLM call)
            → WindowPolicy(maxCount=3)
            → ClinicalNarrative bus (L4)
```

**Tests demonstrate:**
- Full pipeline: L1 vitals -> L2 episodes -> L3 phases -> L4 narrative
- Severity escalation: normal vitals -> abnormal -> critical episode detection
- Phase transitions: stable -> deterioration -> recovery
- Async summariser with `CompletionStage` (simulates LLM latency)
- Error handling via `tick()` return type (async failure scenario)
- Rationale traces at every level

#### Logistics hub monitoring

Different domain, different event shapes — proves the framework's type
parameters work with arbitrary payload types.

**Domain records:**

| Record | Level | Fields |
|--------|-------|--------|
| `PackageScan` | L1 input | `scanId`, `warehouseId`, `weight`, `destination`, `scanType` (INBOUND, OUTBOUND, TRANSFER) |
| `PackageAnomaly` | L2 output | `type` (MISROUTE, WEIGHT_MISMATCH, DELAY, DAMAGE), `severity`, `details`, `rationale` |
| `HubPhase` | L3 output | `phase` (NORMAL_FLOW, CONGESTION, RECOVERY), `durationSeconds`, `rationale` |
| `HubNarrative` | L4 output | `summary`, `timestamp` |

**Pipeline:**

```
PackageScan bus (L1)
    → AnomalyDetectorSummariser (sync, rule-based: pattern detection)
    → WindowPolicy(maxCount=10)
    → PackageAnomaly bus (L2)
        → HubPhaseSummariser (sync, rule-based: congestion classification)
        → WindowPolicy(maxAge=300_000)  // 5 minutes
        → HubPhase bus (L3)
            → HubNarrativeSummariser (async, simulates LLM)
            → WindowPolicy(maxCount=2)
            → HubNarrative bus (L4)
```

**Tests demonstrate:**
- Full pipeline with different event shapes than clinical
- Count-based windowing (L1->L2) vs time-based windowing (L2->L3)
- Independent `EventAccumulator` usage without `SummarisationRunner` (Pattern B)
- Multi-level subscription: single consumer on L2 + L3 buses
- Structured domain records with typed fields (not flat strings)

### Both examples validate

| Aspect | How |
|--------|-----|
| Type safety | Different generic parameters across two domains |
| Sync + async | `Summariser.ofSync()` at L1-L2, raw `CompletionStage` at L3-L4 |
| Count + time windowing | `maxCount` triggers at lower levels, `maxAge` at upper levels |
| Rationale traces | Every output record carries human-readable reasoning |
| Error observability | Async failure test using `tick()` return type |
| Pattern B (independent accumulator) | Direct `EventAccumulator` + `WindowPolicy` without `SummarisationRunner` |
| Multi-bus subscription | Consumer subscribing to multiple level buses simultaneously |

## Extraction workflow

### Phase 1: Improve in quarkmind (on main)

Quarkmind commits on main are approved for consolidation work (CLAUDE.md
"Cross-Repo Consolidation Commits"). Each improvement is a separate commit
with tests run between.

1. Remove `EventConsumer.java`, update `MomentConsumer` — commit, run tests
2. Change `tick()` return type, update `SummarisationRunnerTest` — commit, run tests

### Phase 2: Extract to blocks (on issue-27 branch)

3. Create `io.casehub.blocks.summarisation` package in blocks
4. Copy 7 source files + 3 test files from quarkmind
5. Run blocks tests to validate — commit

### Phase 3: Wire quarkmind to blocks (on quarkmind main)

6. Add `casehub-blocks` as a compile dependency in quarkmind's `pom.xml`
7. Remove the 7 source files + 3 test files from quarkmind
8. Run quarkmind's full test suite to validate the dependency switch — commit

### Phase 4: Domain-agnostic examples (#38, on blocks branch)

9. Clinical domain records + pipeline + tests
10. Logistics domain records + pipeline + tests
11. Run full blocks test suite — commit

### Phase 5: Documentation

12. Update blocks CLAUDE.md with new package documentation
13. Update #38 issue as complete

## Related issues

- [#40](https://github.com/casehubio/blocks/issues/40) — qhorus channel
  integration for summarisation (bidirectional bridge). The summarisation
  hierarchy maps naturally to qhorus's Space -> Channel -> Topic -> Thread
  hierarchy. Correlation chains map to episodes, topics map to phases,
  channels carry the event stream. Should develop in parallel with qhorus#328.
- [#41](https://github.com/casehubio/blocks/issues/41) — generalised
  summarisation pipeline viewer. Concept mockup at
  `docs/assets/summarisation-viewer-concept.svg`.

## Consumers

| Repo | Current usage | Post-extraction |
|------|--------------|-----------------|
| quarkmind | Source (framework + SC2 domain summarisers) | Depends on blocks, keeps SC2-specific `GamePhaseSummariser`, `GameArcSummariser`, `SummarisationLifecycle` |
| Future: IoT | — | L1 sensor readings -> L2 anomalies -> L3 operational phases -> L4 maintenance narratives |
| Future: AML | — | L1 transactions -> L2 behavioural anomalies -> L3 investigation phases -> L4 SAR narratives |
| Future: clinical | — | L1 vitals -> L2 clinical episodes -> L3 care phases -> L4 clinical narratives |

## Acceptance criteria

- [ ] `EventConsumer` removed from quarkmind, `MomentConsumer` updated
- [ ] `tick()` returns `CompletionStage<Void>`, all quarkmind tests pass
- [ ] 7 source + 3 test files extracted to blocks, all pass
- [ ] quarkmind depends on blocks, local copies removed, all quarkmind tests pass
- [ ] Clinical example: full L1->L4 pipeline with sync + async summarisers
- [ ] Logistics example: full L1->L4 pipeline with different event shapes
- [ ] Both examples demonstrate rationale traces, windowing modes, error observability
- [ ] CLAUDE.md updated with summarisation package documentation
- [ ] Zero new compile dependencies added to blocks

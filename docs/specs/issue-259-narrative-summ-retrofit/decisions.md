# Decisions — #259 Narrative Summarisation Retrofit

## D1: Pull-to-push adapter location

**Choice:** Self-contained adapter in the narrative package
**Alternatives:**
- Modify reflection producer (MemoryHygieneOrchestrator) to publish directly — couples memory hygiene to summarisation pipeline, leaks EventStreamBus into unrelated package
**Rationale:** Keeps #259 self-contained. Matches established ChannelEventAdapter pattern. The adapter is tick-driven (called from orchestrator cycle), queries ReflectionQueryStore.findSince() with a watermark, publishes each as LevelEvent<ReflectionEntry>.
**Trade-offs:** Still fundamentally poll-based under the hood, but the orchestrator tick cycle already drives the cadence and the adapter is trivial.
**Sources:** ChannelEventAdapter.java, NarrativeSynthesiser.java (existing pull logic), issue #259 ("pull-to-push adapter")
**Exploration:** quick
**Status:** captured

## D2: EmissionPolicy integration with SummarisationRunner

**Choice:** Runner-level evaluation — SummarisationRunner evaluates EmissionPolicy in tick(), EventAccumulator gets peekBuffer(), stays a dumb buffer
**Alternatives:**
- Accumulator generification (EventAccumulator<E, S>) — pollutes accumulator's type signature with state it doesn't own
- New PolicyAccumulator type — adds composition glue type without clear value
**Rationale:** Puts policy evaluation where the state lives (the runner). EventAccumulator remains simple. WindowPolicy backward compat via adapter that ignores state. Atomicity relaxation between peek and drain is harmless — tick() is synchronized, only collect() can interleave (drains one extra event at most).
**Trade-offs:** peekBuffer() exposes internal buffer as read-only snapshot — minor API surface addition to EventAccumulator.
**Sources:** SummarisationRunner.java (tick/invokeSummariser), EventAccumulator.java (shouldEmit/drainIfReady)
**Exploration:** quick
**Status:** captured

## D3: SummarisationRunner API shape for new SPIs

**Choice:** Builder pattern — SummarisationRunner.builder(summariser, outputBus, outputLevel) with fluent .emissionPolicy(), .stateStore(), .outputProcessor(), .compactor(), .onFailure(). Existing WindowPolicy constructors unchanged. Apply same pattern to KeyedSummarisationRunner.
**Alternatives:**
- Overloaded constructors — combinatorial explosion with 3 optional params × with/without compactor × with/without onFailure
- Config record — pushes complexity into a separate SummarisationRunnerConfig type without adding clarity
**Rationale:** Builders handle optional parameters cleanly, existing code is untouched, extensible for future SPIs. Idiomatic Java.
**Trade-offs:** Builder adds inner class boilerplate. Existing constructors coexist (minor API surface duplication during migration).
**Depends on:** D2 (EmissionPolicy integration at runner level)
**Sources:** SummarisationRunner.java (4 existing constructors), KeyedSummarisationRunner.java (4 existing constructors)
**Exploration:** quick
**Status:** captured

## D4: StateStore design

**Choice:** Regular interface (not @FunctionalInterface) with load(String partitionKey) → @Nullable S and store(String partitionKey, S state). String partition key. SummarisationRunner uses tenancyId; KeyedSummarisationRunner converts K.toString().
**Alternatives:**
- @FunctionalInterface — impossible with two abstract methods
- Generic key type StateStore<K, S> — over-engineered for a persistence SPI where keys are always serializable strings
**Rationale:** Persistence SPIs naturally use string keys. CbrNarrativeStore already uses string keys (scopeId, tenantId). Write-through cache in the runner: read from ConcurrentHashMap first, fall back to StateStore.load(), always write to both.
**Trade-offs:** KeyedSummarisationRunner callers must ensure K.toString() is a meaningful partition key.
**Depends on:** D2 (runner-level state management)
**Sources:** CbrNarrativeStore.java (string-keyed load/store), SummarisationRunner.java:20 (partitionState map)
**Exploration:** quick
**Status:** captured

## D5: NarrativeSynthesiser refactoring boundary

**Choice:** ContentSummariser<ReflectionEntry, NarrativeState> keeps: prompt assembly, LLM invocation, JSON parsing, episode/theme building, merge (existing + new episodes, full theme re-derivation). Moves out: gate logic → NarrativeEmissionPolicy (EmissionPolicy impl), pruning → NarrativeOutputProcessor (OutputProcessor impl), reflection fetching → pull-to-push adapter (D1), state persistence → StateStore/runner, per-agent locking → runner's synchronized tick(). ContentSummariser stays @ApplicationScoped (CDI-injected AgentProvider).
**Alternatives:**
- Keep everything in one class, just implement ContentSummariser interface — defeats the purpose of the pipeline abstraction
- Split further (separate prompt assembler, parser, builder classes) — over-decomposition for a single-responsibility class
**Rationale:** Clean separation: ContentSummariser = pure LLM synthesis logic. All cross-cutting concerns (gating, pruning, persistence, concurrency) move to reusable pipeline SPIs.
**Trade-offs:** NarrativeSynthesiser loses its self-contained nature — behavior now distributed across 4 components. Debuggability requires understanding the pipeline flow.
**Depends on:** D1 (adapter), D2 (EmissionPolicy), D3 (builder)
**Sources:** NarrativeSynthesiser.java:87-209 (doSynthesise flow), ContentSummariser.java (asSummariser bridge)
**Exploration:** quick
**Status:** captured

## D6: WindowPolicy backward compatibility

**Choice:** WindowPolicy does not implement EmissionPolicy directly (type parameter mismatch). Builder's windowPolicy(WindowPolicy) method wraps it internally as EmissionPolicy<Object, Object> that ignores state and delegates to count/age checks. Existing constructors unchanged — they continue to create EventAccumulator with WindowPolicy internally.
**Alternatives:**
- Make WindowPolicy implement EmissionPolicy<Object, Object> — couples a simple value record to the emission SPI hierarchy
- Deprecate WindowPolicy constructors — premature, breaks existing consumers
**Rationale:** Keeps WindowPolicy as a simple record. The adapter is internal to the builder — no public API change. Existing code paths are untouched.
**Trade-offs:** Two code paths in SummarisationRunner (legacy WindowPolicy-based and new EmissionPolicy-based). Can be unified later if WindowPolicy constructors are deprecated.
**Sources:** WindowPolicy.java, EventAccumulator.java:24-28 (shouldEmit uses WindowPolicy directly)
**Exploration:** quick
**Status:** captured

## D7: Pipeline factory

**Choice:** New NarrativePipeline @ApplicationScoped factory (mirrors DecisionNarrativePipeline pattern). Wires: pull-to-push adapter → EventStreamBus<ReflectionEntry> → SummarisationRunner (built via builder with NarrativeEmissionPolicy, CbrStateStore adapter, NarrativeOutputProcessor, ContentSummariser.asSummariser()). Tick-driven by InnerLifeOrchestrator (replaces direct synthesiser.synthesiseIfNeeded() call).
**Alternatives:**
- Wire components directly in InnerLifeOrchestrator — InnerLifeOrchestrator already has too many responsibilities
- Standalone runner without factory — loses CDI lifecycle management (start/stop tick scheduler)
**Rationale:** Mirrors the proven DecisionNarrativePipeline pattern from #241. Centralizes pipeline wiring. CDI lifecycle handles start/stop.
**Trade-offs:** NarrativeOrchestrator unchanged — reads from NarrativeStore (CbrNarrativeStore) independently of the pipeline's write-through cache. InnerLifeOrchestrator changes from calling synthesiser.synthesiseIfNeeded() to pipeline.tick().
**Depends on:** D1, D2, D3, D4, D5
**Sources:** DecisionNarrativePipeline.java (factory pattern reference), InnerLifeOrchestrator.java (current synthesiser integration)
**Exploration:** quick
**Status:** captured

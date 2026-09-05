## D1: LevelEvent tenancyId — clean break, no convenience factory

**Choice:** Add `@Nullable String tenancyId` as 4th record component. No convenience factory — all callers pass tenancyId explicitly (null or a value).
**Alternatives:**
- Convenience factory `LevelEvent.of(payload, timestamp, level)` — backward-compatibility shim; hides the tenancyId decision point behind a default
**Rationale:** Breaking change is the point: every caller must choose whether they are tenant-aware. A process guard ("code review must catch") is weaker than a design guard (compiler forces the choice). ~6 production call sites and ~90 test call sites — mechanical migration.
**Trade-offs:** Noisier diffs for the migration commit. Every test fixture must pass an explicit null tenancyId.
**Sources:** LevelEvent.java, #231 issue body, R1-01 reviewer challenge
**Exploration:** quick
**Status:** revised — removed convenience factory per R1-01

## D2: CloudEvent bridge — plain Java SPI, no CDI dependency

**Choice:** CloudEvent bridge module defines a `@FunctionalInterface EventSink<T>` for emission. No quarkus-arc dependency. Consumer provides CDI wiring as a lambda.
**Alternatives:**
- Direct quarkus-arc dependency — couples module to Quarkus, untestable without CDI container
**Rationale:** Keeps the module testable with plain JUnit. Follows blocks module patterns (VouchService, Summariser — consumer-constructed, not CDI-managed).
**Trade-offs:** Consumer writes ~1 extra line of wiring code per pipeline.
**Sources:** #232 issue body, blocks module patterns, R1-02 reviewer challenge
**Exploration:** quick
**Status:** captured

## D3: YAML pipeline topology — implicit chaining

**Choice:** Levels are ordered — level N's output feeds level N+1's input. A single `source:` block defines the external input. Linear topology enforced by construction.
**Alternatives:**
- Explicit source per level — enables future non-linear topologies but adds verbosity
**Rationale:** Issue specifies "linear topology only — non-linear requires Tier 2." Implicit chaining enforces this at the grammar level. YAGNI.
**Trade-offs:** Non-linear topologies require Tier 2 (Java code).
**Sources:** #233 issue body, LogisticsPipelineTest.java
**Exploration:** quick
**Status:** captured

## D4: Grouping — `grouping:` block with type discriminator

**Choice:** A single `grouping:` block with a `type:` discriminator. Type values: `keyed` and `windowed`.
**Alternatives:**
- Mutually exclusive `keyed:` / `window:` top-level blocks — doesn't scale to additional grouping modes
**Rationale:** Two mutually exclusive top-level blocks is manageable; five is not. Session windowing and sliding windows could be added as new `type:` values.
**Trade-offs:** One extra nesting level in YAML.
**Sources:** KeyedSummarisationRunner.java, WindowPolicy.java, R1-04 reviewer challenge
**Exploration:** quick
**Status:** revised — adopted grouping: block with type discriminator per R1-04

## D5: CloudEvent emission — per-level optional

**Choice:** Any level can declare an `emit:` block with a `cloud-event-type` URI. Non-emitting levels feed the next level internally only.
**Alternatives:**
- Terminal-only emission — prevents RAS Ganglia from subscribing to mid-pipeline events
**Rationale:** desiredstate#74 describes RAS consuming events at multiple altitude levels. Per-level emission enables this without separate pipelines. Depends on D2 (EventSink<T>).
**Trade-offs:** Each emitting level needs a CloudEventEmitter instance.
**Sources:** casehubio/casehub-desiredstate#74, #232 issue body, R1-05 reviewer challenge
**Exploration:** quick
**Status:** revised — added D2 dependency per R1-05

## D6: Pipeline compilation — single SummariserRegistry, dual population

**Choice:** A single `SummariserRegistry` holds all discovered summariser types. Build-time: Jandex populates via recorder. Runtime: callers populate via API. `PipelineCompiler` resolves types against one registry.
**Alternatives:**
- Dual-path compiler with Jandex at build-time and registry at runtime — two type-resolution mechanisms
**Rationale:** The compiler should have exactly one way to resolve types. Whether Jandex or API populated the registry is a population detail.
**Trade-offs:** Dynamic path skips Jandex discovery. Dynamic callers register types explicitly.
**Sources:** #233 issue body, Quarkus extension patterns, R1-06 reviewer challenge
**Exploration:** quick
**Status:** revised — unified type resolution through single registry per R1-06

## D7: Event payload type — generic throughout

**Choice:** The pipeline stays generic (`LevelEvent<E>`) throughout. Tier 1 uses `E = Map<String, Object>`. Tier 2 uses domain types. MVEL3 handles both transparently.
**Alternatives:**
- Map-only Tier 1 — breaks ContextBridge pattern
- JsonNode payload — JQ-native but MVEL3 field access becomes verbose
**Rationale:** Follows the ContextBridge pattern used across the platform. Expression evaluation via MVEL3 handles both typed objects and maps.
**Trade-offs:** JQ (field-extract) requires JsonNode — typed payloads need serialization. Pre-computed aggregates use different code paths for maps vs typed objects.
**Sources:** Platform expression SPI, MVEL3 property access semantics, R1-07 reviewer challenge
**Exploration:** quick
**Status:** revised — acknowledged JQ/aggregate trade-offs per R1-07

## D8: phase-detect initial state — explicit in YAML, framework-managed state

**Choice:** `phase-detect` declares `initial: <STATE>` in YAML. Every new tenant starts there. Transitions evaluated in YAML declaration order (first-match-wins). State is framework-managed per partition (tenant × key), in-memory.
**Alternatives:**
- First-match on first batch — edge cases when nothing matches
**Rationale:** Simple, predictable, no magic. Uses framework state management (D13) for per-partition state.
**Trade-offs:** In-memory state lost on restart. State persistence is Tier 2 territory.
**Sources:** #233 issue body, HubPhaseSummariser.java, R1-08 reviewer challenge
**Exploration:** quick
**Status:** revised — added evaluation order, state management model per R1-08

## D9: Expression engine — platform expression SPI (new precedent)

**Choice:** Depend on `casehub-platform-api` (`ExpressionEngine`, `CompiledExpression<C,R>`, `ExpressionEngineRegistry`). No direct jackson-jq or MVEL3 dependency.
**Alternatives:**
- Bundle own jackson-jq/MVEL3 — duplicates platform infrastructure
- Depend on engine-api ExpressionEngine — coupled to CaseContext
**Rationale:** Platform-api `CompiledExpression<C, R>` is fully generic (`R eval(C)`). New precedent: typed compilation via CompiledExpression rather than the less-typed ExpressionEvaluator.
**Trade-offs:** Consumers must have platform-expression on classpath — but any Quarkus app already does.
**Sources:** javap analysis of platform-api, SituationDefinition.java, R1-09 reviewer challenge
**Exploration:** quick
**Status:** revised — corrected framing: new precedent per R1-09

## D10: Pre-computed batch aggregates with explicit field declaration

**Choice:** YAML declares `aggregate-fields` per level. Runtime pre-computes `size`, `counts.<field>.<value>`, `sums.<field>`, `avgs.<field>`. Raw `batch` available as escape hatch.
**Alternatives:**
- Raw batch only — complex expressions, unpredictable performance
- Speculative aggregation of all fields — unbounded for high-cardinality fields
**Rationale:** Explicit field declaration makes pre-computation bounded and predictable.
**Trade-offs:** One additional YAML line per level.
**Sources:** HubPhaseSummariser.java, AnomalyDetectorSummariser.java, R1-10 reviewer challenge
**Exploration:** quick
**Status:** revised — added explicit aggregate-fields per R1-10

## D11: Tick interval — deployment concern, not pipeline definition

**Choice:** Tick interval is a deployment/runtime parameter, not part of pipeline YAML. Config: `casehub.summarisation.tick-interval-ms` (default: 1000ms).
**Alternatives:**
- Tick interval in YAML — couples operational tuning to pipeline definition
- Computed from WindowPolicy — constrains operational flexibility
**Rationale:** Separation of concerns: pipeline topology in YAML, scheduling frequency in deployment config.
**Trade-offs:** Pipeline authors configure tick scheduling separately.
**Sources:** SummarisationRunner.tick(), R1-12 reviewer challenge
**Exploration:** quick (surfaced by reviewer)
**Status:** captured

## D12: Tenant partitioning — KeyedAccumulator with tenancyId (key extractor API change)

**Choice:** Key extractor widened from `Function<E, K>` to `Function<LevelEvent<E>, K>`, enabling `event.tenancyId()` access for tenant partitioning. Framework interposes tenant partitioning automatically at each pipeline level.
**Alternatives:**
- New TenantAwareAccumulator — duplicates KeyedAccumulator
- One EventAccumulator per tenant — lifecycle management overhead
- Keep `Function<E, K>` and duplicate tenancyId into payload — violates D1
**Rationale:** KeyedAccumulator handles per-key grouping correctly. Widening to `Function<LevelEvent<E>, K>` is the natural companion to D1's breaking change.
**Trade-offs:** Breaking change to KeyedAccumulator/KeyedSummarisationRunner constructors.
**Sources:** KeyedAccumulator.java, R1-13/R2-01 reviewer challenges
**Exploration:** quick (surfaced by reviewer)
**Status:** revised — acknowledged key extractor API change per R2-01

## D13: Unified summarisation model — StatefulSummariser and framework state management

**Choice:** Add `StatefulSummariser<IN, OUT, S> extends Summariser<IN, OUT>` to summarisation-api. The framework manages state per partition (tenant × key). `SummarisationRunner` detects StatefulSummariser and passes/stores state automatically.
**Alternatives:**
- Keep state hidden inside individual summariser implementations (status quo: phase-detect, ContentSummariser) — three separate subsystems with duplicated patterns
- Add state to Summariser directly — forces statefulness on all summarisers
**Rationale:** Blocks has three separate batch-transform subsystems (temporal, content, observation) that share the same fundamental pattern: accumulate → batch → transform. They diverge only in statefulness and output type. A unified model with optional state eliminates bridge adapters, shares accumulation infrastructure, and enables ContentSummariser in YAML pipelines. `StatefulSummariser` extends `Summariser` — stateless summarisers are unchanged. Framework state management (per-partition, in-memory, with future persistence SPI hook) replaces ad-hoc state in each implementation.
**Trade-offs:** Adds one interface and one record to summarisation-api. SummarisationRunner gains state management complexity. Phase-detect must implement StatefulSummariser instead of managing state internally.
**Sources:** ContentSummariserToSummariser.java (bridge adapter that loses state), phase-detect internal state management, ObservationAccumulator reimplementing EventAccumulator, TieredContentSummariser/TieredObservationRenderer duplication
**Exploration:** deep-analysis
**Status:** captured

## D14: ContentSummariser generification — R replaces SummaryResult

**Choice:** `ContentSummariser<T>` becomes `ContentSummariser<T, R>` in summarisation-api (zero deps). The generic `R` replaces the qhorus-api `SummaryResult` dependency. `asSummariser()` default method bridges to `StatefulSummariser<T, R, R>` — no separate bridge class needed. `ContentSummariserToSummariser` is deleted.
**Alternatives:**
- Keep ContentSummariser in blocks with SummaryResult — leaves three separate subsystems, requires bridge adapter
- Define SummaryOutput interface in summarisation-api that SummaryResult implements — requires qhorus-api change
**Rationale:** Generifying the result type breaks the qhorus coupling cleanly. Consumers use `ContentSummariser<Message, SummaryResult>` — the dependency stays with consumers. `asSummariser()` eliminates the bridge adapter and makes ContentSummariser a first-class pipeline citizen with state. Migration is mechanical: add second type parameter, IntelliJ refactoring handles all call sites.
**Trade-offs:** Breaking change: `ContentSummariser<T>` → `ContentSummariser<T, R>` across ~10 consumer files. VerbatimContentSummariser moves to summarisation-api as `ContentSummariser<T, String>` (pure Java).
**Sources:** SummaryResult javap (record: text + annotations — no qhorus-specific behavior), ContentSummariserToSummariser.java (bridge that loses `previous` state)
**Exploration:** deep-analysis
**Status:** captured

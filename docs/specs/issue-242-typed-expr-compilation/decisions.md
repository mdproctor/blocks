# Decisions — #242 Type-safe Expression Compilation

## D1: Layered architecture — per-site compilation + optional descriptors

**Choice:** Two layers. Layer 1: per-site compilation where each expression site in the sealed hierarchy passes its known `Class<?>` to `ExpressionEngine.compile()`. Uses existing platform-api types (`ExpressionEngine`, `CompiledExpression<C, R>`). Zero new infrastructure. Layer 2 (future): descriptor model in yaml-core for dynamic/YAML-declared schemas where the context type isn't statically known. Additive — augments Layer 1, doesn't replace it.
**Alternatives:**
- Descriptor-only (original D1-D5) — over-engineered for the immediate problem; creates parallel type infrastructure when per-site compilation solves all current cases
- Per-site only, no descriptor model — solves #242 but doesn't establish shared infrastructure for Tier 2 summarisation or dynamic pipeline loading
**Rationale:** The reviewer (R1-07) correctly identified that all current expression sites have statically-known context types. Per-site compilation is the simplest correct solution. But Tier 2 summarisation pipelines and dynamic YAML loading will need YAML-declared context schemas — the descriptor model provides that when needed, without complicating Layer 1.
**Trade-offs:** Layer 2 is deferred — no shared descriptor infrastructure in this issue. Consumers needing dynamic schemas will need to wait for Layer 2 or use ExpressionEngine directly with Class<?>.
**Sources:** R1-07 reviewer challenge, issue #242 body ("per-site compilation strategy"), summarisation-yaml D7 ("Tier 1 uses Map, Tier 2 uses domain types")
**Exploration:** quick → revised after light decision review
**Status:** revised — replaces original D1-D5

## D2: Per-site type mapping — sealed hierarchy determines context type

**Choice:** Each expression site in the spec sealed hierarchy maps to a known context type. The compiler switch statement uses the concrete type. No dynamic resolution needed.
**Alternatives:**
- Map<String, Class<?>> lookup table — unnecessary indirection when the switch already knows the type
- Annotation-based site declaration — over-engineered for <10 sites
**Rationale:** The sealed hierarchy IS the type registry. `FirstMatch.guard` → `RoutingCandidate`, `GoalReached.when` → `Map<String, Object>` (MVEL duck-typing for generic T), `ConfidenceThreshold.extractor` → `JudgmentContext`. The compiler already walks the hierarchy — adding the Class<?> at each case is the natural location.
**Trade-offs:** Adding a new expression site requires updating the compiler switch. This is appropriate — each new site is a deliberate design choice that should specify its context type.
**Sources:** PatternCompiler.java (existing switch walk), TerminationConditionRegistry.java (GoalReached throws UnsupportedOperationException), JudgmentTriggerRegistry.java (ConfidenceThreshold throws)
**Exploration:** quick
**Depends on:** D1 (Layer 1 approach)
**Status:** captured

## D3: Generic T sites — compile against Map<String, Object>

**Choice:** For expression sites where the context type is generic `T` (e.g., `GoalReached.when` evaluates against `T` which is only known at runtime), compile against `Map<String, Object>`. MVEL duck-types property access against maps transparently.
**Alternatives:**
- Compile against `Object.class` — throws `IntrospectionException` in MvelExpressionEngine (the root problem in #242)
- Require POJO type declaration in YAML — pushes complexity to YAML authors for Layer 1; appropriate for Layer 2
**Rationale:** Map is MVEL's universal fallback — property access like `score > 0.8` works whether the context is a Map with a "score" key or a POJO with a `getScore()` method. This is exactly what summarisation-yaml already does successfully with ThresholdClassifySummariser.
**Trade-offs:** Loses compile-time property validation for generic-T sites. Misspelled property names become runtime errors, not compile-time errors. Layer 2 descriptors would fix this.
**Sources:** ThresholdClassifySummariser.java line 46 (Map.class cast), MVEL3 MapEvaluator.java, issue #242 body ("GoalReached.when → compile against Map<String, Object>")
**Exploration:** quick
**Depends on:** D2 (per-site mapping)
**Status:** captured

## D4: Reuse CompiledExpression<C, R> from platform-api

**Choice:** Use `CompiledExpression<C, R>` from `casehub-platform-api` directly. No new handle type. The registries compile to `CompiledExpression` and wire it into the runtime SPIs.
**Alternatives:**
- New CompiledHandle<C, R> in yaml-core — duplicates CompiledExpression, violates platform capability ownership
**Rationale:** `CompiledExpression<C, R>` already has `R eval(C context)` and is backed by MVEL3-generated typed bytecode. `ThresholdClassifySummariser` already uses it. Summarisation-yaml D9 explicitly established this as the precedent. No reason to create a parallel type.
**Trade-offs:** Both blocks yaml modules already depend on platform-api (provided scope). No new dependency.
**Sources:** CompiledExpression.java (platform-api), ThresholdClassifySummariser.java (existing usage), summarisation-yaml D9 ("typed compilation via CompiledExpression")
**Exploration:** quick
**Depends on:** D1 (Layer 1 uses existing types)
**Status:** captured

## D5: Layer 2 descriptor model — yaml-core, deferred

**Choice:** Descriptor model (`TypeDescriptor`, `PropertyDescriptor`, `ExpressionCompiler` SPI) lives in yaml-core with zero deps. String-based type names. Bridge module resolves strings to `Class<?>` via `Map<String, Class<?>>` type resolver. Deferred to a future issue — not in scope for #242.
**Alternatives:**
- Implement now — over-engineered for current needs; no consumer exists yet
- Never implement — Tier 2 summarisation and dynamic loading will eventually need it
**Rationale:** The descriptor model is the right long-term architecture for YAML-declared schemas, but no current consumer needs it. All current expression sites have statically-known types. Build it when the first consumer (likely Tier 2 summarisation or dynamic pipeline loading) materialises.
**Trade-offs:** Tier 2 consumers must wait. Layer 1 per-site compilation covers all current use cases.
**Sources:** summarisation-yaml D7 ("Tier 2 uses domain types"), yaml-core pom.xml (zero deps constraint), MVEL3 ContextType/Type/Declaration model
**Exploration:** quick
**Depends on:** D1 (layered architecture)
**Status:** captured

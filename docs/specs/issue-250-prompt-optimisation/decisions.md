## D1: Reuse existing config records directly

**Choice:** OptimiserConfig, SafetyConfig, and FewShotExample are used as-is in the YAML surface — no wrapper spec records.
**Alternatives:**
- Wrapper spec records (e.g. OptimiserConfigSpec) — adds indirection for no benefit since the records are already pure Jackson-compatible types
**Rationale:** These records have zero domain dependencies, are already Jackson-deserializable, and have compact constructor validation. Wrapping them would double the type count with no semantic gain.
**Trade-offs:** If a config record later gains a non-YAML-friendly field, we'd need to introduce a spec then. Low risk — these types are stable.
**Sources:** OptimiserConfig.java, SafetyConfig.java, FewShotExample.java
**Exploration:** quick
**Status:** captured

## D2: PromptSignatureSpec uses String for Class<?> fields

**Choice:** PromptSignatureSpec mirrors PromptSignature but replaces `Class<?> inputType`/`outputType` with `@Nullable String inputType`/`outputType` (fully-qualified class names).
**Alternatives:**
- Drop inputType/outputType entirely — loses type safety metadata that the batch orchestrator uses
- Use a generic type reference object — over-engineering for a string class name
**Rationale:** Class<?> cannot be expressed in YAML. String class names are the standard approach (used by Jackson @JsonSubTypes, Quarkus config, etc.). Resolution via Class.forName() at compile time provides fail-fast validation.
**Trade-offs:** No compile-time type checking between signature and actual I/O types. This is acceptable — the batch orchestrator validates at runtime.
**Sources:** PromptSignature.java (Class<?> inputType, outputType)
**Exploration:** quick
**Status:** captured

## D3: No PromptVariantSpec — examples are the declarative input

**Choice:** PromptVariant is not directly expressible in YAML. The YAML declares `examples` (List<FewShotExample>) and the compiler constructs a baseline PromptVariant from them.
**Alternatives:**
- Full PromptVariantSpec with all 8 fields — most fields are lifecycle state (createdAt, consecutiveWins, parentVariantId) that shouldn't be hand-declared
- Partial PromptVariantSpec with only declarative fields — still creates a type that exists solely to bridge to PromptVariant with defaults
**Rationale:** PromptVariant is the output of optimisation cycles. What a consumer declares is curated examples and optionally an instruction delta. The compiled output constructs the initial variant from these inputs.
**Trade-offs:** Cannot pre-seed a variant with a specific quality score or win count from YAML. Acceptable — these are runtime-computed values.
**Sources:** PromptVariant.java (8 fields, lifecycle-heavy)
**Exploration:** quick
**Status:** captured

## D4: All 8 capabilities in agentic-yaml, no new modules

**Choice:** All spec records, registries, and the compiler go in agentic-yaml. No new Maven modules.
**Alternatives:**
- New prompt-yaml module — creates module proliferation; agentic-yaml already depends on blocks (which has all prompt types) and neocortex-memory-api (provided)
- Spec records in agentic-yaml, registries in blocks — splits related code across modules for no dependency benefit
**Rationale:** agentic-yaml already has every dependency needed. The per-domain root type pattern (PatternSpec, WorldDefinition, CognitionDefinition) naturally extends with PromptOptimisationDefinition. Zero new dependencies.
**Trade-offs:** agentic-yaml grows slightly. Acceptable — it's the YAML surface module by design.
**Sources:** agentic-yaml/pom.xml (neocortex-memory-api already provided), AgenticYamlProcessor.java (per-domain @BuildStep pattern)
**Exploration:** quick
**Status:** captured

## D5: ConfidenceScorer in prompt-optimisation.yaml

**Choice:** ConfidenceScorerSpec goes under the prompt-optimisation.yaml root type as an optional field on PromptOptimisationPipelineSpec.
**Alternatives:**
- Separate memory.yaml root type — correct domain placement but creates infrastructure for one spec record
- cognition.yaml — memory hygiene is cognitive-adjacent but ConfidenceScorer isn't cognition
- Defer to §21 — violates "no deferral" principle
**Rationale:** Same pattern, same module, same deps. Coverage matrix groups it under §17. Creating a separate root type for one named registry is over-engineering.
**Trade-offs:** A memory type lives under a prompt config file. Cosmetic — the YAML field name (`confidenceScorer`) is self-describing.
**Sources:** Coverage matrix §17, ConfidenceScorer.java, ArousalScorer.java, SurpriseScorer.java, CompositeConfidenceScorer.java
**Exploration:** quick
**Status:** captured

## D6: Per-domain root type with new @BuildStep

**Choice:** Follow the existing pattern — PromptOptimisationDefinition root record, META-INF/prompt-optimisation.yaml discovery path, new @BuildStep in AgenticYamlProcessor, PromptOptimisationCompiler created by AgenticRecorder.
**Alternatives:**
- Embed in existing PatternSpec — prompt optimisation is per-capability, not per-pattern
- Single monolithic YAML — would break the clean per-domain separation
**Rationale:** Matches WorldDefinition/CognitionDefinition pattern exactly. Each domain area gets its own file, BuildStep, and compiler. Clean extension point.
**Trade-offs:** One more @BuildStep method. Trivial.
**Sources:** AgenticYamlProcessor.java (@BuildStep methods), AgenticRecorder.java (compiler creation)
**Exploration:** quick
**Status:** captured

## D1: Scope — 5 config-shaped types only

**Choice:** Scope #252 to 5 config-shaped types: TrustRoutingPolicyKeys, CbrOutcomeWeights, CoordinationOutcomeWeights, DispositionProfile, RiskDecision. Drop 4 runtime data types from YAML surface scope.
**Alternatives:**
- All 9 types as listed in issue — would build empty scaffolding around runtime data carriers (AttestationContext, AttestationIntent, ClassificationContext, GateOutcome) that authors never configure
- 5 config types + attestation templates — adds attestation observer compilation, a separate complex feature
**Rationale:** YAML surface types must be declarations (author-time knowable, compilable, choice-bearing). The 4 runtime types fail all criteria — they're data carriers populated by the engine at runtime, not config an author writes.
**Trade-offs:** Coverage matrix won't reach 100% for §19/§20 — 4 types marked N/A. Attestation observer YAML is deferred.
**Sources:** AttestationContext.java, ClassificationContext.java, GateOutcome.java, AttestationIntent.java — field analysis; prior spec patterns (ExecutionBackendSpec, AffordanceSpec)
**Exploration:** quick
**Status:** captured

## D2: Spec categories for the 5 types

**Choice:** Three categories: (A) weight map specs for CbrOutcomeWeights/CoordinationOutcomeWeights — flat map declarations, no sealed interface; (B) adapted record spec for TrustRoutingPolicyKeys — YAML-friendly record, compiler maps to builder; (C) sealed interface spec for RiskDecision — Autonomous/GateRequired variants. DispositionProfile refined to direct reuse during spec writing — record is already Jackson-compatible.
**Alternatives:**
- Uniform sealed interface for all 5 — unnecessarily complex for flat maps and simple records
- Direct reuse for all — CbrOutcomeWeights/CoordinationOutcomeWeights SPIs aren't Jackson-annotated, TrustRoutingPolicyKeys is a builder not a record
**Rationale:** Matches established patterns from prior issues. Weight maps are simple config; adapted records handle type mismatches (builder → record, enum keys → strings); sealed interfaces handle variant types.
**Trade-offs:** Three patterns instead of one uniform approach. Justified by the types' structural differences.
**Sources:** ExecutionBackendSpec.java (sealed pattern), PromptSignatureSpec.java (adapted pattern), prior decisions.md files
**Exploration:** quick
**Status:** captured

## D3: CandidateSetStrategy — direct reuse

**Choice:** Reuse engine-api's CandidateSetStrategy directly inside RiskDecisionSpec. No spec mirror.
**Alternatives:**
- Spec mirror (CandidateSetStrategySpec) — more isolation from engine-api changes but duplicates an already Jackson-annotated type
**Rationale:** CandidateSetStrategy is already Jackson-typed with @JsonTypeInfo/@JsonSubTypes in engine-api. Creating a mirror duplicates working serialization for no benefit. agentic-yaml already depends on engine-api.
**Trade-offs:** Tight coupling to engine-api's CandidateSetStrategy schema. If engine-api changes the Jackson annotations, the YAML surface breaks. Acceptable because agentic-yaml already compiles against engine-api.
**Depends on:** D2 (RiskDecision is a sealed interface spec)
**Sources:** CandidateSetStrategy.java (engine-api), RiskDecision.java (engine-api), GE-20260607-285229 (engine API breaking changes)
**Exploration:** quick
**Status:** captured

## D4: Weight map key handling and spec independence

**Choice:** Two independent spec records (CbrOutcomeWeightsSpec, CoordinationOutcomeWeightsSpec). CbrOutcomeWeightsSpec uses RoutingOutcome enum constant names as keys, validated at compile time. CoordinationOutcomeWeightsSpec uses free-form string keys.
**Alternatives:**
- Shared OutcomeWeightsSpec with key-type discriminator — adds complexity for no reuse benefit since key types and validation differ
- Free-form string keys for both — loses compile-time validation for CbrOutcomeWeights where the enum is known
**Rationale:** Different key types (enum vs string) mean different validation. Compile-time enum validation catches typos in YAML. Two small independent records are clearer than one parameterised type.
**Trade-offs:** Two spec classes instead of one. Trivial cost — each is a one-field record.
**Depends on:** D2 (weight map category)
**Sources:** CbrOutcomeWeights.java (RoutingOutcome enum keys), CoordinationOutcomeWeights.java (String keys), DefaultCbrOutcomeWeights.java, DefaultCoordinationOutcomeWeights.java
**Exploration:** quick
**Status:** captured

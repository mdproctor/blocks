# YAML Coverage Matrix

Living document tracking which blocks capabilities are YAML-expressible
and their implementation status. Update as gaps close.

**Goal:** Build meaningful AI applications in just YAML.

**Last audit:** 2026-09-09 (exhaustive — all 13 Maven modules scanned)

---

## Status Key

| Status | Meaning |
|--------|---------|
| Done | Spec record + registry + compiler/tests in agentic-yaml or summarisation-yaml |
| Spec only | Spec record exists, no compiler |
| Gap | YAML-expressible but not yet implemented |
| Partial | Some fields need Java (functional interfaces) |
| Code-only | Inherently procedural — Java required |
| N/A | Output type, utility, or runtime infrastructure |

---

## Module: agentic-yaml (#165)

### 1. Pattern Orchestration

| Capability | Types | Status | Notes |
|-----------|-------|--------|-------|
| Supervisor | `PatternSpec.Supervisor` | Done | Default: FirstMatch routing, MaxIter(10) |
| Debate | `PatternSpec.Debate` | Done | judge + maxRounds |
| Loop | `PatternSpec.Loop` | Done | maxIterations + exitCondition (expression) |
| Parallel | `PatternSpec.Parallel` | Done | Default: SelectAll, CollectAll |
| Voting | `PatternSpec.Voting` | Done | Default: SelectAll, MajorityVote |
| Conditional | `PatternSpec.Conditional` | Done | branches with condition expressions |
| Sequence | `PatternSpec.Sequence` | Done | Default: Sequential, AgentCount |
| HTN | `PatternSpec.Htn` | Done | rootTask with recursive TaskNodeSpec tree |

### 2. Strategy Families

| Family | Subtypes | Status |
|--------|----------|--------|
| Routing | FirstMatch, RoundRobin, Sequential, LlmSelected, SelectAll | Done |
| Termination | MaxIterations, GoalReached, JudgeConvergence, AllAgreed, Supervisor, Contested, Convergence, SinglePass, AgentCount | Done |
| Aggregation | PassThrough, CollectAll, MajorityVote, Auction | Done |
| Activation | OnDispatch, MaxIterationsGuard | Done |
| Decomposition | Identity, Static, Llm, Hybrid, Heuristic, Goap, CapabilityDependency, ForwardReasoning | Done |
| Failure policy | FailurePolicy (reused directly) | Done |
| Agent refs | Worker, Channel, Human, External, Composed | Done |

### 3. Judgment

| Area | Subtypes | Status |
|------|----------|--------|
| Triggers | AlwaysYield, NeverYield, IterationBased, ConfidenceThreshold | Done |
| Caller strategies | Single, FanOut, EscalationChain | Done |
| Agreement | Unanimous, Majority, Threshold | Done |

### 4. Conversation

| Capability | Status | Notes |
|-----------|--------|-------|
| Turn policies (RoundRobin, Addressed, PointAddressed, Free) | Done | |
| Epistemic rules (ExplicitAcknowledgement, TacitAcceptance, CommitmentResolution) | Done | |
| Convergence policies (Structural, CommonGroundRatio, Composite) | Done | |
| Conversation compiler (ConversationOrchestrator wiring) | Gap | Needs AgentParticipant spec + default PromptAssembler |
| Agent participants (name, role, systemPrompt) | Gap | Key type for conversation YAML |
| Conversation protocol config (sentinel, entry types) | Gap | ConversationProjection config |
| Progress renderer types (percentage, count, step) | Gap | DefaultProgressRenderer named types |

### 5. Negotiation

| Capability | Status | Notes |
|-----------|--------|-------|
| Acceptance policies (Unanimous, Majority, Threshold) | Done | |
| NegotiationSpec (parties, acceptance, termination) | Spec only | No compiler |
| AcceptedTermination | Gap | Terminal on AGREED |
| TerminalOutcomeTermination | Gap | Terminal on AGREED/DEADLOCKED/WITHDRAWN |
| DeadlineTermination (timeout) | Gap | `timeout: PT30M` |
| NegotiationProjection (parties + acceptance) | Gap | Fully declarative, no compiler |

### 6. Normative Conflict Resolution

| Subtypes | Status |
|----------|--------|
| Priority, Specificity, Recency, MostRestrictive, Escalation | Done |

### 7. Expression Compilation (#242)

| Capability | Status | Issue |
|-----------|--------|-------|
| Type-safe MVEL per expression site | Gap | #242 |
| Guard → RoutingCandidate context | Gap | #242 |
| GoalReached → Map context | Gap | #242 |
| ConfidenceThreshold → JudgmentContext | Gap | #242 |

### 8. Schema & Infrastructure

| Capability | Status | Issue |
|-----------|--------|-------|
| Schema drift test with committed baseline | Gap | #243 |
| AgentDescriptor wiring from spec fields | Gap | #244 |
| Registry extensibility | Gap | #245 |

---

## Module: summarisation-yaml (#233)

### 9. Summarisation Pipeline

| Capability | Status | Notes |
|-----------|--------|-------|
| PipelineDefinition (name, source, levels) | Done | Full YAML surface |
| LevelDefinition (name, grouping, summariser, emit) | Done | |
| SourceDefinition (type, cloudEventType) | Done | |
| GroupingDefinition.Windowed (age, count) | Done | |
| GroupingDefinition.Keyed (keyExpr, completionExpr, staleTimeout) | Gap | Parses in YAML but `PipelineCompiler` throws UnsupportedOperationException |
| PipelineCompiler (definition → CompiledPipeline) | Done | |
| PipelineValidator | Done | |
| SummariserRegistry (type:name → factory) | Done | |

### 10. Built-in Summariser Types

| Type name | Status | Notes |
|-----------|--------|-------|
| `threshold-classify` | Done | Per-event MVEL predicate rules → category |
| `phase-detect` | Done | Stateful state machine |
| `count` | Done | Event counting |
| `field-extract` | Gap | Class exists but NOT registered in SummarisationRecorder |
| `pass-through` | Done | Registered in SummariserRegistry constructor |
| `verbatim` | Gap | VerbatimContentSummariser exists in summarisation-api, not registered |

### 11. Summarisation Extensions (blocks module)

| Capability | Status | Notes |
|-----------|--------|-------|
| SummaryMode (APPEND / EDIT) | Gap | Enum — YAML config for LlmContentSummariser |
| TieredContentSummariser thresholds | Gap | smallThreshold, mediumThreshold are YAML; delegates are code |
| LlmContentSummariser (preamble, mode) | Partial | preamble + mode YAML; AgentProvider CDI |
| ContentSummariser.asSummariser() bridge | Done | Built into summarisation-api |

---

## Module: cloudevents

### 12. CloudEvent Bridge

| Capability | Status | Notes |
|-----------|--------|-------|
| CloudEventIngestionAdapter config (typePrefix, tenancyId extension) | Gap | Config fields are YAML-expressible |
| CloudEventEmitter config (cloudEventType) | Gap | Type name is YAML; serialiser is code |
| PipelineTickScheduler (tickInterval) | Gap | `tickInterval: PT1S` — pure config |
| EventSink | Code-only | @FunctionalInterface |

---

## Module: summarisation-api

### 13. Core Summarisation Types

| Capability | Status | Notes |
|-----------|--------|-------|
| WindowPolicy (maxAge, maxCount) | Done | Compiled from GroupingDefinition |
| EventLevel (name, ordinal) | Done | Used in LevelDefinition |
| Summariser, StatefulSummariser, ContentSummariser | Code-only | @FunctionalInterface — computation logic |
| SummarisationRunner | Done | Wired by PipelineCompiler |
| KeyedSummarisationRunner | Gap | Keyed variant — needs KeyedGrouping compiler support |
| EventStreamBus, EventAccumulator, KeyedAccumulator | Code-only | Runtime infrastructure |
| Compactor | Code-only | @FunctionalInterface |

---

## Module: blocks (main)

### 14. Social Cognition Configs

| Config | What it controls | Status |
|--------|-----------------|--------|
| `DriveConfig` | Intrinsic motivation axis weights, thresholds, modulation | Gap |
| `MoodConfig` | PAD emotional baseline, decay, influence | Gap |
| `PersonalityEvolutionConfig` | Trait drift rate, dampening, L2 ceiling | Gap |
| `UserModelConfig` | Per-user profile signals, cooldown, decay, stage tiers | Gap |
| `StrategyLearningConfig` | Interaction strategy adaptation rates | Gap |
| `MentalModelConfig` | Theory of Mind confidence, projection | Gap |
| `NarrativeConfig` | Episode/theme capacity, salience, reflection cap | Gap |
| `NarrativeSynthesisGate` | Synthesis trigger: count + novelty + quiet period | Gap |
| `GoalProposalConfig` | Autonomous goal thresholds, capacity, cooldown | Gap |
| `GoalEscalationConfig` | Priority escalation weights and cycles | Gap |
| `NormDetectionConfig` | Social norm emergence thresholds, min agents | Gap |
| `CollectiveGoalConfig` | Group goal alignment threshold, cooldown | Gap |
| `RetentionConfig` | Memory eviction scoring weights | Gap |

### 15. Affordance / World Model

| Capability | Status | Notes |
|-----------|--------|-------|
| ObservableEntity (id, name, description, affordances) | Gap | Things an agent can see |
| Affordance (actionType, label, requiredItem, acceptsItems) | Gap | Actions on entities |
| ActionDescriptor (type, description, parameterFormat) | Gap | Action vocabulary |
| ObservationSection (sealed: EntityGroup / TextBlock / ItemList) | Gap | World structure |
| AnnotatedSection (requiredTags, resolution alternatives) | Gap | Capability-gated sections |
| ObservationPipeline (ordered filter chain) | Gap | Perception pipeline |
| PerceptionFilter (agentTags for visibility gating) | Gap | Named filter type |
| TieredObservationRenderer (tier thresholds) | Partial | Thresholds YAML; renderer + key extractor code |

### 16. Channel Infrastructure

| Capability | Status | Notes |
|-----------|--------|-------|
| ChannelBinding (channelId, semantic) | Gap | Channel identity |
| ChannelExecutionStrategy (Conversation / FanIn / Barrier) | Gap | Sealed — pure config |
| AgentParticipant (name, role, systemPrompt) | Gap | Participant declaration |
| ConversationProtocol config (sentinel, entry types) | Gap | Message protocol |

### 17. Prompt Optimisation

| Capability | Status | Notes |
|-----------|--------|-------|
| OptimiserConfig (maxExamples, qualityThreshold, minOutcomes) | Gap | |
| SafetyConfig (qualityFloor, circuitBreaker, maxExperiments) | Gap | |
| PromptOptimiser types: few-shot / instruction | Gap | Named registry types |
| DiversityStrategy types: top-n / outcome-aware(weight) | Gap | Named registry types |
| FewShotExample (input, output, outcome, qualityScore) | Gap | |
| PromptVariant (examples, instructionDelta, qualityScore) | Gap | |
| PromptSignature (id, description, baseSystemPrompt) | Gap | Has Class<?> fields — needs string type refs |
| ConfidenceScorer types: arousal / surprise / composite | Gap | Named registry types |

### 18. Execution Infrastructure

| Capability | Status | Notes |
|-----------|--------|-------|
| ExecutionBackend: reactive / choreographed | Gap | Named type |
| EventConcurrencyPolicy: serialize / coalesce / coalesce-by-source | Gap | Named type |
| Listener types: event-log / ledger / metrics | Gap | Named types |
| CoalitionEvaluator: capability-coverage | Gap | Named type |
| JointIntention (intentionId, plan, parties) | Gap | Pure data lifecycle |
| ReconsiderationSignal (reason enum, detail) | Gap | |

### 19. Trust & Routing Config

| Capability | Status | Notes |
|-----------|--------|-------|
| TrustRoutingPolicyKeys (threshold, minObservations, blendFactor) | Gap | |
| CbrOutcomeWeights (outcome → weight map) | Gap | |
| CoordinationOutcomeWeights (outcome → weight map) | Gap | |
| DispositionProfile (desired traits + axis weights) | Gap | Personality matching for routing |
| AttestationContext (tenancyId, caseId, capabilityTag) | Gap | |
| AttestationIntent (full attestation payload) | Gap | |

### 20. Oversight

| Capability | Status | Notes |
|-----------|--------|-------|
| RiskDecision (Autonomous / GateRequired) | Gap | Sealed — pure config |
| ClassificationContext (workerId, caseId, tenancyId) | Gap | Pure data |
| GateOutcome (Autonomous / GatePending) | Gap | Sealed — pure data |

---

## Module: engine-adapter

### 21. Engine Integration

| Capability | Status | Notes |
|-----------|--------|-------|
| PatternJudgmentConfig (prompt, callerConfig, verifierStrategy, mode) | Gap | Full judgment config for engine patterns |
| EngineHostedBackend | Gap | Named backend: `type: engine-hosted` |
| CheckpointingListener | Gap | Named listener: `type: checkpointing` |
| LlmEvaluationVerifier | Gap | Named verifier: `type: llm-evaluation` |
| SchemaValidationVerifier | Gap | Named verifier: `type: schema-validation` |

---

## Module: speech-api

### 22. Speech Pipeline Config

| Capability | Status | Notes |
|-----------|--------|-------|
| TranscriptionOptions (audioFormat, languageHint, modelSize, vocabularyHint) | Gap | STT config |
| SynthesisOptions (voice, language, audioFormat, includePhonemes) | Gap | TTS config |
| CleanupConfig (maxDestructiveness + filter list) | Partial | Threshold YAML; TextFilter list CDI |
| CorrectionStrategy (NONE / BASIC / AGGRESSIVE) | Gap | Enum |
| ConversationTurn (role, content) | Gap | History entry |
| AssembledPrompt (systemPrompt, userPrompt, model override) | Gap | |
| PromptContext (agentId, tenantId, subjectId) | Gap | |

---

## Module: speech-ws

### 23. Avatar WebSocket Config

| Capability | Status | Notes |
|-----------|--------|-------|
| AvatarConfig (sampleRate, maxDestructiveness, systemPrompt, agentId, tenantId, proactiveTickInterval) | Gap | Full avatar session config |
| VisemeMapping (IPA → viseme mapping with weights) | Gap | Lip-sync config table |

---

## Module: speech-sherpa

### 24. Speech Model Configs

| Capability | Status | Notes |
|-----------|--------|-------|
| SherpaConfig (modelDir, numThreads, provider) | Gap | STT model config |
| KokoroConfig (modelDir, voiceId, lengthScale, numThreads) | Gap | Kokoro TTS config |
| Audio8Config (modelDir, variant, numThreads, temperature, topP, topK) | Gap | DualAR TTS config |
| GectorConfig (modelPath, maxIterations, keepConfidence, minErrorProb) | Gap | Grammar correction config |

---

## Module: annotations

### 25. Pattern Annotations (peer pathway to YAML)

| Annotation | YAML equivalent | Status |
|-----------|----------------|--------|
| `@Supervisor`, `@Debate`, `@Loop`, `@Parallel`, `@Voting`, `@Conditional`, `@Sequence`, `@Htn` | PatternSpec topologies | Done (YAML side) |
| `@Agent`, `@Debater`, `@Judge`, `@Voter` | AgentRefSpec roles | Done (YAML side) |
| `@Attestation`, `@OversightGate`, `@TrustRouted`, `@CbrRouted` | Governance specs | Gap |

---

## Summary

| Module | Section | Total capabilities | Done | Spec only | Gap | Partial | Code-only |
|--------|---------|-------------------|------|-----------|-----|---------|-----------|
| agentic-yaml | Patterns (1-3) | 41 | **41** | — | — | — | — |
| agentic-yaml | Conversation (4) | 7 | 3 | — | **4** | — | — |
| agentic-yaml | Negotiation (5) | 6 | 1 | 1 | **4** | — | — |
| agentic-yaml | Normative (6) | 5 | **5** | — | — | — | — |
| agentic-yaml | Expression/infra (7-8) | 7 | — | — | **7** | — | — |
| summarisation-yaml | Pipeline (9-10) | 13 | **10** | — | **3** | — | — |
| summarisation-yaml | Extensions (11) | 4 | 1 | — | **2** | 1 | — |
| cloudevents | Bridge (12) | 4 | — | — | **3** | — | 1 |
| summarisation-api | Core (13) | 9 | **3** | — | **1** | — | 5 |
| blocks | Social configs (14) | 13 | — | — | **13** | — | — |
| blocks | Affordance (15) | 8 | — | — | **7** | 1 | — |
| blocks | Channel (16) | 4 | — | — | **4** | — | — |
| blocks | Prompt optim (17) | 8 | — | — | **8** | — | — |
| blocks | Execution (18) | 6 | — | — | **6** | — | — |
| blocks | Trust/routing (19) | 6 | — | — | **6** | — | — |
| blocks | Oversight (20) | 3 | — | — | **3** | — | — |
| engine-adapter | Engine (21) | 5 | — | — | **5** | — | — |
| speech-api | Speech (22) | 7 | — | — | **6** | 1 | — |
| speech-ws | Avatar (23) | 2 | — | — | **2** | — | — |
| speech-sherpa | Models (24) | 4 | — | — | **4** | — | — |
| annotations | Governance (25) | 3 | — | — | **3** | — | — |
| **Total** | | **165** | **64** | **1** | **91** | **3** | **6** |

**Coverage: 64/165 (39%).** The pattern orchestration layer is complete.
The application configuration layer — social cognition, affordances, channels,
prompt tuning, speech, summarisation extensions — is 91 capabilities wide open.

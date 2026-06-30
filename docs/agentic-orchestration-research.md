# Agentic Orchestration Research

Research notes for casehub's compositional agentic orchestration model.
Design spec: `docs/superpowers/specs/2026-06-30-agentic-orchestration-design.md`.
DSL conventions: `casehubio/parent docs/DSL-STYLE-GUIDE.md`.

## Context

CaseHub needs composable building blocks for agent orchestration that leverage the
full platform (engine, qhorus, work, ledger, eidos) and accommodate all known
execution models — without baking in assumptions about which model is being built.

LangChain4j provides a `Planner` interface that all patterns implement. CaseHub's
approach is more powerful: decompose execution models into five independent concerns,
each an SPI, and let execution models emerge as compositions of those concerns.

### Where Blocks Sits

Blocks is the composition layer for patterns and primitives that don't belong in
engine, qhorus, work, ledger, or platform individually. It exists because there are
cross-cutting concerns that span multiple foundation modules. Blocks can also
incubate primitives that later push down to the module that should own them.

### Dependency Direction

Blocks depends on engine-api, qhorus-api, work-api. Foundation modules do not
depend on blocks. Application repos (drafthouse, quarkmind, etc.) depend on blocks.

---

## Execution Model Catalogue

Epic: [casehubio/engine#595](https://github.com/casehubio/engine/issues/595)

### Already Implemented or Native

| Model | Status | Where |
|-------|--------|-------|
| Event-driven reactive | Native | Engine binding model + ContextChangeTrigger |
| Blackboard | Native | Engine architecture; #445 adds Drools integration |
| Sequential | engine#484 | SequenceWorker |
| Supervisor | engine#101 | LLM-driven planning |
| Contract Net | engine#103 | Capability bidding |
| P2P / Mesh | engine#107 | Qhorus peer-to-peer |
| Goal decomposition / Planning | engine#110, #208 | PlanExecutor, PlanSource SPI |

### New (Child Issues of #595)

| Model | Issue | Description |
|-------|-------|-------------|
| Loop / Iterative Refinement | engine#596 | Repeated execution until convergence |
| Parallel / Fan-out | engine#597 | Concurrent agents with aggregation |
| Conditional Routing | engine#598 | Predicate-based path selection |
| GOAP | engine#599 | Dependency graph routing to goal state |
| HTN | engine#600 | Hierarchical task decomposition |
| Voting / Ensemble | engine#601 | Parallel evaluation + voting strategy |
| Debate / Adversarial | engine#602 | Multi-round argumentation with convergence |
| Market / Auction | engine#603 | Bid-based task allocation |
| Stigmergy | engine#604 | Indirect coordination via shared environment |
| Swarm / Emergent | engine#605 | Self-organising agents via local rules |
| Hierarchical Delegation | engine#606 | Org-chart structure with result synthesis |

---

## LangChain4j Pattern Reference

Source: langchain4j/langchain4j repo, Mario Fusco's work. As of June 2026.

### Shipped (1.17.0, June 26 2026)

| Pattern | Planner Class | Topology | Key Abstraction |
|---------|---------------|----------|-----------------|
| Sequential | `SequentialPlanner` | SEQUENCE | Fixed ordered pipeline |
| Loop | `LoopPlanner` | SEQUENCE | Iterative refinement with exit condition |
| Parallel | `ParallelPlanner` | PARALLEL | Concurrent + output function aggregation |
| Parallel Mapper | via `parallelMapperBuilder()` | PARALLEL | Single agent fanned out over collection |
| Conditional | `ConditionalPlanner` | CONDITIONAL | Predicate-based agent routing |
| Supervisor | `SupervisorPlanner` | STAR | LLM picks next agent; response/context strategies |
| GOAP | `GoalOrientedPlanner` | SEQUENCE | `GoalOrientedSearchGraph` — dependency graph, shortest path |
| Blackboard | `BlackboardPlanner` | STAR | `AgentActivator` — agents fire on input readiness |
| Voting | `VotingPlanner` | PARALLEL | `VotingStrategy` — majority, weighted |
| Debate | `DebatePlanner` | STAR | 2+ debaters + judge, `ConvergenceStrategy` |
| P2P | `P2PPlanner` | STAR | `AgentActivator` + reactive `onStateChanged` |

### In PR (Not Yet Shipped)

| Pattern | PR | Key Abstraction |
|---------|-----|-----------------|
| HTN | #5584 | `TaskNode` (sealed: Primitive/Compound), `DecompositionMethod`, `HtnPlanner` — SHOP-style forward planning |
| AgentRegistry | #5551 | Dynamic agent discovery |

### Core Interfaces

**`Planner`** — foundation of all patterns:
- `nextAction(PlanningContext)` → `Action` — the only required method
- `firstAction(PlanningContext)` → `Action` — optional initial action
- `terminated()` → boolean
- `executionState()` / `restoreExecutionState(Map)` — crash recovery
- `topology()` → SEQUENCE | PARALLEL | STAR | CONDITIONAL

**`Action`** — sealed return type:
- `call(AgentInstance...)` — invoke agents
- `noOp()` — wait/skip
- `done()` / `done(result)` — terminate

**`AgenticScope`** — shared key-value state:
- Agents write via `outputKey`; read via argument name matching
- Tracks invocation sequence and responses
- Serializable for crash recovery

### HTN Detail

- `TaskNode` — sealed: `PrimitiveTask` (agent ref + optional precondition/effect) or `CompoundTask` (name + decomposition methods)
- `DecompositionMethod` — guard (`Predicate<AgenticScope>`) + strategy
- `DecompositionStrategy` — `(AgenticScope, Map<Class<?>, AgentInstance>) → List<TaskNode>`
- `LlmDecompositionStrategy` — LLM selects agents; supports recursive decomposition via `maxDepth`
- SHOP-style forward planning: effects applied during traversal, downstream guards see updated state

### GOAP vs HTN

| Aspect | GOAP | HTN |
|--------|------|-----|
| Direction | Backward (goal → start via dependency graph) | Forward (start → goal via tree decomposition) |
| Structure | Flat action sequence via graph shortest-path | Hierarchical task tree with explicit decomposition |
| Planning | Pre-compute full path from current state to goal | Expand tree incrementally, one action at a time |
| Best for | Dependency-driven pipelines | Workflows with natural hierarchy and conditional branching |

---

## Broader Execution Models (Beyond LangChain4j)

Models not in langchain4j but relevant to CaseHub:

| Model | How It Works | CaseHub Mapping |
|-------|-------------|----------------|
| Market / Auction | Tasks auctioned; agents bid on capability, cost, load | Qhorus COLLECT for bid aggregation, extends AgentRoutingStrategy |
| Stigmergy | Indirect coordination via shared environment changes | CaseContext as shared environment; extends choreography |
| Swarm / Emergent | Self-organisation via local rules, no predefined structure | Qhorus instance registry + peer channels; hardest to audit |
| Hierarchical Delegation | Org-chart: executive → manager → specialist | Cases + sub-cases; stage nesting |
| Event-driven Reactive | Agents subscribe to event streams, activate on triggers | Native engine binding model |

---

## Five Compositional Concerns

Every execution model decomposes into decisions on five independent concerns.
This is CaseHub's architectural departure from langchain4j's single `Planner`
interface — the concerns are the primitives, not the coordinator pattern.

| Concern | Question It Answers | Examples |
|---------|-------------------|----------|
| **Routing** | Which agent(s) handle this task? | First-match, LLM-selected, bid/auction, capability-matched, round-robin, trust-weighted |
| **Decomposition** | How is a task broken into subtasks? | Flat sequence, HTN tree, GOAP dependency graph, LLM-driven, none (primitive) |
| **Activation** | When does an agent fire? | On explicit dispatch, on state change, on event, on schedule, on input readiness |
| **Aggregation** | How are multiple results combined? | Collect-all (barrier), majority vote, scored, convergence/judge, first-to-complete, weighted merge |
| **Termination** | When is the work done? | Goal reached, convergence detected, max iterations, budget exhausted, all subtasks complete |

### Execution Models as Compositions

| Execution Model | Routing | Decomposition | Activation | Aggregation | Termination |
|----------------|---------|---------------|------------|-------------|-------------|
| Sequential | Ordered list | Flat sequence | On predecessor complete | Pass-through | Last step done |
| Loop | Same agent | None (repeat) | On iteration complete | Overwrite | Exit condition |
| Parallel | All eligible | None | Simultaneous | Collect-all / barrier | All complete |
| Conditional | Predicate-selected | None | On dispatch | Pass-through | Selected path done |
| Supervisor | LLM-selected | None (one at a time) | On result | Pass-through | LLM says done |
| GOAP | Dependency-computed | Dependency graph | On predecessor complete | Pass-through | Goal keys present |
| HTN | Decomposition-driven | Hierarchical tree | On parent decomposed | Pass-through | All primitives done |
| Blackboard | Input-ready | None | On state change | Merge to blackboard | Goal condition |
| Voting | All (same task) | None | Simultaneous | Majority / weighted | All votes in |
| Debate | Round-robin | None | On round complete | Convergence check | Judge decides |
| P2P | Neighbour-driven | None | On dependency satisfied | Merge | All stable |
| Contract Net | Bid-winning | None | On award | Pass-through | Task complete |
| Market / Auction | Bid-evaluated | None | On award | Pass-through | Task complete |
| Stigmergy | Self-selected | None | On env change | Merge to env | Emergent |
| Swarm | Local-rule | None | On neighbour change | Emergent | Emergent |
| Hierarchical | Tier-delegated | Responsibility tree | On delegation | Upward synthesis | Executive satisfied |

---

## ContextBridge<T> — Typed Context Protocol

Engine epic: [casehubio/engine#203](https://github.com/casehubio/engine/issues/203)

The `ContextBridge<T>` SPI makes the context passed between agents a user-domain
class. Workers declare their bridge; the engine applies it at scheduling time.
This follows the approach taken by Serverless Workflow and Quarkus Flow.

```java
interface ContextBridge<T> {
    T initialise(Object enclosingContext, Map<String, Object> inputMapping,
                 PropagationContext propagation);
    void onWrite(String key, Object value, Object enclosingContext);
    void complete(T context, Object enclosingContext,
                  Map<String, Object> outputMapping,
                  PropagationContext propagation);
}
```

### Built-in Bridges (Planned)

| Bridge | Context Type | Use Case |
|--------|-------------|----------|
| `PlainMapBridge` | `Map<String, Object>` | Plain lambda workers |
| `AgenticScopeBridge` | LangChain4j `AgenticScope` | LangChain4j interop (engine#419) |
| `WorkingMemoryBridge` | Drools `WorkingMemory` | Drools rule evaluation (engine#446) |
| `SubCaseBridge` | CaseContext (child) | Sub-case spawning |
| `WorkflowContextBridge` | Flow context | Quarkus Flow workers |

### Status

`ContextBridge<T>` is designed (#203) but not yet implemented — being prioritised
for immediate delivery. Cases currently use `CaseContext` directly. The blocks SPIs
are designed generic over `<T>` from the start so no retrofit is needed when
`ContextBridge<T>` lands.

### Implication for Blocks SPIs

The five compositional concern SPIs are **generic over the context type `<T>`**,
not hardcoded to CaseContext or a StateView wrapper. This allows:

- LangChain4j users to pass `AgenticScope` and keep type safety
- Drools users to pass `WorkingMemory` for rule-based activation
- Domain engineers to use typed records (`@CaseFile`) as context
- All of these to compose with the same routing, decomposition, activation,
  aggregation, and termination SPIs

### Related Issues

- engine#419 — CaseContextProvider SPI for AgenticScope interop
- engine#446 — WorkingMemoryBridge for Drools
- engine#203 — ContextBridge protocol epic
- engine#201 — Adaptive execution architecture (parent)

---

## Design Approach

**Chosen approach:** compositional primitives (five concern SPIs) with pre-composed
pattern façades (supervisor, debate, voting, etc.) on top.

Full rationale and SPI definitions in the design spec. This section captures the
decision context that led there.

### Why Not LangChain4j's Model

LangChain4j's `Planner` interface is clean for JVM-local, synchronous,
single-coordinator orchestration. But it bakes in the orchestration assumption.
CaseHub's compositional approach says the *concerns* are the primitives, not the
coordinator pattern. This is architecturally more powerful because:

1. Reactive/emergent models (stigmergy, swarm, blackboard) don't fit "what's next?"
2. CaseHub's choreography (event-driven, binding-based) is a first-class execution
   model, not a special case of orchestration
3. New execution models don't require new interfaces — just new compositions
4. The typed context bridge (`ContextBridge<T>`) composes naturally with generic SPIs
5. Eidos provides four-layer agent identity (slot, capability, disposition, health)
   that enriches routing far beyond langchain4j's name+description model

### Layering

| Level | What Lives Here | Why |
|-------|----------------|-----|
| **engine-api** | Core primitives that belong alongside Goal, Stage, PlanItem | Pure orchestration vocabulary |
| **engine** | Pattern implementations needing runtime (EventLog, CaseContext versioning, persistence) | Runtime dependencies |
| **blocks** | Cross-cutting compositions spanning engine + qhorus + work + eidos | The composition layer |
| **application** | Domain wiring — hook methods, vocabularies, agent definitions | Like DebateChannelProjection today |

Primitives incubate in blocks, then push down to the owning foundation module
once the interface stabilises.

---

## Quarkus Flow Considerations

Quarkus Flow (CNCF Serverless Workflow) provides step-based orchestration,
fan-out, conditional routing, and looping natively. Before building a custom
implementation of any execution model, evaluate whether Quarkus Flow is the
right backbone.

Custom implementation is justified when:
- The pattern is too primitive for Flow's overhead (like WorkItem child spawning)
- The pattern doesn't map to a workflow graph (stigmergy, swarm)
- The pattern needs deep integration with CaseContext/EventLog/Stage that Flow doesn't provide
- The pattern is reactive rather than step-based

Each execution model issue (#596–#606) includes a Quarkus Flow evaluation section.

---

## SPI Definitions

Full SPI definitions with interfaces, sealed types, context carriers, and
planned implementations are in the design spec:
`docs/superpowers/specs/2026-06-30-agentic-orchestration-design.md`

**Summary of the five SPIs:**

| SPI | Generic Signature | Platform Mapping |
|-----|------------------|-----------------|
| `RoutingStrategy<T>` | `route(RoutingContext<T>) → RoutingDecision` | Generalises `AgentRoutingStrategy` |
| `DecompositionStrategy<T>` | `decompose(TaskNode, DecompositionContext<T>) → List<TaskNode>` | New — extends plan model |
| `ActivationRule<T>` | `shouldActivate(ActivationContext<T>) → boolean` | Generalises `ContextChangeTrigger` |
| `AggregationStrategy<T, R>` | `aggregate(List<AgentResult>, AggregationContext<T>) → AggregationResult<R>` | Maps to qhorus COLLECT/BARRIER |
| `TerminationCondition<T>` | `evaluate(TerminationContext<T>) → TerminationDecision` | Generalises `Goal` + `GoalExpression` |

All SPIs are generic over `<T>` to align with `ContextBridge<T>`.
`RoutingContext<T>` carries `List<AgentCandidate>` with eidos `AgentDescriptor`
per agent for personality-aware, capability-health-aware routing.

---

## Composition DSL

Two-level API following the DSL Style Guide (`parent/docs/DSL-STYLE-GUIDE.md`):
pre-composed pattern builders for the 80% case, compositional builders for custom.
Full DSL examples in the design spec; key examples retained here for quick reference.

### Pre-Composed Pattern Builders (80% Case)

Each named pattern provides defaults for all five concerns. Override only
what differs from the default.

```java
// Supervisor — defaults: routing=llmSelected, activation=onResult,
//   decomposition=none, aggregation=passThrough, termination=llmDecides
supervisor(chatModel)
    .agents(reviewer, implementor, arbitrator)
    .terminate(goalReached(".review.complete"))
    .build()

// Debate — defaults: routing=roundRobin, activation=onRoundComplete,
//   aggregation=convergenceCheck, termination=judgeDecides
debate()
    .debaters(critic, advocate)
    .judge(arbitrator)
    .maxRounds(5)
    .convergence(judgeDecides())
    .build()

// Voting — defaults: routing=allEvaluators, activation=simultaneous,
//   aggregation=majorityVote, termination=allVotesIn
voting()
    .evaluators(analyst1, analyst2, analyst3)
    .strategy(majorityVote())
    .build()

// HTN — defaults: routing=decompositionDriven, activation=onParentDecomposed,
//   aggregation=passThrough, termination=allSubtasksComplete
htn()
    .task(compound("analyse",
        method(when(".hasData"), sequence(extract, transform, load)),
        method(when(".needsCollection"), sequence(collect, validate, extract))
    ))
    .terminate(allSubtasksComplete())
    .build()

// Loop — defaults: routing=sameAgent, activation=onIterationComplete,
//   aggregation=overwrite, termination=exitCondition
loop()
    .agents(scorer, editor)
    .maxIterations(5)
    .exitCondition(ctx -> ctx.readState("score") >= 0.8)
    .build()
```

### Compositional Builders (20% Custom Case)

Two entry points matching the two execution drivers:

```java
// Orchestrated — imperative driver, coordinator decides next action
orchestration("custom-pipeline")
    .route(bidEvaluated(costWeighted()))
    .decompose(goapGraph(agentCapabilities))
    .activate(onPredecessorComplete())
    .aggregate(collectAll())
    .terminate(goalReached(".pipeline.done"))
    .build()

// Choreographed — reactive driver, agents fire on state changes
choreography("reactive-mesh")
    .route(capabilityMatched())
    .activate(onStateChange(".data.*"))
    .aggregate(mergeToContext())
    .terminate(emergentStability(5))
    .build()
```

### Static Factory Imports

Each concern has its own factory class for static import:

```java
import static io.casehub.blocks.agentic.Routing.*;
import static io.casehub.blocks.agentic.Decomposition.*;
import static io.casehub.blocks.agentic.Activation.*;
import static io.casehub.blocks.agentic.Aggregation.*;
import static io.casehub.blocks.agentic.Termination.*;
```

### Expression Overloads (CaseHub Convention)

Following the platform-wide three-way overload convention:

```java
.terminate(goalReached(".done == true"))                // JQ string
.terminate(goalReached(ctx -> ctx.isComplete()))         // typed predicate
.terminate(goalReached(evaluator))                       // evaluator instance

.activate(onStateChange(".data.ready == true"))          // JQ string
.activate(onStateChange(ctx -> ctx.dataReady()))         // typed predicate
```

### Composability

Any pattern can be a component in another — following LangChain4j's model:

```java
var reviewLoop = loop()
    .agents(reviewer, editor)
    .exitCondition(ctx -> ctx.readState("score") >= 0.8)
    .build();

var pipeline = sequence()
    .agents(drafter, reviewLoop, publisher)
    .build();
```

---

## Platform Integration (Agreed)

### Polymorphic AgentRef

An agent in the DSL abstracts over all platform execution surfaces. The
`AgentRef<T>` sealed type resolves to the appropriate runtime mechanism.

```java
sealed interface AgentRef<T> {
    record WorkerAgent<T>(Worker worker) implements AgentRef<T> {}
    record ChannelAgent<T>(UUID channelId, ChannelAgentHandler handler) implements AgentRef<T> {}
    record HumanAgent<T>(WorkItemCreateRequest template) implements AgentRef<T> {}
    record ExternalAgent<T>(Function<T, CompletionStage<AgentResult>> fn) implements AgentRef<T> {}
}
```

**Execution surfaces:**

| Platform Surface | AgentRef Variant | How Agents Run |
|-----------------|-----------------|----------------|
| Engine Worker | `WorkerAgent` | `WorkerProvisioner` dispatches; JVM-local, Docker, remote |
| Qhorus Channel | `ChannelAgent` | Message posted to channel, response via projection |
| Work WorkItem | `HumanAgent` | Human task created, claimed, completed |
| External | `ExternalAgent` | Arbitrary async function (LangChain4j, REST, etc.) |

**Factory methods for ergonomics:**

```java
supervisor(chatModel)
    .agents(
        worker(reviewerWorker),
        worker(implementorWorker),
        human(WorkItemCreateRequest.builder()
            .title("Arbitration needed")
            .candidateGroups("senior-reviewers")
            .build())
    )
    .build()
```

This is where blocks' cross-cutting nature shows — an execution model can
mix engine workers, channel agents, and human tasks in the same composition.

### State Flow via ContextBridge<T>

The execution driver holds the `ContextBridge<T>`. Agent invocation cycle:

1. Bridge provides typed context `T` to the routing strategy
2. Routing selects agent(s)
3. Bridge serialises relevant state for agent input
4. Agent executes (via platform surface)
5. Agent result returns
6. Bridge applies result to typed context
7. Termination condition evaluates against updated context
8. Loop (orchestration) or wait for next event (choreography)

The SPIs never touch raw CaseContext or raw AgenticScope — the bridge is the
single integration point.

### Event Flow for Choreography

The choreography driver subscribes to state changes via platform event sources:

| AgentRef Variant | Event Source | Mechanism |
|-----------------|-------------|-----------|
| `WorkerAgent` | Engine EventBus | `ContextChangeTrigger`, binding events |
| `ChannelAgent` | Qhorus channel | `ChannelProjection` message observation |
| `HumanAgent` | Work lifecycle | WorkItem status events (claimed, completed) |
| `ExternalAgent` | Callback | `CompletionStage` completion |

The choreography driver registers `ActivationRule<T>` instances against the
appropriate event source based on `AgentRef` type.

---

## Eidos — Agent Identity and Capability Model

Eidos provides structured agent identity that no peer framework offers.
LangChain4j has agent name + description string. CaseHub has a four-layer
identity model with vocabulary-backed personality traits and learned
performance history.

### AgentDescriptor — Four Layers

| Layer | What It Contains | Vocabulary |
|-------|-----------------|------------|
| **Identity** | Who the agent is (name, tenancy, model) | — |
| **Slot** | What role it fills | `CasehubSlotTerm` |
| **Capabilities** | What it can do — inputTypes, outputTypes, qualityHint, latencyHintP50Ms, costHint, epistemicDomains | Per-capability metadata |
| **Disposition** | Behavioural traits / personality | Belbin (9 team roles), DISC (4 types), Thomas-Kilmann (5 conflict modes), Conscientiousness, SVO |

### Supporting Infrastructure

| SPI | What It Does |
|-----|-------------|
| `AgentRegistry` | Discover agents by slot or capability; blocking + reactive |
| `CapabilityHealth` | Probe readiness: Ready, Degraded, Unavailable, EpistemicallyWeak |
| `VocabularyRegistry` | Cross-vocabulary equivalence (DISC ↔ Conscientiousness ↔ TK) |
| `SystemPromptRenderer` | Generate system prompts from descriptors (MARKDOWN, PROSE, A2A_CARD) |
| `AgentGraphStore` | Record task history and outcomes per agent |
| `CapabilitySpecializationStore` | Learned DECLINE/FAIL patterns for proactive routing exclusion |

### Impact on Blocks SPIs

**RoutingContext<T>** should carry `AgentDescriptor` per available agent.
Routing strategies can then use:

- **Slot vocabulary** — role-appropriate selection (Coordinator vs Specialist vs Evaluator)
- **Disposition axes** — personality-informed selection:
  - Belbin: Plant for creative work, Completer-Finisher for review
  - DISC: Dominant for decisive routing, Conscientious for analytical tasks
  - Thomas-Kilmann: Collaborator for debate, Competitor for adversarial review
- **Capability health** — filter agents that are Degraded or EpistemicallyWeak
- **Epistemic domains** — subject-matter confidence matching
- **Historical performance** — learned DECLINE/FAIL patterns from ledger

**AgentRef<T>** should optionally carry an `AgentDescriptor`. Worker-based
agents get their descriptor from `Worker.agentDescriptor()`. Channel and human
agents declare descriptors at composition time.

### Platform Advantage Over LangChain4j

| Aspect | CaseHub (Eidos) | LangChain4j |
|--------|-----------------|-------------|
| Agent identity | 4-layer structured descriptor | Name + description string |
| Personality model | Belbin, DISC, Thomas-Kilmann vocabularies | None |
| Capability discovery | Registry with slot/capability queries | Static agent list |
| Health probing | Ready/Degraded/Unavailable/EpistemicallyWeak | None |
| Learned routing | DECLINE/FAIL pattern aggregation via ledger | None |
| System prompt | Generated from descriptor, format-specific | Manual string |
| Cross-vocab | Equivalence mapping across personality frameworks | None |

---

## Open Design Questions

1. How do orchestration/choreography drivers compose — can an orchestrated
   Supervisor contain a choreographed Blackboard sub-step?
2. Which SPIs push down to engine-api vs stay in blocks permanently?
3. How does `TaskNode` relate to engine-api's `PlanElement` marker — should
   `PrimitiveTask` implement `PlanElement`?
4. How does the DSL integrate with Quarkus Flow — can a `function()` step in
   FuncDSL invoke a blocks execution model?
5. How should disposition-aware routing compose with other routing strategies
   — is it a decorator, a filter, or a scoring factor?

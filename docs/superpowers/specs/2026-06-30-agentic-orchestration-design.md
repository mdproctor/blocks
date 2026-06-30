# Agentic Orchestration — Compositional Execution Model Design

**Date:** 2026-06-30
**Repo:** casehubio/blocks
**Epic:** casehubio/engine#595 (Execution Capability Models)
**Research:** `docs/agentic-orchestration-research.md`
**DSL Guide:** `casehubio/parent docs/DSL-STYLE-GUIDE.md`

---

## Problem

CaseHub needs composable building blocks for agent orchestration that leverage
the full platform (engine, qhorus, work, ledger, eidos) and accommodate all
known execution models — without baking in assumptions about which model is
being built.

LangChain4j provides a `Planner` interface where all patterns implement
`nextAction()`. This works for JVM-local, synchronous, single-coordinator
orchestration but cannot express CaseHub's choreographic, distributed,
human-in-the-loop, personality-aware capabilities.

## Architectural Position

**Blocks is the composition layer** for patterns and primitives that don't
belong in engine, qhorus, work, ledger, or platform individually. It exists
because agentic orchestration is inherently cross-cutting — an execution model
may route via engine workers, communicate via qhorus channels, delegate to
human work items, select agents via eidos personality traits, and audit via
ledger.

**Dependency direction:** blocks depends on engine-api, qhorus-api, work-api.
Foundation modules do not depend on blocks. Application repos depend on blocks.

**Incubation:** new primitives may prototype in blocks, then push down to the
owning foundation module once the interface stabilises.

---

## Design: Five Compositional Concerns

Every execution model decomposes into decisions on five independent concerns.
Each concern is an SPI. Execution models are compositions of specific
implementations of these concerns.

| Concern | Question It Answers |
|---------|-------------------|
| **Routing** | Which agent(s) handle this task? |
| **Decomposition** | How is a task broken into subtasks? |
| **Activation** | When does an agent fire? |
| **Aggregation** | How are multiple results combined? |
| **Termination** | When is the work done? |

### Execution Models as Compositions

| Model | Routing | Decomposition | Activation | Aggregation | Termination |
|-------|---------|---------------|------------|-------------|-------------|
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

## SPI Definitions

All SPIs are generic over the context type `<T>`, aligning with engine's
`ContextBridge<T>` (engine#203). `ContextBridge<T>` is designed but not yet
implemented — engine#203 is OPEN. **Initial implementations bind `T` to
`CaseContext` concretely** and use `CaseContext` methods directly. The generic
`<T>` parameter is forward-looking: when `ContextBridge<T>` lands, SPIs work
unchanged with `AgenticScope`, `WorkingMemory`, or typed domain records
without retrofit. If `ContextBridge<T>` ships with a different shape than
assumed here, only the execution driver's bridge integration changes — the
SPI signatures are stable because they depend on `T` (the domain type), not
on the bridge protocol itself.

**All five SPIs return `Uni<>`** (Mutiny reactive). This is a uniform
design decision, not routing-specific. Any SPI implementation may involve
async operations: `LlmDecomposition` makes LLM calls, `ConvergenceCheck`
may invoke an LLM judge, `GoalReached` may evaluate JQ against I/O-backed
CaseContext. Synchronous implementations wrap results with
`Uni.createFrom().item()` — a one-liner and standard Quarkus idiom.
Uniform reactivity avoids a future breaking retrofit when the first
non-routing SPI needs async, and composes cleanly in the driver's reactive
pipeline.

### RoutingStrategy<T>

```java
interface RoutingStrategy<T> {
    Uni<RoutingDecision> route(RoutingContext<T> context);
}
```

Returns `Uni<>` (Mutiny reactive) — consistent with engine's
`AgentRoutingStrategy` which returns `Uni<AgentAssignment>`. Routing may
involve async operations: LLM calls (`LlmSelectedRouting`), embedding
lookups (`SemanticAgentRoutingStrategy` delegation), external registry
queries, or trust score computation. Implementations that do only in-memory
work return `Uni.createFrom().item(result)`. Mutiny is acceptable in Tier 1
per the module-tier-structure protocol.

`RoutingContext<T>` carries: task to route, available agents as
`List<RoutingCandidate>` (each with `AgentRef` + `AgentDescriptor`), current
typed state `T`, propagation context.

`RoutingDecision` — sealed:
- `Selected(List<AgentRef> agents)`
- `Unresolvable(String reason)`
- `Escalate(String reason)`

Generalises engine's existing `AgentRoutingStrategy` (Assigned/Unresolvable/
EscalateToOversight) with typed context and eidos-aware agent candidates.
For `WorkerAgent` routing, implementations may delegate to the engine's
`AgentRoutingStrategy` (including `TrustWeightedAgentStrategy`) by bridging
`RoutingCandidate` to engine's `AgentCandidate`. This preserves the
four-phase trust maturity model (PLATFORM.md) — blocks does not reimplement
trust scoring; it delegates to the engine's trust-qualified candidate pool.

**Implementations:**
`FirstMatchRouting`, `LlmSelectedRouting`, `RoundRobinRouting`,
`BidEvaluatedRouting`, `CapabilityMatchedRouting`, `DependencyComputedRouting`,
`DispositionAwareRouting` (Belbin/DISC/TK personality matching).

### DecompositionStrategy<T>

```java
interface DecompositionStrategy<T> {
    Uni<List<TaskNode>> decompose(TaskNode compound, DecompositionContext<T> context);
}
```

`DecompositionContext<T>` carries: current typed state `T`, available agents
with descriptors, decomposition depth.

`TaskNode` — sealed:
- `PrimitiveTask(AgentRef agent, Predicate<T> precondition, Consumer<T> effect)`
- `CompoundTask(String name, List<DecompositionMethod<T>> methods)`

**Planning-time vs execution-time effects:** The `Consumer<T> effect` on
`PrimitiveTask` is a **planning-time simulation effect** used by GOAP forward
search. During planning, the strategy operates on a snapshot of `T` (via
`CaseContext.snapshot()` or equivalent clone). Effects are applied to the
snapshot to simulate state progression; if the plan is discarded, the snapshot
is discarded with it. After actual agent execution, state mutation happens
via `ContextBridge` applying the real `AgentResult` — the `Consumer<T>` is
not re-applied. For context types without cheap snapshot semantics, GOAP
planning is not available; HTN (which doesn't apply effects during planning)
is the alternative.

`DecompositionMethod<T>`: `Predicate<T> guard` + `DecompositionStrategy<T> strategy`.

**Implementations:**
`StaticDecomposition`, `GoalOrientedDecomposition` (GOAP graph),
`LlmDecomposition`, `HierarchicalDecomposition` (responsibility tiers),
`IdentityDecomposition` (pass-through for primitive tasks).

### ActivationRule<T>

```java
interface ActivationRule<T> {
    Uni<Boolean> shouldActivate(ActivationContext<T> context);
}
```

`ActivationContext<T>` carries:
- Event or state change that triggered evaluation
- Current typed state `T`
- Agent being evaluated
- History of prior activations
- `Optional<AggregationResult> lastAggregationResult` — enables debate-style
  activation where the next round depends on convergence state
- `int consecutiveIdleActivations` — enables emergent stability detection
  (Stigmergy/Swarm) by tracking activation-less cycles

Generalises engine's binding trigger conditions (`ContextChangeTrigger`).

**Implementations:**
`OnExplicitDispatch`, `OnStateChange`, `OnInputReady`,
`OnPredecessorComplete`, `OnRoundComplete`, `OnSchedule`.

### AggregationStrategy<T>

```java
interface AggregationStrategy<T> {
    Uni<AggregationResult> aggregate(List<AgentResult> results,
                                     AggregationContext<T> context);
}
```

`AggregationResult` — sealed:
- `Resolved(Object value)`
- `Partial(Object collected, int remaining)`
- `Deadlocked(String reason)`

The second type parameter `R` is dropped. Specific strategies document their
return type (`CollectAll` returns `List<AgentResult>`, `MajorityVote` returns
a single `AgentResult`), but the SPI uses `Object` uniformly. This avoids
cascading generic complexity — without `R`, the execution driver and
termination condition don't need to be generic over the aggregation result
type. Consumers cast `Resolved.value()` — consistent with `CaseContext.get()`
returning `Object`.

Maps to qhorus COLLECT/BARRIER semantics for collection; the strategy (how to
combine) is the new part.

**Implementations:**
`CollectAll`, `MajorityVote`, `WeightedVote`, `ScoredAggregation`,
`ConvergenceCheck`, `FirstToComplete`, `UpwardSynthesis`, `PassThrough`.

### TerminationCondition<T>

```java
interface TerminationCondition<T> {
    Uni<TerminationDecision> evaluate(TerminationContext<T> context);
}
```

`TerminationContext<T>` carries: current typed state `T`, iteration count,
elapsed time, results so far, budget remaining.

`TerminationDecision` — sealed:
- `Continue`
- `Complete(Object result)`
- `Failed(String reason)`
- `Escalate(String reason)`

Generalises engine's `Goal` with `GoalKind` and `GoalExpression`.

**Implementations:**
`GoalReached`, `MaxIterations`, `ConvergenceDetected`, `BudgetExhausted`,
`AllSubtasksComplete`, `EmergentStability`.

---

## Platform Integration

### Polymorphic AgentRef

An agent in the DSL abstracts over all platform execution surfaces.
`AgentRef` is **non-generic** — it identifies the execution target without
carrying the context type. The context type `<T>` is bound at the execution
driver level, which knows `T` and can enforce type safety when invoking
agents. This avoids a phantom type parameter on 4 of 5 variants (only
`ExternalAgent` would use `T` in its fields, and even there the driver
handles the cast).

```java
sealed interface AgentRef {
    record WorkerAgent(Worker worker) implements AgentRef {}
    record ChannelAgent(UUID channelId, ChannelAgentHandler handler)
        implements AgentRef {}
    record HumanAgent(WorkItemCreateRequest template) implements AgentRef {}
    record ExternalAgent(Function<Object, CompletionStage<AgentResult>> fn)
        implements AgentRef {}
    record ComposedAgent(ExecutionModel<?> model) implements AgentRef {}
}
```

Type-safe factory methods enforce `<T>` at the DSL level:

```java
static <T> ExternalAgent external(Function<T, CompletionStage<AgentResult>> fn) {
    @SuppressWarnings("unchecked")
    var erased = (Function<Object, CompletionStage<AgentResult>>) (Function<?, ?>) fn;
    return new ExternalAgent(erased);
}
```

| Platform Surface | AgentRef Variant | Runtime Mechanism |
|-----------------|-----------------|-------------------|
| Engine Worker | `WorkerAgent` | `WorkerProvisioner` — JVM, Docker, remote, human |
| Qhorus Channel | `ChannelAgent` | Message dispatch + projection observation |
| Work WorkItem | `HumanAgent` | WorkItem lifecycle — claim, complete, escalate |
| External | `ExternalAgent` | Arbitrary async function (LangChain4j, REST) |
| Composed | `ComposedAgent` | Nested execution — driver spawns a child driver |

`ComposedAgent` enables composability: any `ExecutionModel` can be used as a
component in another composition. When the driver encounters a `ComposedAgent`,
it creates a child execution driver for the nested model (analogous to
sub-case spawning in the engine). This is how `reviewLoop` can be passed to
`sequence().agents(drafter, reviewLoop, publisher)` — the builder wraps the
`ExecutionModel` in a `ComposedAgent`.

### Eidos-Enriched Routing Candidates

`RoutingContext<T>` carries agents as `List<RoutingCandidate>`:

```java
record RoutingCandidate(AgentRef ref, @Nullable AgentDescriptor descriptor) {}
```

`AgentDescriptor` is `@Nullable` — not all agents have descriptors:
- `ExternalAgent` (arbitrary lambda) has no natural identity metadata
- `ComposedAgent` (nested model) has no single personality
- Even `WorkerAgent` — engine's `AgentCandidate.agentDescriptor()` is
  nullable (the `Worker` record itself has no descriptor field; the
  descriptor is attached at the routing layer via `AgentCandidate`)

Routing strategies that need descriptors filter candidates; strategies that
don't care ignore the field. This follows the engine's established pattern.

Named `RoutingCandidate` (not `AgentCandidate`) to avoid collision with
engine's `io.casehub.api.spi.routing.AgentCandidate`, which carries
operational metadata (workerId, runningJobs, health). The blocks type pairs
an `AgentRef` (cross-platform execution target) with an `AgentDescriptor`
(identity and capability metadata) — a different concept from the engine's
worker-specific candidate.

Routing strategies access the full four-layer identity:
- **Slot** — role-appropriate selection (Coordinator vs Specialist)
- **Disposition** — personality matching (Belbin, DISC, Thomas-Kilmann)
- **Capability health** — filter Degraded or EpistemicallyWeak agents
- **Epistemic domains** — subject-matter confidence
- **Historical performance** — learned DECLINE/FAIL patterns via ledger

### State Flow via ContextBridge<T>

The execution driver holds the `ContextBridge<T>`. Invocation cycle:

1. Bridge provides typed context `T` to routing strategy
2. Routing selects agent(s) using descriptor-enriched candidates
3. Bridge serialises state for agent input
4. Agent executes via platform surface
5. Result returns
6. Bridge applies result to typed context
7. Termination evaluates against updated context
8. Loop (orchestration) or wait for next event (choreography)

### Event Flow for Choreography

| AgentRef Variant | Event Source | Mechanism |
|-----------------|-------------|-----------|
| `WorkerAgent` | Engine EventBus | `ContextChangeTrigger`, binding events |
| `ChannelAgent` | Qhorus channel | `ChannelProjection` message observation |
| `HumanAgent` | Work lifecycle | WorkItem status events |
| `ExternalAgent` | Callback | `CompletionStage` completion |

### AgentResult

The data contract between agent execution and aggregation:

```java
record AgentResult(
    AgentRef agent,
    Object output,
    Duration duration,
    AgentResultStatus status
) {
    enum AgentResultStatus { SUCCESS, FAILURE, TIMEOUT, DECLINED }
}
```

Result mapping by AgentRef variant:

| AgentRef Variant | Output Type | Source |
|-----------------|------------|--------|
| `WorkerAgent` | `WorkerResult` payload | `WorkerExecutionManager` completion |
| `ChannelAgent` | Response message content | `ChannelProjection` observation |
| `HumanAgent` | WorkItem completion data | WorkItem lifecycle event |
| `ExternalAgent` | Function return value | `CompletionStage` completion |
| `ComposedAgent` | Nested `ExecutionResult` | Child driver completion |

`DECLINED` status maps to engine's existing `WorkerOutcome.DECLINE` —
the agent was offered the task but refused (capability mismatch, load,
policy). Routing strategies learn from DECLINE patterns via
`CapabilitySpecializationStore`.

---

## Execution Drivers

The execution driver composes the five SPIs and runs the orchestration.
`ExecutionModel<T>` is the composition record; the driver executes it.

```java
record ExecutionModel<T>(
    RoutingStrategy<T> routing,
    DecompositionStrategy<T> decomposition,
    ActivationRule<T> activation,
    AggregationStrategy<T> aggregation,
    TerminationCondition<T> termination,
    Supplier<List<RoutingCandidate>> candidateSupplier,
    FailurePolicy failurePolicy,
    List<ExecutionEventListener> listeners
) {}
```

`candidateSupplier` replaces a fixed `List<RoutingCandidate>`. The driver
calls the supplier before each routing cycle to get the current candidate
pool. For static agent lists (the 80% case), DSL builders wrap via
`() -> List.of(a, b, c)`. For dynamic discovery (Blackboard, Stigmergy,
Swarm, long-running orchestrations), the supplier queries `AgentRegistry`:

```java
choreography("swarm")
    .candidatesFrom(agentRegistry, query)  // → Supplier wrapping registry.find(query)
    ...
```

`FailurePolicy` configures the driver's response to failures:

```java
record FailurePolicy(
    RoutingFailureAction onRoutingFailure,
    AggregationFailureAction onDeadlock,
    AgentRetryPolicy agentRetry
) {
    enum RoutingFailureAction { FAIL, RETRY_BROADER, ESCALATE }
    enum AggregationFailureAction { FAIL, ESCALATE, RETRY_DIFFERENT }
    record AgentRetryPolicy(int maxRetries, Duration backoff,
                            AgentFailureAction onExhausted) {}
    enum AgentFailureAction { FAIL, ESCALATE, SKIP }

    static FailurePolicy defaults() {
        return new FailurePolicy(FAIL, FAIL,
            new AgentRetryPolicy(3, Duration.ofSeconds(1), FAIL));
    }
}
```

DSL integration:
```java
supervisor(chatModel)
    .agents(...)
    .onRoutingFailure(ESCALATE)
    .onDeadlock(RETRY_DIFFERENT)
    .maxAgentRetries(5)
    .build()
```

### ExecutionDriver<T>

```java
interface ExecutionDriver<T> {
    Uni<ExecutionResult> execute(ExecutionModel<T> model, T initialContext);
    Uni<Void> cancel();
    ExecutionState state();
}
```

`ExecutionResult` — sealed:
- `Completed(Object result)` — termination returned `Complete`
- `Failed(String reason, Throwable cause)` — termination returned `Failed`
- `Escalated(String reason)` — routing or termination escalated
- `Cancelled()` — external cancellation

`ExecutionState` — sealed:
- `Idle`, `Running(int iteration)`, `WaitingForAgent(AgentRef agent)`,
  `WaitingForEvent`, `Completed`, `Failed`, `Cancelled`

### OrchestratedDriver<T> — Imperative Loop

The orchestrated driver coordinates agents actively in two phases:

**Initialisation phase** (runs once):
1. **Decompose** — if the model has a decomposition strategy, decompose the
   root task into subtasks. For HTN: expand the full task tree. For GOAP:
   compute the plan from current state to goal. For Supervisor: no
   decomposition — skip.

**Execution loop** (repeats until termination):
2. **Refresh candidates** — call `candidateSupplier.get()` for current pool
3. **Route** — select agent(s) via `RoutingStrategy` → `Selected(List<AgentRef>)`
4. **Activate** — evaluate `ActivationRule` for each selected agent
5. **Dispatch** — invoke activated agents. The driver handles single vs
   multi-agent dispatch based on the selected list:
   - **Single agent** (`Selected(List.of(a))`) — dispatch one agent, await
     result. Used by Sequential, Supervisor, GOAP, HTN.
   - **Multi-agent concurrent** (`Selected(List.of(a, b, c))`) — dispatch
     all activated agents concurrently via `Uni.join().all()`, collect all
     results (or timeout per `AgentRetryPolicy`). Used by Parallel, Voting,
     Debate. The driver publishes `onAgentDispatched` for each agent and
     collects results as they arrive.
6. **Aggregate** — pass all collected `AgentResult`s to `AggregationStrategy`.
   For single-agent dispatch, this is typically `PassThrough`. For
   multi-agent, this is `CollectAll`, `MajorityVote`, `ConvergenceCheck`, etc.
7. **Terminate** — evaluate `TerminationCondition` against updated context
8. If `Continue`, loop to step 2; otherwise return `ExecutionResult`

State is held in the driver instance. For durable execution (survives
restarts), the driver persists state to `CaseContext` — the versioned,
per-key concurrent store provides crash recovery. This delegates to the
engine's existing `EventLog` for `WorkerAgent` invocations.

### ChoreographedDriver<T> — Reactive Event-Driven

The choreographed driver registers event listeners and reacts:

1. **Register** — for each agent, subscribe to the appropriate event source
   based on `AgentRef` variant (see Event Flow table above)
2. **Wait** — the driver is idle until an event arrives
3. **Activate** — evaluate `ActivationRule` against the event
4. **Dispatch** — if activated, invoke the agent
5. **Aggregate** — combine with prior results
6. **Terminate** — evaluate termination condition
7. If `Continue`, return to waiting; otherwise return `ExecutionResult`

The choreographed driver does not control execution order — agents fire
when their activation conditions are met by external events.

### CaseInstance Integration

Both drivers integrate with `CaseInstance` when operating within a case
lifecycle. The driver accesses `CaseContext` via the case instance and
publishes state changes via the engine's `EventBus`. When operating
outside a case (standalone mode), the driver manages its own state
container.

---

## Failure Handling

### Routing Failures

| RoutingDecision | Driver Response |
|-----------------|----------------|
| `Unresolvable` | Configurable: `FAIL` (terminate with `Failed`), `RETRY_BROADER` (expand candidate pool — e.g., include Degraded agents), `ESCALATE` (create oversight WorkItem) |
| `Escalate` | Driver creates an oversight WorkItem via `OversightGateService`, suspends until resolved |

### Agent Execution Failures

| AgentRef Variant | Failure Source | Driver Response |
|-----------------|---------------|----------------|
| `WorkerAgent` | Platform-level failure | Delegate to `casehub-engine-resilience` — Dead Letter Queue, PoisonPill detection, backoff. Configurable retry count. |
| `ChannelAgent` | No response / parse failure | Timeout → `AgentResult(TIMEOUT)`. Parse failure → `AgentResult(FAILURE)` via `AgentResultParseException`. |
| `HumanAgent` | WorkItem expires | SLA escalation via casehub-work. Configurable: treat as `Failed` termination or re-route. |
| `ExternalAgent` | `CompletionStage` exception | Retry with backoff (configurable). After max retries → `AgentResult(FAILURE)`. |
| `ComposedAgent` | Child driver failure | Propagate child `ExecutionResult.Failed` as `AgentResult(FAILURE)`. |

### Aggregation Failures

| AggregationResult | Driver Response |
|-------------------|----------------|
| `Deadlocked` | Configurable: `FAIL`, `ESCALATE` (oversight), `RETRY_DIFFERENT` (re-route to different agents) |

### Choreography Stall Detection

The choreographed driver runs a watchdog timer (configurable, default 30
minutes). If no activation occurs within the watchdog period, the driver
evaluates the termination condition with a `STALLED` flag. The termination
condition can return `Failed("stalled")` or `Escalate("no progress")`.

---

## DSL Build Contract

All pattern builders and compositional builders return `ExecutionModel<T>`
from `.build()`:

```java
ExecutionModel<CaseContext> model = supervisor(chatModel)
    .agents(reviewer, implementor)
    .terminate(goalReached(".review.complete"))
    .build();  // → ExecutionModel<CaseContext>
```

The caller then passes the model to a driver:
```java
ExecutionDriver<CaseContext> driver = new OrchestratedDriver<>();
Uni<ExecutionResult> result = driver.execute(model, caseContext);
```

**Shorthand `.execute()`:** Pre-composed pattern builders provide a
convenience method that creates the appropriate default driver:
```java
// Equivalent to: new OrchestratedDriver<>().execute(supervisor(...).build(), ctx)
Uni<ExecutionResult> result = supervisor(chatModel)
    .agents(reviewer, implementor)
    .execute(caseContext);
```

Pre-composed patterns know their default driver: `supervisor()`,
`debate()`, `voting()`, `loop()`, `sequence()`, `parallel()`,
`conditional()`, `htn()` → `OrchestratedDriver`. `choreography()` builder
→ `ChoreographedDriver`. Compositional builders (`orchestration()`,
`choreography()`) also provide `.execute()`.

**Composability:** When `.build()` returns `ExecutionModel<T>`, it can be
passed to `.agents()` on another builder — the builder wraps it in
`ComposedAgent` automatically.

---

## Execution Event Observability

The execution driver publishes events for audit, compliance, and
operational monitoring via `ExecutionEventListener`:

```java
interface ExecutionEventListener {
    default void onRoutingDecision(RoutingDecision decision,
                                   List<RoutingCandidate> candidates) {}
    default void onActivation(AgentRef agent, boolean activated) {}
    default void onAgentDispatched(AgentRef agent) {}
    default void onAgentResult(AgentResult result) {}
    default void onAggregation(AggregationResult result) {}
    default void onTermination(TerminationDecision decision) {}
    default void onStateTransition(ExecutionState from, ExecutionState to) {}
    default void onFailure(AgentRef agent, Throwable cause) {}
}
```

All methods are `default` — implementations override only the events they
care about.

### Built-in Listeners

| Listener | Target | What It Captures |
|----------|--------|-----------------|
| `EventLogListener` | Engine EventLog | Routing decisions, agent results, terminations — extends engine's existing case event history |
| `LedgerExecutionListener` | casehub-ledger | Compliance-grade audit entries for routing decisions, escalations, failures — EU AI Act Art.12 automated decision logging |
| `MetricsListener` | OpenTelemetry | Execution duration, agent latency, routing distribution, failure rates |

### Configuration

Listeners are registered on `ExecutionModel`:
```java
supervisor(chatModel)
    .agents(...)
    .listener(new EventLogListener(eventLog))
    .listener(new LedgerExecutionListener(ledgerService))
    .build()
```

CDI-discovered listeners (via `@ApplicationScoped implements
ExecutionEventListener`) are automatically added by the driver when
operating within a Quarkus container.

### Audit-Critical Events

For regulated deployments (EU AI Act Art.12, GDPR Art.22):
- **Routing decisions** — which agent was selected, from which candidates,
  why others were excluded (trust score, capability mismatch, health)
- **Escalation events** — routing or termination triggered human oversight
- **Failure events** — agent failures, deadlocks, stall detection
- **Termination decisions** — why execution ended (goal, failure, budget)

These events carry sufficient detail for post-hoc decision auditing.

---

## Relationship to Existing Packages

### `io.casehub.blocks.channel`

The `channel` package provides low-level agent dispatch primitives:
`ChannelAgentHandler` (first-match sub-task routing), `ChannelAgentDispatcher`
(handler routing + invocation), `ContextTracker`, `BoundedProjectionDecorator`.

The new `agentic` package **uses** `ChannelAgentHandler` as the handler
within `ChannelAgent` — it is a dependency, not a replacement. The
`ChannelAgentDispatcher`'s dispatch logic is subsumed by the execution
driver's `ChannelAgent` dispatch path, which provides richer lifecycle
management (aggregation, termination, failure handling). Applications that
use `ChannelAgentDispatcher` directly for simple dispatch continue to do so;
the `agentic` package is for multi-agent orchestration that composes across
platform surfaces.

### `io.casehub.blocks.conversation`

The `conversation` package implements a specific structured debate protocol:
round-based multi-agent deliberation with point lifecycle, human escalation,
and sub-task dispatch. It is a **concrete execution model** — conceptually,
it is a composition of `RoundRobinRouting`, `OnRoundComplete` activation,
`ConvergenceCheck` aggregation, and `JudgeDecides` termination.

Post-implementation, the `conversation` package can be refactored as a
concrete composition using the five SPIs (the `debate()` pattern builder).
This refactoring is tracked as blocks#4 — the `conversation` package
continues to work as-is and is not broken by the new `agentic` package.

### Coexistence

The three packages coexist at different abstraction levels:
- `channel` — primitive dispatch utilities (used by `agentic` internally)
- `conversation` — concrete debate model (future refactoring candidate)
- `agentic` — compositional orchestration framework

---

## Concern Coupling and Driver Mediation

The five concerns are independently **replaceable** — any single concern's
implementation can be swapped without changing the others. They are not
fully **independent** in execution: some patterns have implementations that
share state across concerns. This coupling is mediated by the execution
driver, not by direct SPI-to-SPI references.

Known coupling patterns:

| Pattern | Coupled Concerns | Mediation |
|---------|-----------------|-----------|
| GOAP | Routing + Decomposition share dependency graph | Driver passes the same graph to both; `GoalOrientedDecomposition` produces the graph, `DependencyComputedRouting` consumes it via shared state in the driver |
| Debate | Activation needs prior AggregationResult | `ActivationContext<T>` carries `lastAggregationResult` — the driver populates this field after each aggregation cycle |
| Stigmergy/Swarm | Termination needs activation history | `TerminationContext<T>` already carries iteration count and results so far; `EmergentStability` evaluates stability from this history |

The `ActivationContext<T>` canonical field list (§ActivationRule) includes
`lastAggregationResult` and `consecutiveIdleActivations` specifically for
these coupling patterns — the driver populates them after each cycle.

---

## Engine Execution Model Relationship

Several execution models already have engine-level implementations:
Sequential (engine#484), Supervisor (engine#101), Contract Net (engine#103),
P2P/Mesh (engine#107), Blackboard (native architecture).

The blocks compositional framework **does not re-implement** these. Instead:

1. **Blocks provides the compositional SPI layer** — the five concerns as
   replaceable strategies
2. **Engine implementations become compositions** — when blocks matures,
   existing engine implementations (e.g., `SequenceWorker`) can be
   refactored as specific compositions of the five SPIs
3. **Blocks adds models the engine doesn't have** — Debate, Voting, GOAP,
   HTN, Stigmergy, Swarm are new to the platform
4. **The incubation path is bidirectional** — SPIs push down to engine-api
   when stable; engine implementations push up to blocks compositions when
   the framework proves capable

This follows PLATFORM.md's propagation rule: parallel implementations
are not left in place. The engine implementations are the starting point;
blocks compositions are the target architecture. Migration is incremental.

### Binding Model Integration

Blocks execution models do not produce `CaseDefinition` structures directly.
Instead, the execution driver translates to engine primitives where needed:

- `WorkerAgent` dispatch → driver invokes `WorkerExecutionManager` or
  creates an engine `Binding` internally (using the same trigger/target
  model the engine uses natively)
- `ChannelAgent` dispatch → driver uses qhorus `MessageService` directly
- `HumanAgent` dispatch → driver creates WorkItem via `WorkBroker`
- `ExternalAgent` dispatch → driver invokes the function directly

For `WorkerAgent`, the driver benefits from the engine's existing
infrastructure: `EventLog`, `CaseContext` versioning, stage gating,
resilience (DLQ, PoisonPill). For non-engine agents, the driver provides
its own lifecycle management. The DSL is the user-facing API; engine
bindings are an implementation detail of `WorkerAgent` dispatch.

---

## Dependency Requirements

Blocks' public API references types from modules currently available only
transitively through `casehub-engine-api`. These must be declared as
explicit compile dependencies to avoid fragile transitive chains:

| Type Used | Source Module | Current Status |
|-----------|-------------|---------------|
| `AgentDescriptor` | `casehub-eidos-api` | Transitive via engine-api |
| `Worker` | `casehub-worker-api` | Transitive via engine-api |
| Personality vocabularies | `casehub-eidos-vocab` | Transitive via engine-api |

These dependencies must be added explicitly to blocks' `pom.xml` as
compile-scope dependencies before implementation begins.

---

## Documentation Updates

The following docs must be updated as part of implementation:

- **ARC42STORIES.MD §5** — add `conversation` package (already missing) and
  new `agentic` package with building block table
- **Deep-dive doc** (`parent/docs/repos/casehub-blocks.md`) — add Agentic
  Orchestration as a fifth key abstraction alongside Oversight Gate,
  Structured Conversation, Channel Agent Dispatch, Context Tracking

---

## Composition DSL

Two-level API following the DSL Style Guide (`parent/docs/DSL-STYLE-GUIDE.md`).

### Pre-Composed Pattern Builders

Each named pattern provides defaults for all five concerns. Override any.

```java
supervisor(chatModel)
    .agents(reviewer, implementor, arbitrator)
    .terminate(goalReached(".review.complete"))
    .build()

debate()
    .debaters(critic, advocate)
    .judge(arbitrator)
    .maxRounds(5)
    .convergence(judgeDecides())
    .build()

voting()
    .evaluators(analyst1, analyst2, analyst3)
    .strategy(majorityVote())
    .build()

htn()
    .task(compound("analyse",
        method(when(".hasData"), sequence(extract, transform, load)),
        method(when(".needsCollection"), sequence(collect, validate, extract))
    ))
    .terminate(allSubtasksComplete())
    .build()

loop()
    .agents(scorer, editor)
    .maxIterations(5)
    .exitCondition(ctx -> ctx.readState("score") >= 0.8)
    .build()
```

### Compositional Builders

Two entry points matching the two execution drivers:

```java
orchestration("custom-pipeline")
    .route(bidEvaluated(costWeighted()))
    .decompose(goapGraph(agentCapabilities))
    .activate(onPredecessorComplete())
    .aggregate(collectAll())
    .terminate(goalReached(".pipeline.done"))
    .build()

choreography("reactive-mesh")
    .route(capabilityMatched())
    .activate(onStateChange(".data.*"))
    .aggregate(mergeToContext())
    .terminate(emergentStability(5))
    .build()
```

### Conventions

**Static factory imports for vocabulary:**
```java
import static io.casehub.blocks.agentic.Routing.*;
import static io.casehub.blocks.agentic.Activation.*;
import static io.casehub.blocks.agentic.Termination.*;
```

**Three-way expression overloads (CaseHub convention):**
```java
.terminate(goalReached(".done == true"))              // JQ string
.terminate(goalReached(ctx -> ctx.isComplete()))       // typed predicate
.terminate(goalReached(evaluator))                     // evaluator instance
```

**Composability — any pattern is a component:**
```java
var reviewLoop = loop()
    .agents(reviewer, editor)
    .exitCondition(ctx -> ctx.readState("score") >= 0.8)
    .build();

var pipeline = sequence()
    .agents(drafter, reviewLoop, publisher)
    .build();
```

**Mixed agent types in one composition:**
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

---

## Package Structure

```
io.casehub.blocks.agentic/
├── AgentRef.java                     # sealed: WorkerAgent, ChannelAgent, HumanAgent, ExternalAgent, ComposedAgent
├── RoutingCandidate.java             # AgentRef + @Nullable AgentDescriptor
├── AgentResult.java                  # result envelope with status, output, duration
├── FailurePolicy.java                # configurable failure responses
│
├── routing/
│   ├── RoutingStrategy.java          # SPI — returns Uni<RoutingDecision>
│   ├── RoutingContext.java
│   ├── RoutingDecision.java          # sealed
│   ├── Routing.java                  # static factories
│   └── [implementations]
│
├── decomposition/
│   ├── DecompositionStrategy.java    # SPI
│   ├── TaskNode.java                 # sealed: PrimitiveTask, CompoundTask
│   ├── DecompositionMethod.java
│   ├── Decomposition.java            # static factories
│   └── [implementations]
│
├── activation/
│   ├── ActivationRule.java           # SPI
│   ├── ActivationContext.java        # carries lastAggregationResult, consecutiveIdleActivations
│   ├── Activation.java               # static factories
│   └── [implementations]
│
├── aggregation/
│   ├── AggregationStrategy.java      # SPI — single type param <T>, no <R>
│   ├── AggregationResult.java        # sealed — Resolved(Object), Partial, Deadlocked
│   ├── Aggregation.java              # static factories
│   └── [implementations]
│
├── termination/
│   ├── TerminationCondition.java     # SPI
│   ├── TerminationDecision.java      # sealed
│   ├── Termination.java              # static factories
│   └── [implementations]
│
├── model/
│   ├── ExecutionModel.java           # composition record
│   ├── ExecutionDriver.java          # SPI — Uni<ExecutionResult> execute(...)
│   ├── ExecutionResult.java          # sealed: Completed, Failed, Escalated, Cancelled
│   ├── ExecutionState.java           # sealed: Idle, Running, WaitingForAgent, etc.
│   ├── OrchestratedDriver.java       # imperative loop driver
│   ├── ChoreographedDriver.java      # reactive event-driven driver
│   └── ExecutionEventListener.java   # audit/observability SPI
│
└── pattern/                           # pre-composed builders
    ├── Patterns.java                  # static entry: supervisor(), debate(), etc.
    ├── SupervisorBuilder.java
    ├── DebateBuilder.java
    ├── VotingBuilder.java
    ├── HtnBuilder.java
    ├── LoopBuilder.java
    ├── SequenceBuilder.java
    ├── ParallelBuilder.java
    └── ConditionalBuilder.java
```

## Module Placement

### Push-Down Candidates (incubate in blocks, push to engine-api when stable)

| Candidate | Target Module | When |
|-----------|--------------|------|
| `RoutingStrategy<T>` | engine-api | When it generalises `AgentRoutingStrategy` |
| `TerminationCondition<T>` | engine-api | When it generalises `Goal` |
| `TaskNode` | engine-api | When it aligns with `PlanElement` |
| `ActivationRule<T>` | engine-api | When it generalises trigger conditions |
| Sealed decision types | engine-api | Could move early — pure vocabulary |

### Permanent in Blocks

| What | Why |
|------|-----|
| `AgentRef` (all variants) | Cross-cutting — engine + qhorus + work |
| `RoutingCandidate` | Cross-cutting — AgentRef + eidos descriptor |
| `AgentResult` | Cross-cutting — result envelope spanning platform surfaces |
| `ExecutionModel` | Composition of all five SPIs |
| `ExecutionDriver` / `OrchestratedDriver` / `ChoreographedDriver` | Drivers span platform |
| Pre-composed pattern builders | Convenience on composition |
| `AggregationStrategy` | Inherently cross-cutting |

---

## Platform Advantages Over LangChain4j

| Capability | CaseHub | LangChain4j |
|-----------|---------|-------------|
| Durable state | CaseContext — versioned, per-key concurrency | Cognisphere — flat KV, JVM-local |
| Audit | EventLog + Ledger — compliance-grade | AgentMonitor — invocation tree |
| Distributed agents | WorkerProvisioner — JVM, Docker, remote, human | JVM-local; A2A for remote |
| Human participation | Work — first-class tasks, SLA, claim/escalate | Not in scope |
| Structured comms | Qhorus — typed channels, speech acts, commitments | Flat state map |
| Scope control | CMMN Stages — lifecycle-gated eligibility | All agents always eligible |
| Goal model | Typed Goals, GoalExpression, condition evaluation | Implicit — LLM decides |
| Agent identity | Eidos — 4-layer descriptor, personality vocabularies | Name + description |
| Capability health | Ready/Degraded/Unavailable/EpistemicallyWeak | None |
| Learned routing | DECLINE/FAIL aggregation via ledger | None |
| Oversight | OversightGateService, ActionRiskClassifier | None |
| Typed context | ContextBridge<T> — pluggable domain classes (engine#203, designed, pending) | Fixed AgenticScope |
| Execution models | 16+ via compositional SPIs | 11 via Planner interface |

---

## Quarkus Flow Considerations

Before building a custom implementation of any execution model, evaluate
whether Quarkus Flow is the right backbone. Custom is justified when:

- The pattern is too primitive for Flow's overhead (WorkItem child spawning)
- The pattern doesn't map to a workflow graph (stigmergy, swarm)
- The pattern needs deep CaseContext/EventLog/Stage integration
- The pattern is reactive rather than step-based

Each execution model issue (#596–#606) includes a Quarkus Flow evaluation.

### Integration Sketch

Blocks and Quarkus Flow interoperate in both directions:

**Flow → Blocks:** A Quarkus Flow `function()` step invokes a blocks
`ExecutionModel` via `ExternalAgent`. The flow step wraps the blocks
driver's `execute()` call in a worker function, passing flow context as
input and receiving `ExecutionResult` as output.

**Blocks → Flow:** A blocks `ExecutionModel` delegates to Flow via
`ExternalAgent` that calls `FlowWorkerExecutor.execute()`. The blocks
driver treats the flow as an opaque async operation.

**Full integration** (future): a `FlowAgent` variant of `AgentRef` that
natively bridges to `casehub-engine-flow`, sharing `CaseContext` and
`EventLog` without the `ExternalAgent` wrapping overhead. This is deferred
until the compositional framework proves stable.

Detailed per-model evaluations are in each issue (#596–#606).

---

## Related Issues

- engine#595 — Execution Capability Models epic
- engine#596–#606 — Individual execution model issues
- engine#203 — ContextBridge<T> protocol
- engine#101 — LLM supervisor mode
- engine#419 — CaseContextProvider for AgenticScope interop
- engine#446 — WorkingMemoryBridge for Drools
- engine#577 — Belbin-aware agent routing
- engine#505 — AgentRoutingStrategy
- parent DSL-STYLE-GUIDE.md — DSL conventions

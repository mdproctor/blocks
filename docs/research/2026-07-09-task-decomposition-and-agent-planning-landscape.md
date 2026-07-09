# Task Decomposition & Agent Planning — Research Landscape

**Date:** 2026-07-09
**Context:** CaseHub blocks #13 (LlmDecomposition) + broader engine planning architecture
**Scope:** Comprehensive survey of agent planning architectures, task decomposition approaches,
and how CaseHub's current design maps to the state of the art

---

## 1. Agent Planning Architectures

Six distinct architectural patterns have emerged for LLM agent planning.
Each makes different trade-offs between adaptability, cost, latency, and reliability.

### 1.1 Architecture Comparison

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    Agent Planning Architecture Spectrum                     │
│                                                                             │
│  Adaptive ◄──────────────────────────────────────────────► Efficient        │
│                                                                             │
│  ReAct        Reflexion     Plan-Execute    ReWOO      LLMCompiler          │
│  ┌──────┐     ┌──────┐     ┌──────────┐   ┌──────┐   ┌──────────┐         │
│  │Reason│     │Reason│     │  Plan    │   │Plan  │   │Plan DAG  │         │
│  │  ↓   │     │  ↓   │     │  (LLM)  │   │(LLM) │   │  (LLM)   │         │
│  │ Act  │     │ Act  │     │    ↓     │   │  ↓   │   │    ↓     │         │
│  │  ↓   │     │  ↓   │     │ Execute │   │Worker│   │ Parallel │         │
│  │Observe│    │Observe│    │ (cheap) │   │(tool)│   │ Execute  │         │
│  │  ↓   │     │  ↓   │     │    ↓     │   │  ↓   │   │    ↓     │         │
│  │(loop)│     │Reflect│    │ Replan? │   │Solver│   │  Joiner  │         │
│  └──────┘     │  ↓   │     └──────────┘   └──────┘   └──────────┘         │
│               │(loop)│                                                      │
│               └──────┘                                                      │
│                                                                             │
│  LLM calls:   LLM calls:   LLM calls:    LLM calls:  LLM calls:           │
│  N per step   N+reflect    2+N           2 total     1+join                │
│                                                                             │
│  Latency:     Latency:     Latency:      Latency:    Latency:              │
│  High         Very high    Medium        Low         Low                   │
│                                                                             │
│  Adaptability: Adaptability: Adaptability: Adaptability: Adaptability:     │
│  ★★★★★        ★★★★★         ★★★★☆         ★★☆☆☆        ★★★☆☆              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 1.2 Detailed Comparison

| Architecture | Planning | Execution | Re-planning | LLM Calls | Best For |
|-------------|----------|-----------|-------------|-----------|----------|
| **ReAct** | Per-step (interleaved) | Sequential | Implicit (next step adapts) | N per step | Open-ended exploration, dynamic tool use |
| **Reflexion** | Per-step + self-critique | Sequential + retry | Reflection-driven | N + reflect per attempt | Code gen, QA where correctness > cost |
| **Plan-and-Execute** | Upfront plan, then execute | Sequential (cheaper model) | Explicit replan on failure | 2 + N (plan + execute each step) | Structured multi-step tasks |
| **ReWOO** | Upfront plan with placeholders | Parallel tool calls | None (fragile) | 2 total (plan + solve) | Known parallelisable workflows |
| **LLMCompiler** | DAG with dependencies | Parallel (topological order) | Joiner evaluates + replans | 1 + join | Parallel tool calls, latency-sensitive |
| **Tree of Thoughts** | Branching search tree | Parallel exploration | Backtracking | Very high | Problems requiring backtracking |
| **LATS** | Monte Carlo tree search | Search + evaluate | Backpropagated feedback | Very high | High-stakes complex reasoning |

### 1.3 Key Insight: Planning and Execution Are Orthogonal

The research converges on a separation:
- **Planning** = deciding what to do (which agents, what tasks, what order)
- **Execution** = carrying out the plan (dispatching, monitoring, recovering)

The most effective systems cleanly separate these. CaseHub's architecture already does this:
`DecompositionStrategy` (planning) is separate from `ExecutionDriver` (execution).

---

## 2. Task Decomposition Approaches

### 2.1 Decomposition Taxonomy

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     Task Decomposition Taxonomy                             │
│                                                                             │
│  ┌─────────────────────┐  ┌──────────────────────┐  ┌───────────────────┐  │
│  │  Static / Rule-Based │  │   LLM-Driven          │  │  Hybrid            │  │
│  │                      │  │                        │  │                    │  │
│  │  HTN methods with    │  │  LLM generates plan    │  │  Symbolic planner  │  │
│  │  guard predicates    │  │  from goal + agents    │  │  + LLM fallback    │  │
│  │                      │  │                        │  │                    │  │
│  │  Output: Primitive   │  │  Output: Agent-task    │  │  Output: Mixed     │  │
│  │  tasks with pre/eff  │  │  pairs (description)   │  │                    │  │
│  │                      │  │                        │  │                    │  │
│  │  Pros:               │  │  Pros:                 │  │  Pros:             │  │
│  │  - Deterministic     │  │  - Handles novel goals │  │  - Best of both    │  │
│  │  - Sound plans       │  │  - No domain authoring │  │  - LLM reduces     │  │
│  │  - Fast              │  │  - Natural language I/O │  │    knowledge eng.  │  │
│  │                      │  │                        │  │                    │  │
│  │  Cons:               │  │  Cons:                 │  │  Cons:             │  │
│  │  - Brittle           │  │  - Hallucination risk  │  │  - Complex         │  │
│  │  - Domain authoring  │  │  - No soundness proof  │  │  - Mode switching  │  │
│  │  - Can't handle      │  │  - Expensive           │  │                    │  │
│  │    novel goals       │  │                        │  │                    │  │
│  │                      │  │                        │  │                    │  │
│  │  CaseHub:            │  │  CaseHub:              │  │  CaseHub:          │  │
│  │  StaticDecomposition │  │  LlmDecomposition      │  │  Future: ChatHTN-  │  │
│  │  IdentityDecomp      │  │  (this issue, #13)     │  │  style interleave  │  │
│  └─────────────────────┘  └──────────────────────┘  └───────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 Plan Structure Representations

The output of decomposition — the plan itself — can take several forms:

| Representation | Structure | Parallelism | Re-planning | Used By |
|---------------|-----------|-------------|-------------|---------|
| **Linear sequence** | `List<Task>` | None | Restart from failed step | ReAct, Plan-Execute, CaseHub (current) |
| **DAG** | Nodes + dependency edges | Yes (independent paths) | Patch failed node, replan subgraph | LLMCompiler, Graph Harness, GAP |
| **Tree** | Hierarchical parent-child | Sibling parallelism | Backtrack and re-explore | ToT, LATS, HTN |
| **Placeholders** | Sequence with `$N` refs | Yes (all at once) | None (fragile) | ReWOO |

CaseHub currently uses **linear sequence** (`DecompositionStrategy` returns `List<TaskNode<T>>`).
The research strongly favours **DAG** for production systems.

### 2.3 Task Node Data Models Across Frameworks

What metadata does each framework attach to a task node?

```
┌───────────────────────────────────────────────────────────────────────────────────┐
│                        Task Node Data Models Compared                             │
│                                                                                   │
│  Framework        │ Identity      │ Agent     │ Dependencies │ Contract  │ State  │
│  ─────────────────┼───────────────┼───────────┼──────────────┼───────────┼────────│
│  CaseHub HTN      │ (implicit)    │ AgentRef  │ Sequential   │ None      │ None   │
│  (PrimitiveTask)  │               │           │ (list order) │           │        │
│  ─────────────────┼───────────────┼───────────┼──────────────┼───────────┼────────│
│  Graph Harness    │ node ID       │ config    │ Edges with   │ Output    │ State  │
│                   │               │           │ join semantics│ contract │ machine│
│                   │               │           │ (all/any_of) │ (κ_v)    │ (Σ)    │
│  ─────────────────┼───────────────┼───────────┼──────────────┼───────────┼────────│
│  RSTD             │ subtask ID    │ LLM call  │ State mgr    │ Output   │ Valid/ │
│                   │ (state key)   │ (scoped)  │ keys         │ schema   │ invalid│
│  ─────────────────┼───────────────┼───────────┼──────────────┼───────────┼────────│
│  LLMCompiler      │ $N reference  │ tool call │ Explicit     │ None     │ Pending│
│                   │               │           │ dependency   │          │ /done  │
│                   │               │           │ list         │          │        │
│  ─────────────────┼───────────────┼───────────┼──────────────┼───────────┼────────│
│  AgentOrchestra   │ step ID       │ sub-agent │ Sequential   │ None     │ Status │
│                   │               │ (typed)   │ (plan tool)  │          │ enum   │
│  ─────────────────┼───────────────┼───────────┼──────────────┼───────────┼────────│
│  ChatHTN          │ task name     │ operator  │ HTN ordering │ Pre/eff  │ World  │
│                   │               │           │ constraints  │ (STRIPS) │ state  │
│  ─────────────────┼───────────────┼───────────┼──────────────┼───────────┼────────│
│  GAP              │ query node    │ tool call │ Dependency   │ None     │ None   │
│                   │               │           │ graph edges  │          │        │
└───────────────────────────────────────────────────────────────────────────────────┘
```

**Key observation:** CaseHub's `PrimitiveTask` is the most minimal — no identity/description,
no explicit dependencies, no output contract, no state tracking. Every other framework
carries at least a task identity/description and some form of dependency expression.

---

## 3. Key Frameworks — Deep Dives

### 3.1 Graph Harness (2026) — Scheduler-Theoretic

**Source:** [arxiv.org/html/2604.11378v1](https://arxiv.org/html/2604.11378v1)

Formalises agent execution as a scheduling problem. Key contributions:

- **Execution Plan** = `(id, version, V, E, σ, κ)` — versioned, immutable DAG
- **Join semantics** on edges: `all_of` (conjunction), `any_of` (disjunction) — structural
  alternatives rather than ad-hoc LLM decisions
- **Three-level recovery**: retry → local patch → full replan. Each escalation creates a
  new plan version (immutability preserves audit trail)
- **Context separation**: execution context vs diagnostic context per node — prevents
  failure history from corrupting downstream reasoning
- **Side-effect classification**: idempotent vs non-idempotent nodes for retry safety

**Relevance to CaseHub:** The plan versioning and recovery protocol map well onto
CaseHub's oversight gate model — a gate rejection is a "local patch" recovery that
the Graph Harness framework would handle structurally.

### 3.2 RSTD (2026) — Runtime-Structured Task Decomposition

**Source:** [arxiv.org/html/2605.15425v1](https://arxiv.org/html/2605.15425v1)

**Key result:** 51.7% retry cost reduction vs monolithic, 73.2% vs static decomposition.

Three-layer architecture:
1. **Decomposition Engine** — developer-authored control flow with runtime branching
2. **Judgment Operators** — typed LLM calls scoped to single reasoning tasks, each with
   an explicit output schema
3. **State Manager** — persists validated results keyed by subtask ID; downstream tasks
   access prior outputs explicitly

The critical mechanism: **validation-gated state transitions** — a failed subtask's output
is never written to state and never visible downstream. This prevents cascading reasoning
errors.

**Relevance to CaseHub:** The "judgment operator" concept maps to CaseHub's Worker model.
The output schema validation maps to what engine could enforce at the Binding level.

### 3.3 ChatHTN (2025) — Hybrid HTN + LLM

**Source:** [arxiv.org/html/2505.11814](https://arxiv.org/html/2505.11814)

Interleaves symbolic HTN planning with LLM decomposition:
- Symbolic planner runs first, applying methods with preconditions/effects
- When the planner **gets stuck** (no method matches), it calls the LLM
- LLM receives: task semantics, current world state, partial knowledge base
- LLM generates a decomposition into primitive tasks
- Symbolic planner resumes from LLM output

**Key insight:** LLM reduces domain knowledge engineering by 75% while maintaining
plan soundness (the symbolic planner validates LLM output).

**Relevance to CaseHub:** This is the most natural evolution of our current design.
`StaticDecomposition` is the symbolic planner; `LlmDecomposition` is the LLM fallback.
A `HybridDecomposition` could interleave them — try static first, fall back to LLM.

### 3.4 LLMCompiler (2024) — Parallel Function Calling

**Source:** [arxiv.org/abs/2312.04511](https://arxiv.org/abs/2312.04511)

Three components:
1. **Function Calling Planner** — LLM generates DAG with inter-task dependencies
2. **Task Fetching Unit** — topological scheduler, dispatches when deps satisfied
3. **Executor** — runs tasks in parallel

Performance: 1.8× latency speedup on HotpotQA. Production: 3–5× latency reduction,
40–70% cost savings. Now a first-class pattern in LangGraph.

**Relevance to CaseHub:** The Task Fetching Unit maps to what an enhanced
`ExecutionDriver` could do. Currently `OrchestratedDriver` executes sequentially;
a DAG-aware driver could parallelise independent branches.

### 3.5 AgentOrchestra (2026) — Hierarchical Multi-Agent

**Source:** [arxiv.org/html/2506.12508v1](https://arxiv.org/html/2506.12508v1)

Two-tier hierarchy: Planning Agent + specialised sub-agents. Results:
- SimpleQA: 95.3% (vs 93.9% best baseline)
- GAIA avg: 82.42% (vs 77.58% best baseline)

Key design: Planning Agent maintains "global perspective" and dynamic role allocation.
Sub-agents interact through a standard interface that "conceals domain-specific details."

**Relevance to CaseHub:** CaseHub's `RoutingStrategy` + `DecompositionStrategy` already
separate the "which agent" from "what work" concerns. AgentOrchestra validates this
separation — their performance gains come from the hierarchical coordination, not from
any novel task representation.

### 3.6 GAP (2025) — Graph-based Agent Planning

**Source:** [arxiv.org/html/2510.25320v1](https://arxiv.org/html/2510.25320v1)

Trains agent foundation models to decompose complex queries into dependency graphs
for parallel tool execution. Outperforms ReAct on multi-hop QA benchmarks.

**Relevance to CaseHub:** The dependency graph concept is the same as LLMCompiler's DAG.
GAP's contribution is training models specifically for graph-structured decomposition.

### 3.7 LLM-Generated HTN Heuristics (2026)

**Source:** [arxiv.org/html/2605.07707v1](https://arxiv.org/html/2605.07707v1)

Rather than using LLMs to plan directly, uses them to generate search heuristics
for a symbolic HTN planner:
- LLM generates Python heuristic functions
- Symbolic planner uses them to guide search
- Coverage: 131/139 problems (vs 134 for best baseline)
- Search efficiency: 83% of problems solved with fewer node expansions
- Soundness guaranteed by planner, not LLM

**Relevance to CaseHub:** An alternative to `LlmDecomposition` — instead of having
the LLM produce the plan, have it produce heuristics that guide `StaticDecomposition`.
More robust but requires a richer HTN domain model.

### 3.8 DSPy (2024–2026) — Programmatic LLM Orchestration

**Source:** [dspy.ai](https://dspy.ai), [github.com/stanfordnlp/dspy](https://github.com/stanfordnlp/dspy)

Replaces prompt engineering with composable modules + automated optimisation:
- **Signatures** define typed I/O contracts per module
- **Teleprompters** auto-optimise prompts and few-shot examples
- **Modules** (Predict, ChainOfThought, ReAct) compose into pipelines

**Relevance to CaseHub:** DSPy's typed signatures are analogous to output schemas
on task nodes. The optimisation concept could apply to LLM routing prompts — the
`RoutingPromptAssembler` could be auto-optimised based on routing outcome feedback.

---

## 4. Capability Matrix

How does CaseHub's current architecture compare to the state of the art?

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                           Capability Matrix                                         │
│                                                                                     │
│  Capability              │ CaseHub │ Graph   │ RSTD  │ LLM     │ Agent  │ Chat    │
│                          │ Current │ Harness │       │ Compiler│ Orch.  │ HTN     │
│  ────────────────────────┼─────────┼─────────┼───────┼─────────┼────────┼─────────│
│  Static decomposition    │   ✅    │   —     │  ✅   │   —     │   —    │   ✅    │
│  LLM decomposition       │   ❌*   │   ✅    │  ✅   │   ✅    │   ✅   │   ✅    │
│  Hybrid (static+LLM)     │   ❌    │   —     │  —    │   —     │   —    │   ✅    │
│  DAG plan structure       │   ❌    │   ✅    │  —    │   ✅    │   —    │   —     │
│  Parallel execution       │   ❌    │   ✅    │  —    │   ✅    │   —    │   —     │
│  Join semantics (any/all) │   ❌    │   ✅    │  —    │   —     │   —    │   —     │
│  Task description/name    │   ❌    │   ✅    │  ✅   │   ✅    │   ✅   │   ✅    │
│  Output contracts/schema  │   ❌    │   ✅    │  ✅   │   —     │   —    │   ✅    │
│  Validation gates         │   ❌    │   ✅    │  ✅   │   —     │   —    │   ✅    │
│  Plan versioning          │   ❌    │   ✅    │  —    │   —     │   —    │   —     │
│  Multi-level recovery     │   ❌†   │   ✅    │  ✅   │   ✅    │   ✅   │   —     │
│  Re-planning              │   ❌    │   ✅    │  ✅   │   ✅    │   ✅   │   ✅    │
│  Context isolation        │   ❌    │   ✅    │  ✅   │   —     │   ✅   │   —     │
│  Side-effect classif.     │   ❌    │   ✅    │  —    │   —     │   ✅   │   —     │
│  Trust-based filtering    │   ✅    │   —     │  —    │   —     │   —    │   —     │
│  Oversight gates          │   ✅    │   —     │  —    │   —     │   —    │   —     │
│  CBR evidence routing     │   ✅    │   —     │  —    │   —     │   —    │   —     │
│  Agent identity (eidos)   │   ✅    │   —     │  —    │   —     │   —    │   —     │
│  Typed execution state    │   ✅    │   —     │  —    │   —     │   —    │   —     │
│  Composable patterns      │   ✅    │   —     │  —    │   —     │   —    │   —     │
│  ────────────────────────┼─────────┼─────────┼───────┼─────────┼────────┼─────────│
│  * = #13 in progress     │         │         │       │         │        │         │
│  † = oversight gates are  │         │         │       │         │        │         │
│      single-level recovery│         │         │       │         │        │         │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

**Key takeaways:**

1. **CaseHub is strong where others are weak** — trust-based routing, oversight gates,
   CBR evidence, agent identity (eidos), typed execution state, composable patterns.
   No other framework has these.

2. **CaseHub is weak where others are strong** — plan structure (linear-only, no DAG),
   task identity (no description on nodes), execution parallelism, output validation,
   re-planning, and recovery protocols.

3. **The gap is structural, not algorithmic.** CaseHub has the right SPIs (5 strategy
   interfaces) but lacks the plan representation and execution infrastructure to
   exploit them for DAG parallelism, validation gates, and multi-level recovery.

---

## 5. CaseHub Current Architecture

### 5.1 What We Have

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    CaseHub Agentic Orchestration (Current)                   │
│                                                                             │
│  ┌────────────────────────────────────────────────────┐                     │
│  │                 5 Strategy SPIs                     │                     │
│  │  RoutingStrategy ──── who executes                 │                     │
│  │  DecompositionStrategy ── how to break down goals  │                     │
│  │  ActivationRule ──── when to start                 │                     │
│  │  AggregationStrategy ── how to combine results     │                     │
│  │  TerminationCondition ── when to stop              │                     │
│  └────────────────────────────────────────────────────┘                     │
│                           ↓                                                 │
│  ┌────────────────────────────────────────────────────┐                     │
│  │              ExecutionModel<T>                      │                     │
│  │  Composes: routing + decomposition + activation    │                     │
│  │            + aggregation + termination + agents    │                     │
│  │            + failurePolicy + listeners             │                     │
│  └────────────────────────────────────────────────────┘                     │
│                           ↓                                                 │
│  ┌────────────────────────────────────────────────────┐                     │
│  │              ExecutionDriver<T>                     │                     │
│  │  OrchestratedDriver — central control loop         │                     │
│  │  ChoreographedDriver — peer-to-peer events         │                     │
│  └────────────────────────────────────────────────────┘                     │
│                           ↓                                                 │
│  ┌────────────────────────────────────────────────────┐                     │
│  │                TaskNode<T>                          │                     │
│  │  PrimitiveTask(AgentRef, Predicate<T>, Consumer<T>)│                     │
│  │  CompoundTask(String name, List<Method<T>>)        │                     │
│  └────────────────────────────────────────────────────┘                     │
│                           ↓                                                 │
│  ┌────────────────────────────────────────────────────┐                     │
│  │              Pattern Builders (8)                   │                     │
│  │  Supervisor, Sequence, Loop, Parallel, Voting,     │                     │
│  │  Debate, Conditional, HTN                          │                     │
│  └────────────────────────────────────────────────────┘                     │
│                                                                             │
│  Cross-cutting:                                                             │
│  - Oversight gates (risk classification → gate lifecycle)                   │
│  - Trust routing (score → policy → filter → classify)                       │
│  - CBR evidence (case-based reasoning → outcome recording)                  │
│  - Agent identity (eidos descriptors → capability matching)                 │
│  - Accountability (EventLogListener, LedgerListener, MetricsListener)       │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 5.2 Current TaskNode Hierarchy — Limitations

```java
public sealed interface TaskNode<T>
        permits TaskNode.PrimitiveTask, TaskNode.CompoundTask {

    // HTN leaf — agent + precondition + effect
    // Missing: description, dependencies, output contract
    record PrimitiveTask<T>(AgentRef agent, Predicate<T> precondition,
                            Consumer<T> effect) implements TaskNode<T> {}

    // HTN non-leaf — goal + decomposition methods
    record CompoundTask<T>(String name, List<DecompositionMethod<T>> methods)
            implements TaskNode<T> {
        public CompoundTask { methods = List.copyOf(methods); }
    }
}
```

**Problems:**

1. **No task description on leaves** — `PrimitiveTask` has no name or description.
   Every other framework gives tasks human-readable identity. Needed for logging,
   oversight display, re-planning, and passing instructions to agents.

2. **Precondition/effect are unused** — `HtnBuilder.flatten()` extracts only `agent()`.
   The STRIPS-style precondition/effect model is structurally present but functionally
   dead code.

3. **No representation for LLM-generated tasks** — an LLM decomposition produces
   agent-task pairs with natural language descriptions. Forcing these into
   `PrimitiveTask` with always-true/no-op precondition/effect is a type-level lie.

4. **Linear-only plan structure** — `DecompositionStrategy` returns `List<TaskNode<T>>`.
   No way to express "A and B can run in parallel, C depends on both."

5. **No output contracts** — tasks don't declare what they produce, so validation gates
   and selective retry are impossible.

---

## 6. Design Directions

### 6.1 Immediate (#13 scope — blocks)

**Add `PlannedTask` to the sealed hierarchy:**

```java
public sealed interface TaskNode<T>
        permits TaskNode.PrimitiveTask, TaskNode.CompoundTask, TaskNode.PlannedTask {

    record PrimitiveTask<T>(AgentRef agent, Predicate<T> precondition,
                            Consumer<T> effect) implements TaskNode<T> {}

    record CompoundTask<T>(String name, List<DecompositionMethod<T>> methods)
            implements TaskNode<T> {
        public CompoundTask { methods = List.copyOf(methods); }
    }

    record PlannedTask<T>(String description, AgentRef agent,
                          @Nullable String rationale) implements TaskNode<T> {}
}
```

**Implement `LlmDecomposition<T>`:**
- Takes a `CompoundTask`, builds a prompt with goal + available agents (from context)
- Invokes `AgentProvider` (same pattern as `LlmSelectedRouting`)
- Parses JSON response into a `List<PlannedTask<T>>`
- Maps agent names from LLM response to `AgentRef` via candidate lookup

### 6.2 Near-Term (engine scope)

**a) DAG plan structure** — replace `List<TaskNode<T>>` with a plan graph:

```
ExecutionPlan<T>
├── nodes: Map<String, TaskNode<T>>      — keyed by task ID
├── edges: Set<Dependency>                — (from, to, joinType)
└── JoinType: ALL_OF | ANY_OF
```

This enables parallel execution of independent tasks and structural alternatives.

**b) DAG-aware execution driver** — a new `ParallelDriver<T>` that performs topological
dispatch (same concept as LLMCompiler's Task Fetching Unit). Independent tasks run
concurrently; dependent tasks wait for predecessors.

**c) Output contracts** — tasks declare expected output shape. The execution driver
validates before passing to downstream tasks. Failed validation triggers retry at
the task level, not pipeline restart.

### 6.3 Medium-Term (engine + blocks)

**d) Multi-level recovery protocol** (inspired by Graph Harness):
- Level 1: Retry (bounded, at task level)
- Level 2: Local patch (re-decompose failed subtask only)
- Level 3: Full replan (create new plan version)

This composes naturally with oversight gates — a gate rejection IS a Level 2 recovery
(the human patches the plan by rejecting a risky action).

**e) Plan versioning** — immutable plan snapshots with version IDs. Each recovery action
creates a new version. Enables full audit trail of plan evolution.

**f) Hybrid decomposition** (inspired by ChatHTN):
- Try `StaticDecomposition` first (fast, deterministic, sound)
- If no method matches → fall back to `LlmDecomposition` (flexible, handles novel goals)
- Symbolic planner validates LLM output before execution

**g) Context isolation** — each task gets its own scoped execution context. Prevents
failure history from one task from corrupting reasoning in sibling tasks.

### 6.4 Long-Term

**h) LLM-generated heuristics** — instead of having the LLM plan directly, have it
generate search heuristics for the HTN planner (à la the Pytrich paper). More robust
than direct LLM planning, but requires a richer domain model.

**i) Prompt optimisation** (DSPy-style) — auto-optimise routing and decomposition prompts
based on outcome feedback. The CBR recording infrastructure already captures the data;
the optimisation loop is the missing piece.

**j) Re-planning on failure** — when a task fails, the system can:
- Re-decompose the remaining compound tasks with updated context
- Reroute to a different agent for the failed task
- Insert new compensating tasks

---

## 7. Priority Assessment

| Direction | Value | Effort | Dependencies | Priority |
|-----------|-------|--------|-------------|----------|
| **PlannedTask + LlmDecomposition** (#13) | High — unblocks LLM-driven planning | S–M | None | **P0 — this session** |
| **DAG plan structure** | Very high — enables parallelism + recovery | L | Engine changes | **P1 — next** |
| **DAG-aware driver** | Very high — realises DAG value | L | DAG plan structure | P1 |
| **Output contracts** | High — enables validation gates | M | DAG plan structure | P2 |
| **Multi-level recovery** | High — production resilience | L | DAG, output contracts | P2 |
| **Hybrid decomposition** | Medium — optimisation, not enablement | M | LlmDecomposition | P2 |
| **Plan versioning** | Medium — audit trail | M | DAG plan structure | P3 |
| **Context isolation** | Medium — robustness | M | Engine changes | P3 |
| **LLM-generated heuristics** | Low (for now) — needs richer domain model | L | Mature HTN usage | P4 |
| **Prompt optimisation** | Low (for now) — needs more outcome data | L | CBR maturity | P4 |

---

## 8. Sources

### Papers
- [Graph Harness: Scheduler-Theoretic Framework for LLM Agent Execution](https://arxiv.org/html/2604.11378v1) (April 2026)
- [RSTD: Runtime-Structured Task Decomposition for Agentic Coding](https://arxiv.org/html/2605.15425v1) (May 2026)
- [ChatHTN: Interleaving LLM and Symbolic HTN Planning](https://arxiv.org/html/2505.11814) (May 2025)
- [LLMCompiler: Parallel Function Calling](https://arxiv.org/abs/2312.04511) (ICML 2024)
- [AgentOrchestra: Hierarchical Multi-Agent Framework](https://arxiv.org/html/2506.12508v1) (June 2025)
- [GAP: Graph-based Agent Planning](https://arxiv.org/html/2510.25320v1) (October 2025)
- [HTN Planning with LLM-Generated Heuristics](https://arxiv.org/html/2605.07707v1) (May 2026)
- [Multi-Agent Orchestration Survey](https://arxiv.org/html/2601.13671v1) (January 2026)
- [Multi-Agent Reflexion (MAR)](https://arxiv.org/html/2512.20845) (December 2025)

### Frameworks & Guides
- [DSPy — Programming, Not Prompting](https://dspy.ai)
- [LLM Compiler Agent Pattern](https://agent-patterns.readthedocs.io/en/stable/patterns/llm-compiler.html)
- [LangChain Plan-and-Execute Agents](https://www.langchain.com/blog/planning-agents)
- [LLM Agent Task Decomposition Strategies](https://apxml.com/courses/agentic-llm-memory-architectures/chapter-4-complex-planning-tool-integration/task-decomposition-strategies)
- [6 Multi-Agent Orchestration Patterns (2026)](https://beam.ai/agentic-insights/multi-agent-orchestration-patterns-production)

### Architecture Comparisons
- [ReAct vs Plan-and-Execute vs ReWOO vs Reflexion](https://theaiengineer.substack.com/p/the-4-single-agent-patterns)
- [Agent Architectures: ReAct, Plan-Execute, Graph Agents](https://dasroot.net/posts/2026/04/agent-architectures-react-plan-execute-graph-agents/)
- [AI Agent Architecture Patterns (OpenClaw)](https://openclaw-ai.net/en/blog/ai-agent-architecture-patterns-2026)

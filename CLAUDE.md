# blocks Workspace
**Name:** casehub-blocks

**Physical path:** `/Users/mdproctor/claude/casehub/blocks/CLAUDE.md`
**Symlinked at:** `/Users/mdproctor/claude/public/casehub/blocks/CLAUDE.md`
**Project repo:** `/Users/mdproctor/claude/casehub/blocks`
**Workspace:** `/Users/mdproctor/claude/public/casehub/blocks`
**Workspace type:** public

## Session Start

Run `add-dir /Users/mdproctor/claude/casehub/blocks` before any other work.

## Artifact Locations

| Skill | Writes to |
|-------|-----------|
| brainstorming (specs) | `specs/` |
| writing-plans (plans) | `plans/` |
| handover | `HANDOFF.md` |
| idea-log | `IDEAS.md` |
| design-snapshot | `snapshots/` |
| adr | `adr/` |
| write-blog | `blog/` |

## Structure

- `HANDOFF.md` — session handover (single file, overwritten each session)
- `IDEAS.md` — idea log (single file)
- `specs/` — brainstorming / design specs (superpowers output)
- `plans/` — implementation plans (superpowers output)
- `snapshots/` — design snapshots with INDEX.md (auto-pruned, max 10)
- `adr/` — architecture decision records with INDEX.md
- `blog/` — project diary entries with INDEX.md
- `design/` — epic journal (created by `epic` at branch start)

## Git Discipline

Two git repositories are active in every session:
- **Workspace** (`/Users/mdproctor/claude/public/casehub/blocks`) — methodology artifacts: handover, blog (staging before publish), plans, snapshots
- **Project repo** (`/Users/mdproctor/claude/casehub/blocks`) — source code, ADRs (`docs/adr/`), specs

Never rely on CWD for git operations — the session may have started in either repo. Always use explicit paths:
```bash
git -C /Users/mdproctor/claude/public/casehub/blocks ...   # workspace artifacts
git -C /Users/mdproctor/claude/casehub/blocks ...           # project artifacts
```

## Rules

- All methodology artifacts go here, not in the project repo
- Promotion to project repo is always explicit — never automatic
- Workspace branches mirror project branches — switch both together

## Routing

| Artifact   | Destination | Notes |
|------------|-------------|-------|
| adr        | project     | lands in `docs/adr/` |
| blog       | workspace   | staged here; published to mdproctor.github.io via publish-blog |
| design     | project     | journal file lives in workspace design/; DESIGN.md merge target is project docs/DESIGN.md |
| snapshots  | workspace   | |
| specs      | project     | lands in project `docs/` |
| plans      | workspace   | |
| handover   | workspace   | |

---

# CaseHub Blocks

## Project Type

type: java

## Repository Role

Reusable building blocks for CaseHub applications — composed from qhorus, engine, and work primitives. Foundation-adjacent library (sits between foundation and application tier). Single module, single artifact: `casehub-blocks`.

**Peer repos (each has its own Claude session — do not commit to these):**
platform, eidos, ledger, connectors, iot, work, worker, qhorus, pages, engine, claudony, openclaw, neural-text, devtown, aml, clinical, drafthouse, life, quarkmind, flow, soc, fsitrading, ras, ops, workers, desiredstate

## Build Commands

```bash
mvn --batch-mode install
mvn --batch-mode test
```

## Testing

No Quarkus runtime — plain JUnit 5 tests with Mockito. No CDI container in tests.

## Key Directories

| Path | Contents |
|------|----------|
| `src/main/java/io/casehub/blocks/channel/` | Channel utility blocks — message meta, context tracking, bounded projection |
| `src/test/java/io/casehub/blocks/channel/` | Tests for channel blocks |
| `src/main/java/io/casehub/blocks/agentic/` | Compositional agentic orchestration — five SPIs, execution drivers, pattern builders |
| `src/test/java/io/casehub/blocks/agentic/` | Tests for agentic orchestration blocks |
| `src/main/java/io/casehub/blocks/routing/` | Trust routing utilities — shared preference keys, policy resolver, compliance records |
| `src/test/java/io/casehub/blocks/routing/` | Tests for routing utilities |

## Package: `io.casehub.blocks.channel`

| Class | What it does |
|-------|-------------|
| `ChannelMessageMeta` | Sentinel-prefixed key=value metadata headers in message bodies. Apps choose their own sentinel. Methods: `parseMeta()`, `bodyContent()`, `encode()`, `parseInt()` |
| `ContextTracker` | Incremental LLM context window usage tracking via atomic counters. Thread-safe. |
| `ContextSnapshot` | Immutable record of context state: contribution chars, window size, effective %, threshold exceeded |
| `BoundedProjectionDecorator<S>` | Generic decorator wrapping any qhorus `ChannelProjection<S>` — skips messages past a configurable bound. Consumer supplies the value extraction function. |
| `ChannelAgentHandler` | SPI interface for sub-task handlers: `handles()`, `prepareTask()`, `buildResponse()`. First-match routing. |
| `ChannelAgentDispatcher` | First-match handler routing + agent invocation. Takes `Function<AgentTask, String>` (agent provider) and `Consumer<MessageDispatch>` (message sink). Subclass to override `onError()`. |
| `ChannelAgentRequest` | Record: channelId, correlationId, message (the sub-task trigger) |
| `AgentTask` | Record: systemPrompt, assembledInput (what to send to the LLM) |
| `AgentResultParseException` | Unchecked exception for handler parse failures |

## Package: `io.casehub.blocks.agentic`

Compositional agentic orchestration framework — eight sub-packages implementing five SPIs for routing, decomposition, activation, aggregation, and termination, plus execution drivers and pre-composed pattern builders.

| Sub-package | What it contains |
|-------------|-----------------|
| `agentic` | Foundation types: `AgentRef` (sealed: WorkerAgent, ChannelAgent, HumanAgent, ExternalAgent, ComposedAgent), `AgentResult`, `RoutingCandidate`, `FailurePolicy` |
| `agentic.routing` | Routing SPI: `RoutingStrategy<T>`, `RoutingDecision` (sealed: Selected, Unresolvable, Escalate), `FirstMatchRouting`, `RoundRobinRouting`, `SequentialRouting`, `LlmSelectedRouting` |
| `agentic.decomposition` | Decomposition SPI: `DecompositionStrategy<T>`, `TaskNode` (sealed: PrimitiveTask, CompoundTask), `DecompositionMethod`, `IdentityDecomposition`, `StaticDecomposition` |
| `agentic.activation` | Activation SPI: `ActivationRule<T>`, `ActivationContext`, `OnExplicitDispatch`, `MaxIterationsGuard` |
| `agentic.aggregation` | Aggregation SPI: `AggregationStrategy<T>`, `AggregationResult` (sealed: Resolved, Partial, Deadlocked), `PassThrough`, `CollectAll`, `MajorityVote` |
| `agentic.termination` | Termination SPI: `TerminationCondition<T>`, `TerminationDecision` (sealed: Continue, Complete, Failed, Escalate), `GoalReached`, `MaxIterationsTermination`, `JudgeConvergence` |
| `agentic.model` | Execution model: `ExecutionModel<T>`, `ExecutionDriver<T>`, `AbstractExecutionDriver`, `OrchestratedDriver`, `ChoreographedDriver`, `AgentInvoker<T>`, `ExecutionResult` (sealed: Completed, Failed, Escalated, Cancelled), `ExecutionState` (sealed: Idle, Running, WaitingForAgent, WaitingForEvent, Complete, Faulted, Cancelled), `ExecutionEventListener` |
| `agentic.listener` | Accountability listeners: `OrchestrationEventType`, `EventLogListener` (operational audit via EventSink), `LedgerExecutionListener` (compliance audit via LedgerSink) |
| `agentic.pattern` | Pattern DSL: `Patterns` entry point, `AbstractPatternBuilder`, 8 builders (Supervisor, Sequence, Loop, Parallel, Voting, Debate, Conditional, HTN) |

## Package: `io.casehub.blocks.routing`

Shared trust routing utilities — eliminates duplicated preference-to-policy boilerplate across domain repos.

| Class | What it does |
|-------|-------------|
| `DoublePreference` | `SingleValuePreference` record for double-typed preference values. Replaces copies in aml, devtown, life. |
| `IntPreference` | `SingleValuePreference` record for int-typed preference values. Replaces copies in aml, devtown. |
| `TrustRoutingPolicyKeys` | Parameterised `PreferenceKey` definitions — scope prefix + 4 universal keys (threshold, minimum-observations, borderline-margin, blend-factor) + builder for domain-specific quality floor keys. |
| `TrustRoutingPolicyResolver` | Stateless utility: `resolve(Preferences, TrustRoutingPolicyKeys)` → `TrustRoutingPolicy`. Also exposes `collectFloors()` for hybrid providers that read some fields from a domain registry. |
| `RoutingDecisionRecord` | Compliance audit record for trust-weighted routing decisions: capabilityTag, workerId, trustScoreAtRouting, thresholdApplied, evidenceEntryId. |
| `TrustRoutingRequirement` | Compliance evidence wrapper: requirementId, citation, mechanism, status, decisions. |
| `RequirementStatus` | Enum: CLOSED, PARTIAL, BREACHED, GAP. |

## Dependencies

**Compile:** `casehub-qhorus-api`, `casehub-work-api`, `casehub-engine-api`, `casehub-eidos-api`, `casehub-worker-api`, `org.jspecify:jspecify`
**Provided:** `io.smallrye.reactive:mutiny`, `casehub-platform-agent-api`, `casehub-platform-api`
**Test:** `casehub-qhorus`, `casehub-qhorus-testing`, `casehub-engine`, `casehub-engine-testing`, `assertj`, `mockito`, `awaitility`

## Consumers

| Repo | What it uses |
|------|-------------|
| casehub-drafthouse | All channel blocks — DebateProtocol delegates to ChannelMessageMeta, DebateSession uses ContextTracker, RoundBoundedProjection extends BoundedProjectionDecorator, ChannelAgentDispatcher subclass with debate-specific error dispatch |
| casehub-aml | Routing: `TrustRoutingPolicyKeys`, `TrustRoutingPolicyResolver`, `DoublePreference`, `IntPreference` |
| casehub-devtown | Routing: `TrustRoutingPolicyKeys`, `TrustRoutingPolicyResolver.collectFloors()`, `DoublePreference` |
| casehub-life | Routing: `TrustRoutingPolicyKeys`, `TrustRoutingPolicyResolver.collectFloors()`, `DoublePreference` |

## Blocks Scope Criteria

A pattern belongs in blocks if it meets at least one of these criteria:
1. **Needs an LLM in the loop** — the pattern involves LLM invocation, prompt construction, or LLM-driven decision-making
2. **Uses classical AI** — classical planning, Bayesian reasoning, CEP (complex event processing), or similar
3. **Requires integration with foundational platform parts** — the pattern composes across qhorus, engine, work, or eidos APIs in a way that would otherwise be duplicated by every consumer

**What does NOT belong in blocks:**
- Small isolated utilities (backoff computation, rate limiters, CloudEvent adapters) → stay in platform or engine
- Pure SPI unifications (e.g. ProvisionerConfigRegistry) → stay in the API module that owns the provisioning lifecycle (engine-api)
- Domain-specific logic that happens to be duplicated but doesn't involve AI or foundational integration

**The test:** if removing the LLM/AI/integration aspect leaves a generic utility, it belongs in platform. If removing the domain-specific aspect leaves a reusable AI-integration pattern, it belongs in blocks.

## Trust Routing Architecture

The trust routing system spans three layers — blocks owns policy configuration, not score computation or strategy execution.

| Layer | Owner | What it does |
|-------|-------|-------------|
| Score computation | **ledger** | `TrustScoreRoutingPublisher` computes trust scores from ledger entries and publishes them. The `trust-score-routing` package owns all score payloads and events. |
| Policy configuration | **blocks** (routing package) + **engine-api** (`TrustRoutingPolicyProvider` SPI) | `TrustRoutingPolicyKeys` + `TrustRoutingPolicyResolver` provide the shared preference-to-policy loading. Domain repos implement `TrustRoutingPolicyProvider` using these utilities. |
| Strategy execution | **engine** | `TrustWeightedAgentStrategy` applies trust scores against policy thresholds and quality floors. `SemanticAgentRoutingStrategy` adds embedding-based re-ranking. |

Domain repos (aml, devtown, clinical, life, ops) implement `TrustRoutingPolicyProvider` from engine-api — they configure policy parameters, not compute scores or execute routing.

## Consolidation Epic

Epic #28 tracks extraction of shared patterns from domain repos into blocks. Each child issue covers a distinct pattern duplicated across 2+ repos.

| # | Title | Scale | Complexity | Ready? | Destination | Migrates from | Downstream consumers |
|---|-------|-------|------------|--------|-------------|---------------|---------------------|
| #17 | Trust routing YAML | M | Med | **Done** | blocks | aml, devtown, clinical, life, ops, soc | aml, devtown, clinical, life, ops, soc, fsitrading |
| #22 | Debate channel infrastructure | L | High | Yes but large | blocks | drafthouse | drafthouse, devtown, clinical, aml |
| #23 | Oversight gate lifecycle + risk classification | L | High | Yes but large | blocks | openclaw, engine-api | openclaw, aml, soc, life, devtown, clinical, iot, claudony |
| #24 | Universal pluggable routing strategy | L | High | Design-first | blocks | engine, work | engine, work, qhorus, eidos |
| #25 | Worker data coordination (DataExchange/DataChannel) | L | High | Blocked on engine#528 | blocks | engine | engine, workers, desiredstate |
| #27 | Layered event summarisation | M | Med | Not yet — quarkmind still baking | blocks | quarkmind | quarkmind, iot, aml, clinical |

## Cross-Repo Scanning

To scan all CaseHub repos for shared patterns, use `ide_open_workspace` with the parent directory:
```
ide_open_workspace(path="/Users/mdproctor/claude/casehub")
```
This opens all 26 repos in a single IntelliJ window with full cross-project code intelligence. Use `ide_find_class`, `ide_search_text`, and `ide_find_references` with the workspace `project_path` for cross-repo analysis.

## Cross-Repo Consolidation Commits

When implementing consolidation work (epic #28), commits to peer repos (aml, devtown, life, etc.) on main are expected and approved. This is an exception to the normal "do not commit to peer repos" rule — consolidation by definition spans repos. Always:
- Verify all affected repos are on main before starting
- Install blocks to local Maven repo before compiling consumers (`mvn install -DskipTests`)
- Commit each repo separately with meaningful messages tagged with the blocks issue (`Refs casehubio/blocks#N`)
- Push all repos after all commits succeed

## Extraction Plan

Full extraction plan with prioritisation: [casehubio/parent#310 comment](https://github.com/casehubio/parent/issues/310#issuecomment-4795440229). P1–P5 complete. Consolidation epic #28 tracks remaining extractions.

## Writing Style Guide

**The writing style guide at `~/claude-workspace/writing-styles/blog-technical.md` is mandatory for all blog and diary entries.** Load it in full before drafting. Complete the pre-draft voice classification (I / we / Claude-named) before generating any prose. Do not show a draft without verifying it against the style guide.

## Project Artifacts

Paths that are project content (not workspace noise). Skills use this to avoid
filtering or dropping commits that touch these paths.

| Path | What it is |
|------|------------|
| `CLAUDE.md` | Project conventions |
| `docs/` | Documentation |

## IntelliJ MCP Routing

One IntelliJ MCP server is available:

- **`mcp__intellij-index__*`** — use this for ALL code intelligence and navigation. Supports auto-opening projects via `project_path` — pass the project path and the plugin opens it automatically. Never ask the user to open a project manually.

`mcp__intellij__*` (built-in JetBrains MCP) is **disabled** due to a memory leak. Do not attempt to use it.

## Development Workflow

Before designing: `superpowers:brainstorming`
Before implementing: `superpowers:test-driven-development`
For all Java work: `java-dev`
Before committing: `superpowers:requesting-code-review`
After implementation: `implementation-doc-sync`

## Work Tracking

Issue tracking: enabled
GitHub repo: casehubio/blocks
Changelog: GitHub Releases

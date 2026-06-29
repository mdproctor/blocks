# ARC42STORIES — casehub-blocks

## Layer Taxonomy

| § | Chapter | Status | Notes |
|---|---------|--------|-------|
| 1 | Introduction and Goals | ✅ initial (#2) | |
| 2 | Constraints | 🔲 pending (TBD) | |
| 3 | Context and Scope | 🔲 pending (TBD) | |
| 4 | Solution Strategy | ✅ initial (#2) | Single module, all foundation deps |
| 5 | Building Block View | ✅ initial (#2) | `channel` package — 4 classes |
| 6 | Runtime View | 🔲 pending (TBD) | |
| 7 | Deployment View | 🔲 pending (TBD) | N/A — library, no deployment |
| 8 | Crosscutting Concepts | 🔲 pending (TBD) | |
| 9 | Architecture Decisions | ✅ initial (#2) | Sentinel parameterised, not hardcoded |
| 10 | Quality Requirements | 🔲 pending (TBD) | |
| 11 | Risks and Technical Debt | 🔲 pending (TBD) | |
| 12 | Glossary | 🔲 pending (TBD) | |

---

## §1 Introduction and Goals

Curated collection of reusable building blocks composed from CaseHub foundation primitives (qhorus, engine, work). App developers pick the blocks they need to assemble their applications.

Not infrastructure, not a framework, not a tool library. Blocks are composed workflows and interaction patterns that sit between the foundation layer and application logic.

**Epic:** casehubio/parent#310

---

## §4 Solution Strategy

Single Maven module. Depends on `casehub-qhorus-api` + `casehub-work-api` + `casehub-engine-api`. No tiered sub-modules — blocks can freely compose from all three foundations. Every CaseHub app already has the full foundation stack, so transitive deps are not a concern.

---

## §5 Building Block View

### Package: `io.casehub.blocks.channel`

Channel utility blocks — foundational patterns for LLM-mediated channel communication.

| Block | What it does | Extracted from |
|-------|-------------|---------------|
| `ChannelMessageMeta` | Sentinel-prefixed key=value metadata headers in message bodies. Apps choose their own sentinel prefix — the utility handles the format. | DraftHouse `DebateProtocol` |
| `ContextTracker` | Incremental LLM context window usage tracking with threshold warnings. Thread-safe via atomic counters. | DraftHouse `ContextTracker` |
| `ContextSnapshot` | Immutable record of context state at a point in time. | DraftHouse `ContextSnapshot` |
| `BoundedProjectionDecorator<S>` | Generic decorator wrapping any qhorus `ChannelProjection<S>` — skips messages where an extracted value exceeds a bound. Consumer supplies the extraction function. | DraftHouse `RoundBoundedProjection` |
| `ChannelAgentHandler` | SPI for sub-task handlers: `handles()` predicate, `prepareTask()` assembles LLM input, `buildResponse()` builds qhorus MessageDispatch from LLM output. First-match routing. | DraftHouse `ChannelAgentHandler` |
| `ChannelAgentDispatcher` | First-match handler routing + agent invocation. Agent provider via `Function<AgentTask, String>`, message sink via `Consumer<MessageDispatch>`. Subclass `onError()` for app-specific error encoding. CDI-friendly (protected no-args constructor). | DraftHouse `ChannelAgentDispatcher` |
| `ChannelAgentRequest` | Sub-task trigger record: channelId, correlationId, OutboundMessage. | DraftHouse `ChannelAgentRequest` |
| `AgentTask` | LLM input record: systemPrompt, assembledInput. | DraftHouse `AgentTask` |
| `AgentResultParseException` | Unchecked exception signalling handler parse failure — dispatcher catches and routes to `onError()`. | DraftHouse `AgentResultParseException` |

### Planned packages (per parent#310)

| Package | What it will contain | Priority |
|---------|---------------------|----------|
| `io.casehub.blocks.conversation` | Structured conversation protocol — round-based multi-agent deliberation with point lifecycle, human escalation, sub-task dispatch | P5 |
| `io.casehub.blocks.gate` | Oversight gate lifecycle — risk classification → approval gate → commitment → decision | P6 (from openclaw) |

---

## §9 Architecture Decisions

### Sentinel prefix is parameterised, not hardcoded

`ChannelMessageMeta` takes the sentinel as a parameter in every method rather than hardcoding a default. Each app defines its own prefix (DraftHouse uses `DHMETA:`). This avoids coupling blocks to any one app's protocol while keeping the encoding scheme standard.

### BoundedProjectionDecorator uses a value extractor function

Rather than hardcoding "round number" as the bound concept, the decorator takes a `ToIntFunction<MessageView>` that extracts the bound value from each message. DraftHouse passes `msg -> DebateProtocol.parseRound(parseMeta(msg.content()))`. Other apps can bound on sequence number, timestamp, or any integer field.

### ChannelAgentDispatcher uses Consumer/Function, not MessageService

The dispatcher takes `Function<AgentTask, String>` for agent invocation and `Consumer<MessageDispatch>` for message posting — not the qhorus runtime `MessageService` directly. This keeps blocks at the API dependency level (qhorus-api only, not qhorus runtime). Apps pass `messageService::dispatch` and `agentProvider::analyse` as method references. Error dispatch is a protected `onError()` method that apps override to encode errors in their protocol format.

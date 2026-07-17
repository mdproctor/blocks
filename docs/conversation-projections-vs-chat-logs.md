# Conversation Projections vs Global Chat History

Why CaseHub's conversation model is fundamentally different from what
everyone else is building — and what that means for cost, correctness,
and composability in multi-agent systems.

---

## What everyone else builds

The industry standard for agentic conversation memory is: persist the chat,
replay it into context. Frameworks like LangChain, CrewAI, and AutoGen store
message histories in vector databases, key-value stores, or structured logs.
When an agent needs context, it reads the history — either the full transcript
or a similarity-searched subset.

This is a distributed append-only log with no read model.

The cost is O(history) on every read. Every agent pays it. Every turn pays it.
The log grows monotonically. Summarisation helps (compress old messages into
a summary), but the summary is lossy and the cost of producing it is itself
O(history).

## What CaseHub builds instead

CaseHub's conversation projection is a materialised view over the event
stream — bounded by active state, not history length.

A `ChannelProjection<ConversationState>` folds the channel's message history
into a structured read model: points raised, responses, status transitions,
sub-task findings, flags. The fold is incremental — new messages extend the
state from a cursor without re-processing history.

A channel with 2,000 messages and 3 open points costs the same to read as a
channel with 20 messages and 3 open points. The history is still there
(qhorus stores every message), but agents don't read it — they read the
projection.

That's CQRS applied to agentic conversations. Nobody in the LLM tooling
space is doing this — they're all building better chat logs.

## Why this matters: three dimensions

### 1. Cost compounds, structure doesn't

A 200-message channel costs ~50K tokens every time an LLM reads it.
A structured projection is ~2K tokens.

Now multiply. In a choreographed workflow with five agents across seven
steps, every agent needs context on every turn. With flat transcripts,
that's 50K × 5 agents × N turns. With projections, it's 2K × 5 agents ×
N turns.

The difference isn't linear — it's multiplicative. More agents × more steps
× more messages. And projections support incremental folding from a cursor,
so new messages don't re-process history. An LLM re-reading the transcript
pays full cost every time.

At CaseHub scale — thousands of concurrent cases, each with multiple
channels, each with multiple agents — the token savings are the difference
between viable and prohibitive.

### 2. Determinism, not capability

An LLM CAN reconstruct structure from a flat transcript — usually. But
"usually" isn't acceptable when the conversation is evidence.

- Did step 4 complete, or was the STATUS message a progress update that
  never reached DONE?
- Does that finding belong to the risk-assessment topic or the
  transaction-analysis topic?
- Is this point resolved or still open?

The LLM might get it right 95% of the time. The fold gets it right 100%
of the time — same input, same output, no inference. No hallucination risk
on the structural level.

And when five agents independently infer structure from the same transcript,
they can disagree about what step they're on, which points are resolved,
and what's still pending. The projection is the single source of truth —
all agents see the same state.

For regulated domains — AML, clinical, insurance — the conversation
projection IS the audit trail. It proves the process was followed: this
task was commanded, here's the progress, here's how it resolved. That's
not something you can derive probabilistically.

### 3. Composability at join time

When a sub-agent spins up at step 5 to handle a specific finding, it needs
context fast. Hand it the projection:

> "You're in topic risk-assessment, step 5 of 7. Here are the 2 open points
> relevant to you. The review agent raised a concern about transaction
> velocity. The KYC check is complete (DONE). Your job: assess the flagged
> pattern."

That's a prompt preamble. Specific, structured, bounded.

The alternative: "Here are 200 messages. Figure out where we are, what's
relevant to you, and what the other agents have already done."

The agent's first turn is wasted on orientation. And it might orient wrong —
picking up on a resolved point, missing an active one, or confusing messages
from different topics.

## Use cases in CaseHub

### Clinical case review

A case opens with three topics: patient-history, treatment-plan,
insurance-pre-auth. The choreography runs three phases. At any point, the
case worker opens the conversation and sees:

- **Patient History** (✅ complete): 3 points raised, all resolved.
  Obligation chain: COMMAND → STATUS → DONE ✓
- **Treatment Plan** (🔄 active): 2 points open, 1 sub-task pending.
  Reactions showing agent acknowledgments.
- **Insurance Pre-Auth** (⏳ pending): not started yet.

They don't need to read 47 interleaved messages. The structure tells the
story.

### AML transaction monitoring

Topics: transaction-analysis, kyc-verification, suspicious-activity-report.
The obligation chain (COMMAND → STATUS → DONE) per topic is compliance
evidence — proving each step was requested, executed, and completed. The
regulator doesn't read the chat log; they read the structured projection
showing the complete audit trail.

Reactions show the compliance officer acknowledged findings — a lightweight
signal that reduces noise without losing the record.

### DevTown code review

Topics map to review dimensions: correctness, security, performance. Each
topic has its own points and responses. The reviewer sees which dimensions
are covered and which have open findings. The obligation chain shows whether
each dimension completed its full cycle (review requested → findings
reported → feedback incorporated → review signed off).

### Drafthouse multi-agent debate

Topics organise multi-round deliberation. Each debate dimension gets its
own topic. Agents raise points, challenge, accept, or escalate — all
tracked as structured conversation state. The moderator sees the debate
across all dimensions simultaneously, with reactions showing which points
resonated.

## The cost comparison

| Dimension | Global chat history | Conversation projection |
|-----------|-------------------|------------------------|
| Read cost per agent turn | O(history) — all messages | O(active state) — open points only |
| Incremental update | Re-read everything or maintain cursor + full context | Fold from cursor — new messages only |
| Multi-agent cost | N agents × O(history) per turn | N agents × O(active state) per turn |
| Correctness | Probabilistic (LLM inference) | Deterministic (pure fold) |
| Agent join cost | Full transcript parse on first turn | Projection snapshot — immediate orientation |
| Topic isolation | LLM must infer topic boundaries | Query-level filtering (`MessageQuery.topic()`) |
| Audit trail | Chat log (unstructured) | Obligation chain per topic (structured) |
| Reactions | Separate lookup, no correlation | Correlated by message ID, rendered inline |

## The architecture

```
┌─────────────────────────────────────────────────┐
│  qhorus (infrastructure)                        │
│                                                 │
│  MessageStore ──→ ProjectionService ──→ fold    │
│  TopicStore        (incremental, scoped)        │
│  ReactionStore                                  │
│  (topic, reaction, message primitives)          │
└───────────────┬─────────────────────────────────┘
                │ MessageView (topic, id, type)
                ▼
┌─────────────────────────────────────────────────┐
│  blocks (shared building blocks)                │
│                                                 │
│  ConversationProjection ──→ ConversationState   │
│  (pure fold, topic-aware)   (points by topic)   │
│                                                 │
│  ConversationRenderer                           │
│  (topic grouping, obligation chains, reactions) │
│                                                 │
│  + ProgressInstance (future, from work#237)      │
└───────────────┬─────────────────────────────────┘
                │ structured state + reactions
                ▼
┌─────────────────────────────────────────────────┐
│  domain apps (consumers)                        │
│                                                 │
│  drafthouse — debate visualisation              │
│  devtown    — code review dashboard             │
│  clinical   — case review progress              │
│  AML        — compliance audit trail            │
└─────────────────────────────────────────────────┘
```

The projection is the read model. The channel is the event store. The
renderer is the view. This is event sourcing applied to multi-agent
conversations — the same pattern that powers financial ledgers, applied
to AI agent coordination.

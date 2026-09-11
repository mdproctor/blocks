# Decisions — #249 Channel Infrastructure YAML Surface

## D1: CDI injection for code-only conversation dependencies

**Choice:** CompiledConversation exposes declarative config (turnPolicy, termination, participants, epistemic rules, convergence). Consumer constructs ConversationOrchestrator by combining compiled fields with their CDI-injected functional impls (PromptAssembler, ResponseMessageBuilder, AgentInvoker).
**Alternatives:**
- Factory with CDI slots — compiler produces a factory taking CDI deps as method params. Tighter coupling between compiler and runtime.
**Rationale:** ConversationOrchestrator has 10 constructor args; 5 are functional interfaces. The compiler shouldn't know about runtime wiring. The consumer already has CDI access.
**Trade-offs:** Consumer must manually wire the orchestrator constructor — but the fields are clearly split (YAML vs CDI).
**Sources:** ConversationOrchestrator.java, ConversationConfig.java, PromptAssembler.java, ResponseMessageBuilder.java
**Exploration:** quick
**Status:** captured

## D2: Extend TerminationSpec with negotiation termination types

**Choice:** Add `accepted`, `terminal-outcome`, `deadline` as new variants to the existing `TerminationSpec` sealed interface. NegotiationSpec already references `List<TerminationSpec>`.
**Alternatives:**
- Separate NegotiationTerminationSpec — cleaner type safety but more infrastructure and a second polymorphic hierarchy
**Rationale:** One polymorphic hierarchy is simpler. The registry resolves context-appropriate types. NegotiationSpec already uses TerminationSpec.
**Trade-offs:** Conversation-specific and negotiation-specific terminations share the same sealed interface. Minimal risk — wrong-context usage is a compiler concern, not a type concern.
**Sources:** TerminationSpec.java, AcceptedTermination.java, TerminalOutcomeTermination.java, DeadlineTermination.java, NegotiationSpec.java
**Exploration:** quick
**Status:** captured

## D3: Include NegotiationCompiler

**Choice:** Build a NegotiationCompiler that compiles NegotiationSpec → CompiledNegotiation (NegotiationProjection + composed termination). Close the spec-only gap.
**Alternatives:**
- Termination types only — add variants but leave NegotiationSpec without a compiler. Kicks the can.
**Rationale:** NegotiationSpec already exists with spec-only status. The termination types are meaningless without compilation. Close the loop now.
**Trade-offs:** Slightly larger scope — adds compiler + CompiledNegotiation + registry integration. Worth it to eliminate the spec-only gap.
**Sources:** NegotiationSpec.java, NegotiationProjection.java, NegotiationCompositeTermination.java
**Exploration:** quick
**Status:** captured

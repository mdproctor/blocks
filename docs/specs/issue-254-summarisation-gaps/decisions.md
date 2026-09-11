## D1: Full scope — all 6 gaps

**Choice:** Implement all 6 gaps as listed in the issue. No runtime/config filtering needed — all are genuine compiler completions or registration wiring.
**Alternatives:**
- Skip keyed grouping (largest gap) — leaves the most impactful gap open, defers the KeyedSummarisationRunner integration
**Rationale:** All 6 gaps are fixable in the existing module structure. Keyed grouping is the only substantial piece; the other 5 are trivial-to-small wiring fixes.
**Trade-offs:** None — this is completion work, not new architecture.
**Sources:** PipelineCompiler.java (UnsupportedOperationException at line 79), SummarisationRecorder.java (3 registered types, 2 missing), SourceDefinition.java, PipelineDefinition.java
**Exploration:** quick
**Status:** captured

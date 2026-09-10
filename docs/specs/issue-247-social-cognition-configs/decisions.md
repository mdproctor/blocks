# Decisions — #247 Social Cognition Configs

## D1: Partial YAML via spec records with @Nullable fields

**Choice:** Create spec records (e.g. `DriveConfigSpec`) with all fields `@Nullable`. A compiler merges non-null values with the runtime config's `defaults()` factory. YAML authors write only the fields they want to override.
**Alternatives:**
- Jackson @JsonCreator with default values — mutates the runtime records with Jackson annotations; couples data types to serialization framework
- Map-based overlay via reflection — no spec records needed but loses type safety and IDE schema support
**Rationale:** Same established pattern as PatternSpec → PatternCompiler. Spec records are purpose-built for YAML deserialization — they don't burden the runtime config types with serialization concerns. victools generates schema from the spec records, showing all available fields with types and descriptions.
**Trade-offs:** 13 spec records mirror 13 config records — boilerplate, but each is small (5-15 fields, all @Nullable). Drift between spec and config is caught by the schema drift test (#243) when it lands.
**Sources:** PatternSpec.java (established pattern), DriveConfig.java (representative config), issue #247 body
**Exploration:** quick
**Status:** captured

## D2: Standalone cognition.yaml file

**Choice:** Configs live in a standalone `cognition.yaml` file discovered on the classpath by the deployment module. Top-level sections map to config types (`drive:`, `mood:`, `narrative:`, etc.). Each section is optional — omitted sections use `defaults()`. The deployment module deserializes and registers compiled configs as CDI beans.
**Alternatives:**
- Nested in PatternSpec under `cognition:` block — implies per-pattern config, but configs are application-scoped (shared across all patterns in the same app)
**Rationale:** Configs are application-scoped, not pattern-scoped. A supervisor and a debate pattern in the same app share the same drive/mood/narrative configs. Standalone file follows the summarisation-yaml precedent (`pipeline.yaml` → `PipelineCompiler`). Deployment module discovery is the established Quarkus pattern.
**Trade-offs:** Separate file from pattern YAML — YAML author manages two files. But the concerns are genuinely different (orchestration topology vs cognitive tuning).
**Sources:** SummarisationYamlProcessor.java (YAML discovery precedent), DriveOrchestrator.java (@ApplicationScoped — application-wide config), issue #247 body
**Exploration:** quick
**Depends on:** D1 (spec records are what the YAML deserializes into)
**Status:** captured

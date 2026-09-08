# Decisions — #165 Agentic Pattern YAML Expansion

## D1: Schema generation approach — victools on spec records

**Choice:** victools reflection on purpose-built spec records, with SchemaModules for reshaping
**Alternatives:**
- Hand-written Jackson deserializers (eidos approach) — simpler, but drifts as Java evolves
- victools on canonical runtime types — produces unusable schema (functional interfaces, pub/sub wiring)
- JavaParser AST walking — engine explored this, moved back to victools reflection
**Rationale:** Spec records ARE the YAML model — purpose-built data types that represent the problem space declaratively. victools reflects on these to generate JSON/YAML schema. SchemaModules (EnumInlining, SealedHierarchy, UnevaluatedProperties from platform's casehub-schema-generator) reshape the output. Same proven approach as engine (CaseDefinition) and neocortex (CbrFeatureSchema).
**Trade-offs:** Requires creating spec records as a new layer between builders and runtime types. More upfront work than hand-written deserializers, but eliminates drift permanently.
**Sources:** engine/generator/CaseHubSchemaGenerator.java, neocortex/schema-generator/CognitiveSchemaGenerator.java, platform casehub-schema-generator, parent/docs/DSL-STYLE-GUIDE.md
**Exploration:** deep-analysis
**Status:** captured

## D2: Shared canonical data model — spec records wrap builders

**Choice:** Introduce spec records (PatternSpec, NegotiationSpec, etc.) as shared data models with string-based strategy references. Builders become sugar over specs. YAML deserializes to specs. Compiler resolves specs to live runtime objects.
**Alternatives:**
- Keep builders and YAML models separate — two independent models that can drift
- Generate YAML model from builder method signatures — builders accept functional interfaces, not serializable
**Rationale:** Engine's CaseDefinition proves this pattern — one model, two pathways (Java builder + YAML), no drift. Builders offer overloaded methods: `.route(new RoundRobinRouting<>())` (typed) or `.route("round-robin")` (string shorthand). Both populate the same spec. Java DSL is not damaged — public API stays identical.
**Trade-offs:** Requires refactoring builder internals (fields change from live SPI instances to spec references). Public API unchanged. Lambdas stay Java-only.
**Sources:** engine/api/model/CaseDefinition.java (Builder inner class), blocks agentic/pattern/AbstractPatternBuilder.java
**Exploration:** deep-analysis
**Depends on:** D1 (spec records are what victools reflects on)
**Status:** captured

## D3: Expression compilation — MVEL3 LambdaCatalog, one-directional YAML→Java

**Choice:** MVEL3 expressions in YAML for predicate-accepting fields. Compilation direction is YAML→Java only. No lambda capture or roundtripping. jq also supported.
**Alternatives:**
- SerializedLambda (SWF approach) — JVM method reference dump, not human-readable, requires capturing class on classpath
- JavaParser compile-time lambda capture — complex, fragile across refactors
- Expression strings only (no catalog) — no dedup, no indexing
**Rationale:** MVEL3's LambdaCatalog provides dedup (identical expressions share one compiled class), indexing (catalog knows every expression and where it's used), and batch compilation (MVELBatchCompiler compiles all unique expressions in one javac call). Blocks already uses ExpressionEngine SPI with MvelExpressionEngine (ThresholdClassifySummariser, PhaseDetectSummariser). This eliminates the "partially YAML-expressible" category — all predicate-accepting patterns become fully YAML-expressible.
**Trade-offs:** Java developers who want YAML-serializable patterns use expression strings. Pure lambda users stay on the Java-only path. Two syntaxes for the same thing — but the DSL-STYLE-GUIDE explicitly calls these "peer representations."
**Sources:** /Users/mdproctor/dev/mvel (LambdaCatalog, MVELBatchCompiler, LambdaRegistryStore), /Users/mdproctor/dev/swf-sdk-java (SerializedLambda approach — rejected), platform MvelExpressionEngine
**Exploration:** deep-analysis
**Depends on:** D2 (spec records hold expression strings)
**Status:** captured

## D4: Dual compilation modes — compile-time + runtime

**Choice:** Both compile-time (Quarkus deployment module, classpath YAML → bytecode) and runtime (dynamic loading, tenant-specific, hot-reload). Same LambdaCatalog, two entry points.
**Alternatives:**
- Compile-time only — no dynamic YAML loading
- Runtime only — no build-time validation, startup cost
**Rationale:** Compile-time gives build-time validation and zero-overhead bytecode for classpath YAML. Runtime is always needed for dynamic pipeline loading (tenant-specific, hot-reload). Both share the same LambdaCatalog. Extends existing SummarisationYamlProcessor deployment module pattern.
**Trade-offs:** Two code paths to maintain. Compile-time path requires deployment module work.
**Sources:** blocks summarisation-yaml-deployment/SummarisationYamlProcessor, MVEL3 MVELBatchCompiler + LambdaRegistryStore
**Exploration:** quick
**Depends on:** D3 (expression compilation approach)
**Status:** captured

## D5: Strategy registries — type:name resolution

**Choice:** Each SPI with concrete named implementations gets a registry mapping `type: name` → implementation. Same pattern as summarisation-yaml's SummariserRegistry.
**Alternatives:**
- CDI discovery only — less explicit, harder for YAML authors to know what's available
- Reflection-based discovery — fragile, no static analysis
**Rationale:** Proven pattern from summarisation-yaml. Strategy name is the bridge between YAML declaration and Java implementation. Registry is populated at build time (deployment module) or runtime (dynamic loading). The pattern-to-YAML mapping table from the typescript-programming-model spec defines the canonical names.
**Trade-offs:** Registry must be maintained as new implementations are added. CDI discovery auto-populates from classpath.
**Sources:** blocks summarisation-yaml SummariserRegistry, specs/main/2026-08-23-typescript-programming-model-design.md (pattern-to-YAML mapping table)
**Exploration:** quick
**Depends on:** D2 (spec records reference strategies by name)
**Status:** captured

## D6: Annotation-driven spec record generation

**Choice:** Annotate SPI implementations with `@YamlSpec(name = "round-robin")`. A Quarkus deployment step (Jandex scan) generates spec records, sealed interface hierarchies with `@JsonTypeInfo`/`@JsonSubTypes`, and registry registrations. ~35 simple strategies are generated; ~5 complex shapes (HTN, Conditional, etc.) are hand-written.
**Alternatives:**
- Hand-write all ~40 spec records — busywork, drifts when implementations evolve
- Annotation processing (standard Java AP) — doesn't have Jandex, misses CDI context
**Rationale:** Blocks already has `annotations/deployment/PatternAnnotationStep` — the Quarkus deployment extension pattern is established. The deployment step reads constructor parameters via Jandex: primitives/strings/enums → direct fields, functional interfaces → `@Nullable String` MVEL expression fields, SPI references → nested spec references. Keeps generated specs in sync automatically as implementations are added or modified.
**Trade-offs:** Complex YAML shapes (recursive task trees, branching) still need hand-written specs. Adds deployment-step complexity. Generated code must be inspectable for debugging.
**Sources:** blocks annotations/deployment/PatternAnnotationStep, blocks summarisation-yaml-deployment/SummarisationYamlProcessor
**Exploration:** quick
**Depends on:** D1 (generated specs are what victools reflects on), D5 (generated registries)
**Status:** captured

## D7: Drift detection for hand-written specs

**Choice:** Every implementation gets `@YamlSpec` — even complex ones. The deployment step generates a baseline field set from the constructor. A compile-time test verifies hand-written specs cover every baseline field. If someone adds a constructor parameter but forgets to update the spec, the test fails.
**Alternatives:**
- No drift detection — hand-written specs rot silently
- Schema-only drift (victools output vs committed schema) — catches schema drift but not field omissions
**Rationale:** Generated baseline fields ⊆ hand-written spec fields → pass. Missing field → fail. This catches the most common drift: a new parameter added to the implementation without updating the spec. Combined with the schema drift test (victools output vs committed schema), both structural and content drift are detected.
**Trade-offs:** Requires all implementations to be annotated, even hand-written ones. Small test maintenance cost.
**Sources:** engine/generator/SchemaDriftTest.java (schema drift precedent)
**Exploration:** quick
**Depends on:** D6 (annotation-driven generation provides the baseline)
**Status:** captured

## D8: ShorthandModule for YAML ergonomics

**Choice:** Use platform's ShorthandModule (being promoted from neocortex) to enable scalar-or-object shorthand for zero-config strategies. `routing: round-robin` is equivalent to `routing: { type: round-robin }`.
**Alternatives:**
- No shorthand — always require object form with `type:` discriminator
**Rationale:** Many strategies have zero config (RoundRobinRouting, PassThrough, CollectAll, OnExplicitDispatch, etc.). Requiring `{ type: round-robin }` for these is verbose. ShorthandModule generates oneOf(string, object) schema — scalar resolves to the zero-config variant. Better YAML ergonomics for the common case.
**Trade-offs:** Slightly more complex schema (oneOf). Deserialization needs custom handling for scalar-or-object. ShorthandModule handles this.
**Sources:** neocortex/schema-generator/ShorthandModule.java (being promoted to platform)
**Exploration:** quick
**Depends on:** D1 (ShorthandModule is a victools schema module)
**Status:** captured

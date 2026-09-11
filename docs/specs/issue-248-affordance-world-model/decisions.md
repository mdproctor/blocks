# Decisions — #248 Affordance World Model YAML Surface

## D1: Standalone world.yaml

**Choice:** Standalone `world.yaml` file discovered at build time
**Alternatives:**
- Embedded in pattern YAML — tight coupling, simpler for small cases but not reusable
- Both standalone + inline overrides — most flexible but most complex
**Rationale:** Consistent with `cognition.yaml` pattern. World model is application-scoped, not pattern-scoped — same entities and action vocabulary are reused across patterns.
**Trade-offs:** Can't inline a quick world model inside a pattern spec without a separate file.
**Sources:** CognitionDefinition.java, SummarisationYamlProcessor.java
**Exploration:** quick
**Status:** captured

## D2: Entity referencing — both inline and top-level

**Choice:** Support both top-level entity map (keyed by ID for reuse) and inline entity declarations in sections
**Alternatives:**
- Top-level only — forces all entities into a separate map even for one-off use
- Inline only — duplicates entities used in multiple sections
**Rationale:** Common YAML pattern (OpenAPI $ref vs inline, Kubernetes). Top-level for shared entities, inline for section-specific ones. Compiler resolves refs against the top-level map.
**Trade-offs:** Compiler must handle two paths (ref resolution + inline). ID collision between top-level and inline is an error.
**Sources:** ObservableEntity.java, ObservationSection.EntityGroup
**Exploration:** quick
**Status:** captured

## D3: AnnotatedSection as inline properties

**Choice:** Add optional requiredTags/resolutions/interpretiveFrame as properties on every section spec variant. Compiler wraps in AnnotatedSection when any annotation property is present.
**Alternatives:**
- Explicit wrapper type (`type: annotated` with nested `section:`) — matches Java shape but adds nesting depth for every gated section
**Rationale:** The decorator pattern is a Java implementation constraint (sealed interfaces can't add optional fields to variants). YAML has no such constraint. Ungated sections have zero overhead — annotation fields are simply absent.
**Trade-offs:** Each section spec variant carries three extra @Nullable fields. Marginal — records are cheap.
**Sources:** AnnotatedSection.java, ResolutionTier.java
**Exploration:** quick
**Status:** captured

## D4: Named filter registry for pipeline

**Choice:** Pipeline is a list of named filter types with a registry. Built-in: `"perception"` → PerceptionFilter. Consumer-extensible.
**Alternatives:**
- Perception-only shorthand (`pipeline: true`) — simpler but paints into a corner when more filters arrive
- Skip pipeline — defer until a second filter type exists
**Rationale:** Follows SummariserRegistry pattern. PerceptionFilter is the only built-in today, but the SPI is designed for extensibility. Registry costs nothing and avoids a breaking YAML change later.
**Trade-offs:** Registry infrastructure for a single built-in type. Acceptable — the infrastructure is small and proven.
**Sources:** SummariserRegistry.java, ObservationFilter.java, PerceptionFilter.java, ObservationPipeline.java
**Exploration:** quick
**Status:** captured

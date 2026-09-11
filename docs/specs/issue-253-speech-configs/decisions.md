## D1: Scope — 5 config-shaped records only

**Choice:** Scope #253 to 5 config-shaped records: TranscriptionOptions, SynthesisOptions, SherpaConfig, KokoroConfig, Audio8Config. Drop 7 types that are runtime data, already YAML-configured, utility classes, or misidentified.
**Alternatives:**
- All 12 types as listed in issue — would build specs for runtime data records (ConversationTurn, AssembledPrompt, PromptContext), an already-YAML-configured Quarkus @ConfigMapping (AvatarConfig), a hardcoded utility class (VisemeMapping), a filesystem-derived heavy type (GectorConfig), and a misidentified @FunctionalInterface (CorrectionStrategy)
**Rationale:** Same principle as D1 in #252 — YAML surface types must be author-time knowable, compilable, and choice-bearing. ConversationTurn/AssembledPrompt/PromptContext are runtime data. AvatarConfig is already @ConfigMapping (Quarkus YAML). VisemeMapping is a static utility. CorrectionStrategy is a @FunctionalInterface, not the enum the issue describes. GectorConfig reads tagVocabulary and verbDictionary from files — filesystem-derived, not hand-authored.
**Trade-offs:** Coverage matrix won't show all 12 types as Done — 7 marked N/A. GectorConfig deferred to a future issue if YAML-authored grammar correction config is wanted.
**Sources:** TranscriptionOptions.java, SynthesisOptions.java, CorrectionStrategy.java, ConversationTurn.java, AssembledPrompt.java, PromptContext.java, AvatarConfig.java, VisemeMapping.java, SherpaConfig.java, KokoroConfig.java, Audio8Config.java, GectorConfig.java — field analysis
**Exploration:** quick
**Status:** captured

## D2: Speech module dependencies — provided scope

**Choice:** Add speech-api and speech-sherpa as provided-scope dependencies to agentic-yaml. Specs and registries co-locate in agentic-yaml.
**Alternatives:**
- Specs only, no registries — consumers construct runtime types themselves. Breaks the established registry pattern.
- Separate speech-yaml modules — maximum isolation but adds Maven modules for little benefit
**Rationale:** Matches the existing pattern (platform-agent-api is already a provided dep). Consumers who use speech + YAML have both modules on classpath. Registries can construct runtime types (Path.of for SherpaConfig, KokoroConfig, Audio8Config).
**Trade-offs:** agentic-yaml gains two provided deps. Acceptable — they're sibling modules in the same parent.
**Sources:** agentic-yaml/pom.xml (existing provided deps pattern)
**Exploration:** quick
**Status:** captured

## D3: Spec categories for the 5 types

**Choice:** Two categories: (A) direct reuse for TranscriptionOptions and SynthesisOptions — already Path-free Jackson-compatible records; (B) adapted record specs for SherpaConfig, KokoroConfig, Audio8Config — Path fields become String, registries call Path.of().
**Alternatives:**
- Adapted records for all 5 — unnecessary for TranscriptionOptions/SynthesisOptions which are already String-based
- Single uniform spec pattern — doesn't respect the structural differences
**Rationale:** TranscriptionOptions and SynthesisOptions have only String/boolean fields. No adaptation needed. The 3 sherpa configs use Path which YAML authors specify as strings.
**Trade-offs:** Two patterns instead of one. Justified — same rationale as #252 D2.
**Depends on:** D2 (provided deps enable direct reuse of speech-api types)
**Sources:** TranscriptionOptions.java (4 String fields), SynthesisOptions.java (3 String + 1 boolean), SherpaConfig.java (Path fields), KokoroConfig.java (Path fields), Audio8Config.java (Path fields)
**Exploration:** quick
**Status:** captured

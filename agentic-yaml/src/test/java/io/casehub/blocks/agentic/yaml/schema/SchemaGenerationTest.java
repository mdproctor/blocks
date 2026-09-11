package io.casehub.blocks.agentic.yaml.schema;

import com.fasterxml.jackson.databind.JsonNode;
import io.casehub.blocks.agentic.yaml.spec.CbrOutcomeWeightsSpec;
import io.casehub.blocks.agentic.yaml.spec.CoordinationOutcomeWeightsSpec;
import io.casehub.blocks.agentic.yaml.spec.PatternSpec;
import io.casehub.blocks.agentic.yaml.spec.RiskDecisionSpec;
import io.casehub.blocks.agentic.yaml.spec.RoutingSpec;
import io.casehub.blocks.agentic.yaml.spec.TerminationSpec;
import io.casehub.blocks.agentic.yaml.spec.Audio8ConfigSpec;
import io.casehub.blocks.agentic.yaml.spec.KokoroConfigSpec;
import io.casehub.blocks.agentic.yaml.spec.SherpaConfigSpec;
import io.casehub.blocks.agentic.yaml.spec.TrustRoutingPolicyKeysSpec;
import io.casehub.blocks.routing.agent.DispositionProfile;
import io.casehub.blocks.speech.SynthesisOptions;
import io.casehub.blocks.speech.TranscriptionOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaGenerationTest {

    private BlocksSchemaGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new BlocksSchemaGenerator();
    }

    @Test
    void routingSpecSchemaHasDiscriminator() {
        JsonNode schema = generator.generate(RoutingSpec.class);
        assertThat(schema).isNotNull();
        var schemaText = schema.toString();
        assertThat(schemaText).contains("first-match");
        assertThat(schemaText).contains("round-robin");
        assertThat(schemaText).contains("select-all");
    }

    @Test
    void terminationSpecSchemaHasAllTypes() {
        JsonNode schema = generator.generate(TerminationSpec.class);
        assertThat(schema).isNotNull();
        var schemaText = schema.toString();
        assertThat(schemaText).contains("max-iterations");
        assertThat(schemaText).contains("single-pass");
        assertThat(schemaText).contains("agent-count");
    }

    @Test
    void patternSpecSchemaHasAllTopologies() {
        JsonNode schema = generator.generate(PatternSpec.class);
        assertThat(schema).isNotNull();
        var schemaText = schema.toString();
        assertThat(schemaText).contains("supervisor");
        assertThat(schemaText).contains("debate");
        assertThat(schemaText).contains("loop");
        assertThat(schemaText).contains("parallel");
        assertThat(schemaText).contains("voting");
        assertThat(schemaText).contains("conditional");
        assertThat(schemaText).contains("sequence");
        assertThat(schemaText).contains("htn");
    }

    @Test
    void patternSpecSchemaHasAgentsField() {
        JsonNode schema = generator.generate(PatternSpec.class);
        assertThat(schema.toString()).contains("agents");
    }

    @Test
    void promptOptimiserSpecSchemaHasTypes() {
        JsonNode schema = generator.generate(io.casehub.blocks.agentic.yaml.spec.PromptOptimiserSpec.class);
        assertThat(schema).isNotNull();
        var schemaText = schema.toString();
        assertThat(schemaText).contains("few-shot");
        assertThat(schemaText).contains("instruction");
    }

    @Test
    void confidenceScorerSpecSchemaHasTypes() {
        JsonNode schema = generator.generate(io.casehub.blocks.agentic.yaml.spec.ConfidenceScorerSpec.class);
        assertThat(schema).isNotNull();
        var schemaText = schema.toString();
        assertThat(schemaText).contains("arousal");
        assertThat(schemaText).contains("surprise");
        assertThat(schemaText).contains("composite");
    }

    @Test
    void promptOptimisationDefinitionSchemaHasPipelines() {
        JsonNode schema = generator.generate(io.casehub.blocks.agentic.yaml.spec.PromptOptimisationDefinition.class);
        assertThat(schema).isNotNull();
        var schemaText = schema.toString();
        assertThat(schemaText).contains("pipelines");
    }

    @Test
    void riskDecisionSpecSchemaHasTypes() {
        JsonNode schema = generator.generate(RiskDecisionSpec.class);
        assertThat(schema).isNotNull();
        var schemaText = schema.toString();
        assertThat(schemaText).contains("autonomous");
        assertThat(schemaText).contains("gate-required");
    }

    @Test
    void cbrOutcomeWeightsSpecSchemaGenerates() {
        JsonNode schema = generator.generate(CbrOutcomeWeightsSpec.class);
        assertThat(schema).isNotNull();
        assertThat(schema.toString()).contains("weights");
    }

    @Test
    void coordinationOutcomeWeightsSpecSchemaGenerates() {
        JsonNode schema = generator.generate(CoordinationOutcomeWeightsSpec.class);
        assertThat(schema).isNotNull();
        assertThat(schema.toString()).contains("weights");
    }

    @Test
    void trustRoutingPolicyKeysSpecSchemaGenerates() {
        JsonNode schema = generator.generate(TrustRoutingPolicyKeysSpec.class);
        assertThat(schema).isNotNull();
        assertThat(schema.toString()).contains("scopePrefix");
    }

    @Test
    void dispositionProfileSchemaGenerates() {
        JsonNode schema = generator.generate(DispositionProfile.class);
        assertThat(schema).isNotNull();
        assertThat(schema.toString()).contains("desired");
    }

    @Test
    void sherpaConfigSpecSchemaGenerates() {
        JsonNode schema = generator.generate(SherpaConfigSpec.class);
        assertThat(schema).isNotNull();
        assertThat(schema.toString()).contains("modelDir");
    }

    @Test
    void kokoroConfigSpecSchemaGenerates() {
        JsonNode schema = generator.generate(KokoroConfigSpec.class);
        assertThat(schema).isNotNull();
        assertThat(schema.toString()).contains("modelDir");
    }

    @Test
    void audio8ConfigSpecSchemaGenerates() {
        JsonNode schema = generator.generate(Audio8ConfigSpec.class);
        assertThat(schema).isNotNull();
        assertThat(schema.toString()).contains("variant");
    }

    @Test
    void transcriptionOptionsSchemaGenerates() {
        JsonNode schema = generator.generate(TranscriptionOptions.class);
        assertThat(schema).isNotNull();
        assertThat(schema.toString()).contains("audioFormat");
    }

    @Test
    void synthesisOptionsSchemaGenerates() {
        JsonNode schema = generator.generate(SynthesisOptions.class);
        assertThat(schema).isNotNull();
        assertThat(schema.toString()).contains("voice");
    }

}

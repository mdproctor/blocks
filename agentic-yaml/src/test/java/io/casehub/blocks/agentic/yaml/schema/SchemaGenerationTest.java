package io.casehub.blocks.agentic.yaml.schema;

import com.fasterxml.jackson.databind.JsonNode;
import io.casehub.blocks.agentic.yaml.spec.PatternSpec;
import io.casehub.blocks.agentic.yaml.spec.RoutingSpec;
import io.casehub.blocks.agentic.yaml.spec.TerminationSpec;
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

}

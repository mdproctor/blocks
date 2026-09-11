package io.casehub.blocks.agentic.yaml.examples;

import io.casehub.blocks.agentic.FailurePolicy;
import io.casehub.blocks.agentic.aggregation.CollectAll;
import io.casehub.blocks.agentic.judgment.JudgmentPolicy;
import io.casehub.blocks.agentic.model.PatternType;
import io.casehub.blocks.agentic.routing.FirstMatchRouting;
import io.casehub.blocks.agentic.social.drive.DriveAxis;
import io.casehub.blocks.agentic.yaml.compiler.NegotiationCompiler;
import io.casehub.blocks.agentic.yaml.registry.ConflictResolutionRegistry;
import io.casehub.blocks.agentic.yaml.registry.TerminationConditionRegistry;
import io.casehub.blocks.agentic.yaml.spec.ConflictResolutionSpec;
import io.casehub.blocks.agentic.yaml.spec.NegotiationSpec;
import io.casehub.blocks.negotiation.AcceptancePolicy;
import io.casehub.blocks.normative.MostRestrictiveResolution;
import io.casehub.blocks.summarisation.Summariser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SupplyChainOpsExampleTest extends ExampleTestBase {

    private static final String SCENARIO = "supply-chain-ops";

    @Test
    void pipelineCompiles() throws IOException {
        summariserRegistry.register("field-extract", config ->
                Summariser.ofSync(batch -> batch.stream()
                        .map(e -> Map.of("extracted", e.payload()))
                        .toList()));

        var def = loadPipeline(SCENARIO);
        var pipeline = pipelineCompiler.<Object>compile(def, summariserRegistry, null, pipelineExpressionEngine);

        assertThat(pipeline.name()).isEqualTo("supply-chain-monitoring");
        assertThat(def.levels()).hasSize(4);
        assertThat(def.levels().get(0).name()).isEqualTo("detection");
        assertThat(def.levels().get(0).summariser().type()).isEqualTo("threshold-classify");
        assertThat(def.levels().get(1).name()).isEqualTo("triage");
        assertThat(def.levels().get(1).summariser().type()).isEqualTo("phase-detect");
        assertThat(def.levels().get(2).name()).isEqualTo("response");
        assertThat(def.levels().get(2).summariser().type()).isEqualTo("count");
        assertThat(def.levels().get(3).name()).isEqualTo("resolution");
        assertThat(def.levels().get(3).summariser().type()).isEqualTo("field-extract");
    }

    @Test
    void patternCompiles() throws IOException {
        var spec = loadPattern(SCENARIO);
        var model = patternCompiler.compile(spec);

        assertThat(model.patternType()).isEqualTo(PatternType.SUPERVISOR);
        assertThat(model.routing()).isInstanceOf(FirstMatchRouting.class);
        assertThat(model.aggregation()).isInstanceOf(CollectAll.class);
        assertThat(model.failurePolicy().onDeadlock())
                .isEqualTo(FailurePolicy.AggregationFailureAction.ESCALATE);

        var candidates = model.candidateSupplier().get();
        assertThat(candidates).hasSize(4);
        assertThat(candidates.get(0).descriptor().name()).isEqualTo("anomaly-detector");
        assertThat(candidates.get(1).descriptor().name()).isEqualTo("triage-coordinator");
        assertThat(candidates.get(2).descriptor().name()).isEqualTo("response-planner");
        assertThat(candidates.get(3).descriptor().name()).isEqualTo("resolution-tracker");

        assertThat(model.judgment()).isNotNull();
        assertThat(model.judgment()).isInstanceOf(JudgmentPolicy.class);
    }

    @Test
    void cognitionCompiles() throws IOException {
        var def = loadCognition(SCENARIO);
        var compiled = cognitionCompiler.compile(def);

        assertThat(compiled.drive().axisWeights().get(DriveAxis.COMPETENCE)).isEqualTo(1.8);
        assertThat(compiled.userModel().minSignalsForSynthesis()).isEqualTo(5);
        assertThat(compiled.goalProposal().maxDriveGoals()).isEqualTo(3);
        assertThat(compiled.goalEscalation().escalationCycles()).isEqualTo(3);
        assertThat(compiled.normDetection().establishedThreshold()).isEqualTo(0.8);
        assertThat(compiled.collectiveGoal().alignmentThreshold()).isEqualTo(0.7);
        assertThat(compiled.narrative().maxEpisodes()).isEqualTo(80);
    }

    @Test
    void negotiationCompiles() throws IOException {
        var spec = load(SCENARIO, "negotiation.yaml", NegotiationSpec.class);
        var compiler = new NegotiationCompiler(new TerminationConditionRegistry(), null);
        var compiled = compiler.compile(spec);

        assertThat(compiled.parties()).containsExactlyInAnyOrder(
                "response-planner", "triage-coordinator", "resolution-tracker");
        assertThat(compiled.projection()).isNotNull();
        assertThat(compiled.termination()).isNotNull();
    }

    @Test
    void conflictResolutionResolves() throws IOException {
        var spec = load(SCENARIO, "conflict-resolution.yaml", ConflictResolutionSpec.class);
        var registry = new ConflictResolutionRegistry();
        var strategy = registry.resolve(spec);

        assertThat(strategy).isInstanceOf(MostRestrictiveResolution.class);
    }
}

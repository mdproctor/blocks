package io.casehub.blocks.agentic.yaml.examples;

import io.casehub.blocks.agentic.FailurePolicy;
import io.casehub.blocks.agentic.aggregation.CollectAll;
import io.casehub.blocks.agentic.judgment.JudgmentPolicy;
import io.casehub.blocks.agentic.model.PatternType;
import io.casehub.blocks.agentic.routing.FirstMatchRouting;
import io.casehub.blocks.agentic.social.drive.DriveAxis;
import io.casehub.blocks.agentic.social.drive.DriveConfig;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class FleetLogisticsExampleTest extends ExampleTestBase {

    private static final String SCENARIO = "fleet-logistics";

    @Test
    void pipelineCompiles() throws IOException {
        var def = loadPipeline(SCENARIO);
        var pipeline = pipelineCompiler.<Object>compile(def, summariserRegistry, null, pipelineExpressionEngine);

        assertThat(pipeline.name()).isEqualTo("fleet-monitoring");
        assertThat(def.levels()).hasSize(4);
        assertThat(def.levels().get(0).name()).isEqualTo("per-vehicle");
        assertThat(def.levels().get(1).name()).isEqualTo("per-route");
        assertThat(def.levels().get(2).name()).isEqualTo("route-health");
        assertThat(def.levels().get(3).name()).isEqualTo("fleet-status");
    }

    @Test
    void patternCompiles() throws IOException {
        var spec = loadPattern(SCENARIO);
        var model = patternCompiler.compile(spec);

        assertThat(model.patternType()).isEqualTo(PatternType.SUPERVISOR);
        assertThat(model.routing()).isInstanceOf(FirstMatchRouting.class);
        assertThat(model.aggregation()).isInstanceOf(CollectAll.class);
        assertThat(model.failurePolicy().onRoutingFailure())
                .isEqualTo(FailurePolicy.RoutingFailureAction.RETRY_BROADER);
        assertThat(model.failurePolicy().onDeadlock())
                .isEqualTo(FailurePolicy.AggregationFailureAction.ESCALATE);

        var candidates = model.candidateSupplier().get();
        assertThat(candidates).hasSize(3);
        assertThat(candidates.get(0).descriptor().name()).isEqualTo("route-analyst");
        assertThat(candidates.get(1).descriptor().name()).isEqualTo("dispatcher");
        assertThat(candidates.get(2).descriptor().name()).isEqualTo("reporter");
        assertThat(candidates.get(0).descriptor().capabilities()).hasSize(2);

        assertThat(model.judgment()).isNotNull();
        assertThat(model.judgment()).isInstanceOf(JudgmentPolicy.class);
    }

    @Test
    void cognitionCompiles() throws IOException {
        var def = loadCognition(SCENARIO);
        var compiled = cognitionCompiler.compile(def);

        assertThat(compiled.drive().axisWeights().get(DriveAxis.COMPETENCE)).isEqualTo(1.8);
        assertThat(compiled.drive().changeThreshold()).isEqualTo(0.12);
        assertThat(compiled.mood().maxDisplacement()).isEqualTo(0.5);
        assertThat(compiled.mood().baseline().pleasure()).isEqualTo(0.4);
        assertThat(compiled.narrative().maxEpisodes()).isEqualTo(75);
        assertThat(compiled.retention().recencyWeight()).isEqualTo(0.7);
    }
}

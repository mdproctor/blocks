package io.casehub.blocks.agentic.yaml.registry;

import io.casehub.blocks.agentic.termination.GoalReached;
import io.casehub.blocks.agentic.yaml.spec.TerminationSpec;
import io.casehub.platform.expression.MvelExpressionEngine;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TerminationConditionRegistryTest {

    private final TerminationConditionRegistry registry = new TerminationConditionRegistry();
    private final MvelExpressionEngine engine = new MvelExpressionEngine();

    @Test
    void goalReachedCompilesAndEvaluates() {
        var spec = new TerminationSpec.GoalReached("score > 0.9");
        var condition = registry.resolve(spec, engine);
        assertThat(condition).isInstanceOf(GoalReached.class);
    }

    @Test
    void goalReachedThrowsWithoutEngine() {
        var spec = new TerminationSpec.GoalReached("score > 0.9");
        assertThatThrownBy(() -> registry.resolve(spec, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ExpressionEngine");
    }
}

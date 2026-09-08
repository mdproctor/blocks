package io.casehub.blocks.agentic.yaml.registry;

import io.casehub.blocks.agentic.judgment.AlwaysYield;
import io.casehub.blocks.agentic.judgment.ConfidenceThreshold;
import io.casehub.blocks.agentic.judgment.IterationBased;
import io.casehub.blocks.agentic.judgment.JudgmentTrigger;
import io.casehub.blocks.agentic.judgment.NeverYield;
import io.casehub.blocks.agentic.yaml.spec.JudgmentSpec.TriggerSpec;
import io.casehub.platform.api.expression.ExpressionEngine;
import org.jspecify.annotations.Nullable;

import java.util.Map;

public class JudgmentTriggerRegistry {

    @SuppressWarnings("unchecked")
    public <T> JudgmentTrigger<T> resolve(TriggerSpec spec,
                                           @Nullable ExpressionEngine engine) {
        return switch (spec) {
            case TriggerSpec.AlwaysYield ay -> new AlwaysYield<>();
            case TriggerSpec.NeverYield ny -> new NeverYield<>();
            case TriggerSpec.IterationBased ib -> new IterationBased<>(ib.every());
            case TriggerSpec.ConfidenceThreshold ct ->
                    throw new UnsupportedOperationException(
                            "confidence-threshold requires runtime expression compilation — " +
                            "use CDI-provided ExpressionEngine");
        };
    }
}

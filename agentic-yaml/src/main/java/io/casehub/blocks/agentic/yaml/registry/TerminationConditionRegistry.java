package io.casehub.blocks.agentic.yaml.registry;

import io.casehub.blocks.agentic.termination.MaxIterationsTermination;
import io.casehub.blocks.agentic.termination.TerminationCondition;
import io.casehub.blocks.agentic.yaml.spec.TerminationSpec;
import io.casehub.blocks.conversation.orchestration.AllAgreedTermination;
import io.casehub.blocks.conversation.orchestration.ContestedEscalation;
import io.casehub.blocks.conversation.orchestration.SupervisorTermination;
import io.casehub.platform.api.expression.ExpressionEngine;
import org.jspecify.annotations.Nullable;

import java.util.Set;

public class TerminationConditionRegistry {

    @SuppressWarnings("unchecked")
    public <T> TerminationCondition<T> resolve(TerminationSpec spec,
                                                @Nullable ExpressionEngine engine) {
        return switch (spec) {
            case TerminationSpec.MaxIterations mi ->
                    new MaxIterationsTermination<>(mi.iterations());
            case TerminationSpec.GoalReached gr ->
                    throw new UnsupportedOperationException(
                            "goal-reached requires runtime expression compilation — " +
                            "use PatternCompiler with CDI-provided ExpressionEngine");
            case TerminationSpec.JudgeConvergence jc ->
                    throw new UnsupportedOperationException(
                            "judge-convergence resolved by PatternCompiler (needs agent ref)");
            case TerminationSpec.AllAgreed aa ->
                    (TerminationCondition<T>) new AllAgreedTermination(
                            aa.resolvedStatuses() != null ? aa.resolvedStatuses()
                                    : Set.of("RESOLVED", "ACCEPTED"));
            case TerminationSpec.Supervisor sv ->
                    sv.signalType() != null
                            ? (TerminationCondition<T>) new SupervisorTermination(sv.role(), sv.signalType())
                            : (TerminationCondition<T>) new SupervisorTermination(sv.role());
            case TerminationSpec.Contested ct ->
                    (TerminationCondition<T>) new ContestedEscalation(ct.maxDisputeRounds());
            case TerminationSpec.Convergence cv ->
                    throw new UnsupportedOperationException(
                            "convergence termination requires state extraction functions");
            case TerminationSpec.SinglePass sp -> new MaxIterationsTermination<>(1);
            case TerminationSpec.AgentCount ac ->
                    throw new UnsupportedOperationException(
                            "agent-count resolved by PatternCompiler (needs agent list)");
        };
    }
}

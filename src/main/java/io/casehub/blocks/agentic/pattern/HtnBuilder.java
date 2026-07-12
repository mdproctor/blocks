package io.casehub.blocks.agentic.pattern;

import io.casehub.blocks.agentic.RoutingCandidate;
import io.casehub.blocks.agentic.activation.OnExplicitDispatch;
import io.casehub.blocks.agentic.aggregation.CollectAll;
import io.casehub.blocks.agentic.decomposition.DecompositionContext;
import io.casehub.blocks.agentic.decomposition.StaticDecomposition;
import io.casehub.blocks.agentic.decomposition.TaskNode;
import io.casehub.blocks.agentic.model.ExecutionModel;
import io.casehub.blocks.agentic.model.ExecutionResult;
import io.casehub.blocks.agentic.model.OrchestratedDriver;
import io.casehub.blocks.agentic.plan.ExecutionPlan;
import io.casehub.blocks.agentic.routing.SequentialRouting;
import io.casehub.blocks.agentic.termination.TerminationCondition;
import io.casehub.blocks.agentic.termination.TerminationDecision;
import io.smallrye.mutiny.Uni;

import java.util.List;

public class HtnBuilder<T> extends AbstractPatternBuilder<T, HtnBuilder<T>> {

    private TaskNode<T> rootTask;

    public HtnBuilder() {
        this.task = "htn";
        this.routing = new SequentialRouting<>();
        this.decomposition = new StaticDecomposition<>();
        this.activation = new OnExplicitDispatch<>();
        this.aggregation = new CollectAll<>();
        this.termination = ctx -> Uni.createFrom().item(
                ctx.iterationCount() >= 1
                        ? new TerminationDecision.Complete(ctx.results())
                        : TerminationDecision.Continue.INSTANCE);
    }

    public HtnBuilder<T> rootTask(TaskNode<T> rootTask) {
        this.rootTask = rootTask;
        return this;
    }

    @Override
    public Uni<ExecutionResult> execute(T initialContext) {
        if (rootTask == null) {
            throw new IllegalStateException("rootTask must be set before execute()");
        }

        return flatten(rootTask, initialContext)
                .map(plan -> {
                    var sortedNodes = plan.topologicalSort();
                    var agents = sortedNodes.stream()
                            .map(n -> new RoutingCandidate(n.task().agent(), null))
                            .toList();

                    var localTermination = (TerminationCondition<T>) ctx -> Uni.createFrom().item(
                            ctx.iterationCount() >= agents.size()
                                    ? new TerminationDecision.Complete(ctx.results())
                                    : TerminationDecision.Continue.INSTANCE);

                    var localModel = new ExecutionModel<>(
                            routing,
                            decomposition,
                            activation,
                            aggregation,
                            localTermination,
                            () -> agents,
                            failurePolicy,
                            listeners,
                            task
                    );

                    return localModel;
                })
                .flatMap(localModel -> new OrchestratedDriver<T>().execute(localModel, initialContext));
    }

    private Uni<ExecutionPlan<T>> flatten(TaskNode<T> node, T state) {
        return switch (node) {
            case TaskNode.LeafTask<T> leaf -> Uni.createFrom().item(ExecutionPlan.singleton(leaf));

            case TaskNode.CompoundTask<T> compound -> {
                var matchingMethod = compound.methods().stream()
                        .filter(m -> m.guard().test(state))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException(
                                "No decomposition method guard matched for task: " + compound.name()));

                var ctx = new DecompositionContext<>(state, List.of(), 0);
                yield matchingMethod.strategy()
                        .decompose(compound, ctx);
            }
        };
    }
}

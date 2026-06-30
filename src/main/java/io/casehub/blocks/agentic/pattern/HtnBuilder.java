package io.casehub.blocks.agentic.pattern;

import io.casehub.blocks.agentic.activation.OnExplicitDispatch;
import io.casehub.blocks.agentic.aggregation.PassThrough;
import io.casehub.blocks.agentic.decomposition.StaticDecomposition;
import io.casehub.blocks.agentic.decomposition.TaskNode;
import io.casehub.blocks.agentic.routing.FirstMatchRouting;
import io.casehub.blocks.agentic.termination.TerminationDecision;
import io.smallrye.mutiny.Uni;

public class HtnBuilder<T> extends AbstractPatternBuilder<T, HtnBuilder<T>> {

    private TaskNode<T> rootTask;

    public HtnBuilder() {
        this.routing = new FirstMatchRouting<>(c -> true);
        this.decomposition = new StaticDecomposition<>();
        this.activation = new OnExplicitDispatch<>();
        this.aggregation = new PassThrough<>();
        this.termination = ctx -> Uni.createFrom().item(
                ctx.iterationCount() >= 1
                        ? new TerminationDecision.Complete(ctx.results())
                        : TerminationDecision.Continue.INSTANCE);
    }

    public HtnBuilder<T> task(TaskNode<T> rootTask) {
        this.rootTask = rootTask;
        return this;
    }
}

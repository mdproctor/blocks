package io.casehub.blocks.agentic.pattern;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.activation.OnExplicitDispatch;
import io.casehub.blocks.agentic.aggregation.PassThrough;
import io.casehub.blocks.agentic.decomposition.IdentityDecomposition;
import io.casehub.blocks.agentic.routing.RoundRobinRouting;
import io.casehub.blocks.agentic.termination.MaxIterationsTermination;
import io.casehub.blocks.agentic.termination.TerminationCondition;

public class DebateBuilder<T> extends AbstractPatternBuilder<T, DebateBuilder<T>> {

    private int maxRounds = 5;
    private AgentRef judge;

    public DebateBuilder() {
        this.routing = new RoundRobinRouting<>();
        this.decomposition = new IdentityDecomposition<>();
        this.activation = new OnExplicitDispatch<>();
        this.aggregation = new PassThrough<>();
        this.termination = new MaxIterationsTermination<>(maxRounds);
    }

    public DebateBuilder<T> debaters(AgentRef... agents) {
        return (DebateBuilder<T>) super.agents(agents);
    }

    public DebateBuilder<T> judge(AgentRef judge) {
        this.judge = judge;
        return this;
    }

    public DebateBuilder<T> maxRounds(int rounds) {
        this.maxRounds = rounds;
        this.termination = new MaxIterationsTermination<>(rounds);
        return this;
    }

    public DebateBuilder<T> convergence(TerminationCondition<T> convergence) {
        this.termination = convergence;
        return this;
    }
}

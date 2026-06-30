package io.casehub.blocks.agentic.routing;

import io.casehub.blocks.agentic.AgentRef;

import java.util.List;

public sealed interface RoutingDecision
        permits RoutingDecision.Selected, RoutingDecision.Unresolvable,
                RoutingDecision.Escalate {

    record Selected(List<AgentRef> agents) implements RoutingDecision {
        public Selected { agents = List.copyOf(agents); }
    }

    record Unresolvable(String reason) implements RoutingDecision {}

    record Escalate(String reason) implements RoutingDecision {}
}

package io.casehub.blocks.agentic.decomposition;

import io.casehub.blocks.agentic.RoutingCandidate;

import java.util.List;

public record DecompositionContext<T>(T state, List<RoutingCandidate> agents,
                                     int depth) {
    public DecompositionContext { agents = List.copyOf(agents); }
}

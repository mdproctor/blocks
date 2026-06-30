package io.casehub.blocks.agentic.routing;

import io.casehub.blocks.agentic.RoutingCandidate;

import java.util.function.Predicate;

public final class Routing {
    private Routing() {}

    public static <T> FirstMatchRouting<T> firstMatch(Predicate<RoutingCandidate> matcher) {
        return new FirstMatchRouting<>(matcher);
    }

    public static <T> RoundRobinRouting<T> roundRobin() {
        return new RoundRobinRouting<>();
    }
}

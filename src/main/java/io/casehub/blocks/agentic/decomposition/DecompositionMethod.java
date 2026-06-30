package io.casehub.blocks.agentic.decomposition;

import java.util.function.Predicate;

public record DecompositionMethod<T>(Predicate<T> guard,
                                     DecompositionStrategy<T> strategy) {}

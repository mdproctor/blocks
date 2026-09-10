package io.casehub.blocks.agentic.yaml.compiler;

import io.casehub.blocks.agentic.termination.TerminationCondition;
import io.casehub.blocks.negotiation.NegotiationProjection;
import io.casehub.blocks.negotiation.NegotiationState;
import org.jspecify.annotations.Nullable;

import java.util.Set;

public record CompiledNegotiation(
        NegotiationProjection projection,
        @Nullable TerminationCondition<NegotiationState> termination,
        Set<String> parties) {}

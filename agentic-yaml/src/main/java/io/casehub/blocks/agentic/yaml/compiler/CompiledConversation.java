package io.casehub.blocks.agentic.yaml.compiler;

import io.casehub.blocks.agentic.termination.TerminationCondition;
import io.casehub.blocks.conversation.ConversationState;
import io.casehub.blocks.conversation.ConvergencePolicy;
import io.casehub.blocks.conversation.EpistemicRule;
import io.casehub.blocks.conversation.orchestration.AgentParticipant;
import io.casehub.blocks.conversation.orchestration.TurnPolicy;
import org.jspecify.annotations.Nullable;

import java.util.List;

public record CompiledConversation(
        TurnPolicy turnPolicy,
        @Nullable TerminationCondition<ConversationState> termination,
        List<AgentParticipant> participants,
        @Nullable EpistemicRule epistemicRule,
        @Nullable ConvergencePolicy convergencePolicy) {}

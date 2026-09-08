package io.casehub.blocks.agentic.yaml.registry;

import io.casehub.blocks.agentic.yaml.spec.EpistemicRuleSpec;
import io.casehub.blocks.conversation.EpistemicRule;
import io.casehub.blocks.conversation.EpistemicRules;

public class EpistemicRuleRegistry {
    public EpistemicRule resolve(EpistemicRuleSpec spec) {
        return switch (spec) {
            case EpistemicRuleSpec.ExplicitAcknowledgement ea ->
                    EpistemicRules.explicitAcknowledgement(ea.minParticipants());
            case EpistemicRuleSpec.TacitAcceptance ta ->
                    EpistemicRules.tacitAcceptance(ta.windowRounds());
            case EpistemicRuleSpec.CommitmentResolution cr ->
                    EpistemicRules.commitmentResolution();
        };
    }
}

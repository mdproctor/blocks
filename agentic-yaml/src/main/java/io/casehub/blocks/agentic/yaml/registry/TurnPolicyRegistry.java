package io.casehub.blocks.agentic.yaml.registry;

import io.casehub.blocks.agentic.yaml.spec.TurnPolicySpec;
import io.casehub.blocks.conversation.orchestration.AddressedTurnPolicy;
import io.casehub.blocks.conversation.orchestration.FreeTurnPolicy;
import io.casehub.blocks.conversation.orchestration.PointAddressedTurnPolicy;
import io.casehub.blocks.conversation.orchestration.RoundRobinTurnPolicy;
import io.casehub.blocks.conversation.orchestration.TurnPolicy;

public class TurnPolicyRegistry {
    public TurnPolicy resolve(TurnPolicySpec spec) {
        return switch (spec) {
            case TurnPolicySpec.RoundRobin rr -> new RoundRobinTurnPolicy();
            case TurnPolicySpec.Addressed a -> new AddressedTurnPolicy();
            case TurnPolicySpec.PointAddressed pa -> new PointAddressedTurnPolicy();
            case TurnPolicySpec.Free f -> new FreeTurnPolicy();
        };
    }
}

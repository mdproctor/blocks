package io.casehub.blocks.agentic.yaml.registry;

import io.casehub.blocks.agentic.yaml.spec.AcceptancePolicySpec;
import io.casehub.blocks.negotiation.AcceptancePolicy;
import io.casehub.blocks.negotiation.MajorityAcceptance;
import io.casehub.blocks.negotiation.ThresholdAcceptance;
import io.casehub.blocks.negotiation.UnanimousAcceptance;

public class AcceptancePolicyRegistry {
    public AcceptancePolicy resolve(AcceptancePolicySpec spec) {
        return switch (spec) {
            case AcceptancePolicySpec.Unanimous u -> new UnanimousAcceptance();
            case AcceptancePolicySpec.Majority m -> new MajorityAcceptance();
            case AcceptancePolicySpec.Threshold t -> new ThresholdAcceptance(t.minAcceptances());
        };
    }
}

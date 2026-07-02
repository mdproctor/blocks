package io.casehub.blocks.routing;

import java.util.UUID;

/**
 * Compliance audit record for a trust-weighted routing decision.
 *
 * @param capabilityTag the capability being routed
 * @param workerId the selected worker
 * @param trustScoreAtRouting trust score at decision time; null if bootstrap
 * @param thresholdApplied the threshold that was applied
 * @param evidenceEntryId UUID reference to the attestation or ledger entry
 */
public record RoutingDecisionRecord(
        String capabilityTag,
        String workerId,
        Double trustScoreAtRouting,
        double thresholdApplied,
        UUID evidenceEntryId) {}

package io.casehub.blocks.routing;

import java.util.List;

/**
 * Compliance evidence wrapper for trust routing decisions.
 *
 * @param requirementId regulatory requirement identifier (e.g. "FATF-R20-TRUST-ROUTING")
 * @param citation human-readable regulatory citation
 * @param mechanism description of how the requirement is met
 * @param status current compliance status
 * @param decisions routing decision records supporting this requirement
 */
public record TrustRoutingRequirement(
        String requirementId,
        String citation,
        String mechanism,
        RequirementStatus status,
        List<RoutingDecisionRecord> decisions) {}

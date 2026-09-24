package io.casehub.blocks.agentic.social.goal;

import java.time.Duration;

public record CognitiveGoalConfig(
        double caseCreationThreshold,
        Duration surfacingCooldown,
        double minimumSurfacingPriority,
        double driveWeight
) {
    public static CognitiveGoalConfig defaults() {
        return new CognitiveGoalConfig(0.7, Duration.ofHours(1), 0.1, 0.5);
    }
}

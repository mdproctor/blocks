package io.casehub.blocks.agentic.yaml.registry;

import io.casehub.blocks.agentic.yaml.spec.PromptOptimiserSpec;
import io.casehub.blocks.prompt.PromptOptimiser;
import io.casehub.blocks.prompt.optimiser.FewShotOptimiser;
import io.casehub.blocks.prompt.optimiser.InstructionOptimiser;
import io.casehub.blocks.prompt.optimiser.TopNDiversityStrategy;
import io.casehub.platform.agent.AgentProvider;
import org.jspecify.annotations.Nullable;

public class PromptOptimiserRegistry {

    private final DiversityStrategyRegistry diversityRegistry;

    public PromptOptimiserRegistry(DiversityStrategyRegistry diversityRegistry) {
        this.diversityRegistry = diversityRegistry;
    }

    public PromptOptimiser resolve(PromptOptimiserSpec spec,
                                    @Nullable AgentProvider agentProvider) {
        return switch (spec) {
            case PromptOptimiserSpec.FewShot fs -> {
                var diversity = fs.diversity() != null
                        ? diversityRegistry.resolve(fs.diversity())
                        : new TopNDiversityStrategy();
                yield new FewShotOptimiser(diversity);
            }
            case PromptOptimiserSpec.Instruction ignored -> {
                if (agentProvider == null)
                    throw new IllegalStateException(
                            "instruction optimiser requires AgentProvider");
                yield new InstructionOptimiser(agentProvider);
            }
        };
    }
}

package io.casehub.blocks.agentic.yaml.spec;

public record AgentParticipantSpec(
        AgentRefSpec agent,
        String role,
        String systemPrompt) {}

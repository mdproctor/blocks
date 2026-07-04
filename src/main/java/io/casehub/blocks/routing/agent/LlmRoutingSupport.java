/*
 * Copyright 2026-Present The Case Hub Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.casehub.blocks.routing.agent;

import io.casehub.api.spi.routing.AgentCandidate;
import io.casehub.eidos.api.AgentCapability;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.stream.Collectors;

final class LlmRoutingSupport {

    private LlmRoutingSupport() {}

    static final String SYSTEM_PROMPT = """
            You are an agent router. Given a capability name, case context, and a list of \
            available agents, select the single best agent to handle the task.

            Respond with JSON only: {"agent": "<agent-name>", "reason": "<one sentence>"}

            Select based on the agent's capabilities, briefing, and domain expertise. \
            Choose the agent whose skills most closely match the task requirements.""";

    static String buildUserPrompt(String capabilityName, @Nullable String caseContextSummary,
                                  List<AgentCandidate> candidates) {
        var sb = new StringBuilder();
        sb.append("Capability: ").append(capabilityName).append("\n\n");
        if (caseContextSummary != null && !caseContextSummary.isBlank()) {
            sb.append("Case context:\n").append(caseContextSummary).append("\n\n");
        }
        sb.append("Available agents:\n");
        for (var candidate : candidates) {
            sb.append(buildCandidateCard(candidate)).append("\n");
        }
        return sb.toString();
    }

    static String buildCandidateCard(AgentCandidate candidate) {
        var sb = new StringBuilder();
        sb.append("- Agent: \"").append(candidate.workerId()).append("\"");
        var desc = candidate.agentDescriptor();
        if (desc != null) {
            if (desc.briefing() != null && !desc.briefing().isBlank()) {
                sb.append("\n  Briefing: ").append(desc.briefing());
            }
            if (desc.capabilities() != null && !desc.capabilities().isEmpty()) {
                var caps = desc.capabilities().stream()
                        .map(AgentCapability::name)
                        .collect(Collectors.joining(", "));
                sb.append("\n  Capabilities: ").append(caps);
            }
            if (desc.slot() != null && !desc.slot().isBlank()) {
                sb.append("\n  Slot: ").append(desc.slot());
            }
        }
        sb.append("\n  Health: ").append(candidate.health());
        sb.append("\n  Running jobs: ").append(candidate.runningJobs());
        return sb.toString();
    }

    static @Nullable String parseSelection(String llmResponse, List<AgentCandidate> candidates) {
        var agentName = extractAgentName(llmResponse);
        if (agentName == null || "done".equalsIgnoreCase(agentName)) {
            return null;
        }
        for (var candidate : candidates) {
            if (candidate.workerId().equals(agentName)) {
                return candidate.workerId();
            }
            var desc = candidate.agentDescriptor();
            if (desc != null && agentName.equals(desc.name())) {
                return candidate.workerId();
            }
        }
        return null;
    }

    static @Nullable String extractAgentName(@Nullable String text) {
        if (text == null) return null;
        var trimmed = text.trim();
        int agentIdx = trimmed.indexOf("\"agent\"");
        if (agentIdx < 0) return null;
        int colonIdx = trimmed.indexOf(':', agentIdx);
        if (colonIdx < 0) return null;
        int firstQuote = trimmed.indexOf('"', colonIdx + 1);
        if (firstQuote < 0) return null;
        int secondQuote = trimmed.indexOf('"', firstQuote + 1);
        if (secondQuote < 0) return null;
        return trimmed.substring(firstQuote + 1, secondQuote);
    }

    static @Nullable String invokeAndCollect(AgentProvider provider,
                                             String systemPrompt, String userPrompt) {
        try {
            var config = AgentSessionConfig.of(systemPrompt, userPrompt);
            return provider.invoke(config)
                    .filter(e -> e instanceof AgentEvent.TextDelta)
                    .map(e -> ((AgentEvent.TextDelta) e).text())
                    .collect().with(Collectors.joining())
                    .await().indefinitely();
        } catch (Exception e) {
            return null;
        }
    }
}

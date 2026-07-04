package io.casehub.blocks.routing.agent;

import io.casehub.api.spi.routing.AgentCandidate;
import io.casehub.api.spi.routing.AgentHealth;
import io.casehub.eidos.api.AgentCapability;
import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmRoutingSupportTest {

    private AgentCandidate candidate(String workerId, String briefing) {
        var descriptor = AgentDescriptor.builder()
                .agentId(workerId)
                .name(workerId)
                .slot("agent")
                .tenancyId("test")
                .capabilities(List.of(AgentCapability.builder().name("analysis").build()))
                .briefing(briefing)
                .build();
        return new AgentCandidate(workerId, Set.of("analysis"), 0, AgentHealth.READY, descriptor);
    }

    private AgentCandidate candidateNoDescriptor(String workerId) {
        return new AgentCandidate(workerId, Set.of("analysis"), 0, AgentHealth.READY, null);
    }

    @Nested
    class PromptBuilding {
        @Test
        void includesCapabilityAndContext() {
            var prompt = LlmRoutingSupport.buildUserPrompt(
                    "data-analysis", "Quarterly report data",
                    List.of(candidate("analyst", "Analyses data")));
            assertThat(prompt).contains("data-analysis").contains("Quarterly report data");
        }

        @Test
        void includesCandidateBriefingAndCapabilities() {
            var prompt = LlmRoutingSupport.buildUserPrompt(
                    "review", null,
                    List.of(candidate("reviewer", "Reviews code for security")));
            assertThat(prompt).contains("reviewer").contains("Reviews code for security").contains("analysis");
        }

        @Test
        void handlesCandidateWithoutDescriptor() {
            var prompt = LlmRoutingSupport.buildUserPrompt(
                    "task", null,
                    List.of(candidateNoDescriptor("plain-worker")));
            assertThat(prompt).contains("plain-worker");
        }
    }

    @Nested
    class ResponseParsing {
        @Test
        void extractsAgentNameFromValidJson() {
            assertThat(LlmRoutingSupport.extractAgentName(
                    "{\"agent\": \"reviewer\", \"reason\": \"best fit\"}"))
                    .isEqualTo("reviewer");
        }

        @Test
        void returnsNullForMalformedResponse() {
            assertThat(LlmRoutingSupport.extractAgentName("not json")).isNull();
        }

        @Test
        void returnsNullForNullInput() {
            assertThat(LlmRoutingSupport.extractAgentName(null)).isNull();
        }

        @Test
        void parsesSelectionToWorkerId() {
            var candidates = List.of(
                    candidate("agent-a", "Does A"),
                    candidate("agent-b", "Does B"));
            var result = LlmRoutingSupport.parseSelection(
                    "{\"agent\": \"agent-b\", \"reason\": \"better fit\"}", candidates);
            assertThat(result).isEqualTo("agent-b");
        }

        @Test
        void returnsNullForUnknownAgent() {
            var candidates = List.of(candidate("agent-a", "Does A"));
            var result = LlmRoutingSupport.parseSelection(
                    "{\"agent\": \"unknown\", \"reason\": \"?\"}", candidates);
            assertThat(result).isNull();
        }

        @Test
        void returnsNullForDoneAgent() {
            var candidates = List.of(candidate("agent-a", "Does A"));
            var result = LlmRoutingSupport.parseSelection(
                    "{\"agent\": \"done\", \"reason\": \"complete\"}", candidates);
            assertThat(result).isNull();
        }
    }

    @Nested
    class InvokeAndCollect {
        @Test
        void collectsTextDeltaEvents() {
            var provider = mock(AgentProvider.class);
            when(provider.invoke(any(AgentSessionConfig.class)))
                    .thenReturn(Multi.createFrom().items(
                            new AgentEvent.TextDelta("{\"agent\""),
                            new AgentEvent.TextDelta(": \"reviewer\", \"reason\": \"ok\"}")));
            var result = LlmRoutingSupport.invokeAndCollect(provider, "system", "user");
            assertThat(result).isEqualTo("{\"agent\": \"reviewer\", \"reason\": \"ok\"}");
        }

        @Test
        void returnsNullOnProviderFailure() {
            var provider = mock(AgentProvider.class);
            when(provider.invoke(any(AgentSessionConfig.class)))
                    .thenReturn(Multi.createFrom().failure(new RuntimeException("LLM down")));
            var result = LlmRoutingSupport.invokeAndCollect(provider, "system", "user");
            assertThat(result).isNull();
        }
    }
}

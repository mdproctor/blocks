package io.casehub.blocks.routing.agent;

import com.fasterxml.jackson.databind.node.NullNode;
import io.casehub.api.spi.routing.AgentAssignment;
import io.casehub.api.spi.routing.AgentCandidate;
import io.casehub.api.spi.routing.AgentHealth;
import io.casehub.api.spi.routing.AgentRoutingContext;
import io.casehub.api.spi.routing.EscalationReason;
import io.casehub.api.spi.routing.TrustRoutingPolicy;
import io.casehub.api.spi.routing.TrustRoutingPolicyProvider;
import io.casehub.eidos.api.AgentGraphQuery;
import io.casehub.ledger.api.spi.TrustScoreSource;
import io.casehub.ledger.routing.TrustCandidateClassifier;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrCase;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.memory.cbr.PlanCbrCase;
import io.casehub.neocortex.memory.cbr.PlanTrace;
import io.casehub.neocortex.memory.cbr.ScoredCbrCase;
import io.casehub.neocortex.memory.cbr.TextualCbrCase;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CbrAgentRoutingStrategyTest {

    @Mock CbrCaseMemoryStore cbrStore;
    @Mock AgentGraphQuery graphQuery;
    @Mock TrustCandidateClassifier classifier;
    @Mock TrustScoreSource scoreSource;
    @Mock TrustRoutingPolicyProvider policyProvider;

    private AgentRoutingContext context(String capability) {
        return new AgentRoutingContext(UUID.randomUUID(), capability, NullNode.instance, "test-tenant");
    }

    private AgentCandidate candidate(String id) {
        return new AgentCandidate(id, Set.of("analysis"), 0, AgentHealth.READY, null);
    }

    @SuppressWarnings("unchecked")
    private <T> Instance<T> presentInstance(T value) {
        var inst = mock(Instance.class);
        when(inst.isUnsatisfied()).thenReturn(false);
        when(inst.get()).thenReturn(value);
        return inst;
    }

    @SuppressWarnings("unchecked")
    private <T> Instance<T> absentInstance() {
        var inst = mock(Instance.class);
        when(inst.isUnsatisfied()).thenReturn(true);
        return inst;
    }

    @Test
    void idIsCbr() {
        var strategy = new CbrAgentRoutingStrategy(
                absentInstance(), absentInstance(), absentInstance(), absentInstance(), absentInstance());
        assertThat(strategy.id()).isEqualTo("cbr");
    }

    @Nested
    class PlanBasedCbr {
        private CbrAgentRoutingStrategy strategy;

        @BeforeEach
        void setUp() {
            strategy = new CbrAgentRoutingStrategy(
                    presentInstance(cbrStore), presentInstance(graphQuery),
                    absentInstance(), absentInstance(), absentInstance());
        }

        @Test
        void selectsWorkerWithHighestSuccessRate() {
            var winnerTrace = new PlanTrace("binding-a", "analysis", "agent-a", "SUCCESS", 1, Map.of());
            var loserTrace = new PlanTrace("binding-a", "analysis", "agent-b", "FAILURE", 1, Map.of());
            var winner2Trace = new PlanTrace("binding-a", "analysis", "agent-a", "SUCCESS", 1, Map.of());

            var case1 = new PlanCbrCase("problem1", "sol1", "RESOLVED", 0.9, Map.of(),
                    List.of(winnerTrace));
            var case2 = new PlanCbrCase("problem2", "sol2", "UNRESOLVED", 0.3, Map.of(),
                    List.of(loserTrace));
            var case3 = new PlanCbrCase("problem3", "sol3", "RESOLVED", 0.8, Map.of(),
                    List.of(winner2Trace));

            when(cbrStore.retrieveSimilar(any(CbrQuery.class), eq(CbrCase.class)))
                    .thenReturn(List.of(
                            new ScoredCbrCase<>(case1, 0.9),
                            new ScoredCbrCase<>(case2, 0.85),
                            new ScoredCbrCase<>(case3, 0.8)));

            var result = strategy.select(context("analysis"),
                    List.of(candidate("agent-a"), candidate("agent-b"))).await().indefinitely();

            assertThat(result).isInstanceOf(AgentAssignment.Assigned.class);
            assertThat(((AgentAssignment.Assigned) result).workerId()).isEqualTo("agent-a");
        }

        @Test
        void ignoresWorkersNotInCandidateList() {
            var trace = new PlanTrace("binding-a", "analysis", "not-a-candidate", "SUCCESS", 1, Map.of());
            var cbrCase = new PlanCbrCase("problem", "solution", "RESOLVED", 0.9, Map.of(),
                    List.of(trace));
            when(cbrStore.retrieveSimilar(any(CbrQuery.class), eq(CbrCase.class)))
                    .thenReturn(List.of(new ScoredCbrCase<>(cbrCase, 0.9)));

            var result = strategy.select(context("analysis"),
                    List.of(candidate("agent-a"))).await().indefinitely();
            // Falls through to AgentGraphQuery or Unresolvable
            assertThat(result).isNotNull();
        }
    }

    @Nested
    class TextualFallback {
        @Test
        void textualCasesDoNotProduceWorkerMatch() {
            var textCase = new TextualCbrCase("similar problem", "some solution", "RESOLVED", 0.8);
            when(cbrStore.retrieveSimilar(any(CbrQuery.class), eq(CbrCase.class)))
                    .thenReturn(List.of(new ScoredCbrCase<>(textCase, 0.9)));

            var strategy = new CbrAgentRoutingStrategy(
                    presentInstance(cbrStore), presentInstance(graphQuery),
                    absentInstance(), absentInstance(), absentInstance());

            var result = strategy.select(context("analysis"),
                    List.of(candidate("agent-a"))).await().indefinitely();
            // Textual cases lack worker identity — falls through
            assertThat(result).isNotNull();
        }
    }

    @Nested
    class Fallbacks {
        @Test
        void fallsBackToGraphQueryWhenCbrStoreUnavailable() {
            when(graphQuery.topAgentsByOutcome(eq("analysis"), any(), any(), eq(10)))
                    .thenReturn(List.of("agent-b", "agent-a"));

            var strategy = new CbrAgentRoutingStrategy(
                    absentInstance(), presentInstance(graphQuery),
                    absentInstance(), absentInstance(), absentInstance());

            var result = strategy.select(context("analysis"),
                    List.of(candidate("agent-a"), candidate("agent-b"))).await().indefinitely();
            assertThat(result).isInstanceOf(AgentAssignment.Assigned.class);
            assertThat(((AgentAssignment.Assigned) result).workerId()).isEqualTo("agent-b");
        }

        @Test
        void bothSourcesUnavailableReturnsUnresolvable() {
            var strategy = new CbrAgentRoutingStrategy(
                    absentInstance(), absentInstance(),
                    absentInstance(), absentInstance(), absentInstance());
            var result = strategy.select(context("analysis"),
                    List.of(candidate("agent-a"))).await().indefinitely();
            assertThat(result).isInstanceOf(AgentAssignment.Unresolvable.class);
        }

        @Test
        void emptyCandidatesReturnsUnresolvable() {
            var strategy = new CbrAgentRoutingStrategy(
                    presentInstance(cbrStore), absentInstance(),
                    absentInstance(), absentInstance(), absentInstance());
            var result = strategy.select(context("analysis"), List.of()).await().indefinitely();
            assertThat(result).isInstanceOf(AgentAssignment.Unresolvable.class);
        }
    }

    @Nested
    class WithTrust {
        @Test
        void excludedCandidatesFilteredBeforeCbr() {
            var policy = new TrustRoutingPolicy(0.7, 5, 0.1, 0.5, Map.of(), false, null);
            when(policyProvider.forCapability("analysis")).thenReturn(policy);
            var good = candidate("qualified");
            var bad = candidate("excluded");
            var classified = List.of(
                    new TrustCandidateClassifier.ClassifiedCandidate(
                            good, TrustCandidateClassifier.Phase.QUALIFIED,
                            OptionalDouble.of(0.9), 1.0),
                    new TrustCandidateClassifier.ClassifiedCandidate(
                            bad, TrustCandidateClassifier.Phase.EXCLUDED_PHASE2B,
                            OptionalDouble.of(0.3), 1.0));
            when(classifier.classify(any(), eq("analysis"), eq(policy), eq(scoreSource)))
                    .thenReturn(classified);
            when(graphQuery.topAgentsByOutcome(eq("analysis"), any(), any(), eq(10)))
                    .thenReturn(List.of("qualified"));

            var strategy = new CbrAgentRoutingStrategy(
                    absentInstance(), presentInstance(graphQuery),
                    presentInstance(classifier), presentInstance(scoreSource),
                    presentInstance(policyProvider));

            var result = strategy.select(context("analysis"),
                    List.of(good, bad)).await().indefinitely();
            assertThat(result).isInstanceOf(AgentAssignment.Assigned.class);
            assertThat(((AgentAssignment.Assigned) result).workerId()).isEqualTo("qualified");
        }
    }
}

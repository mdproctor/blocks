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

import io.casehub.api.spi.routing.AgentAssignment;
import io.casehub.api.spi.routing.AgentCandidate;
import io.casehub.api.spi.routing.AgentRoutingContext;
import io.casehub.api.spi.routing.AgentRoutingStrategy;
import io.casehub.api.spi.routing.EscalationReason;
import io.casehub.api.spi.routing.TrustRoutingPolicy;
import io.casehub.api.spi.routing.TrustRoutingPolicyProvider;
import io.casehub.ledger.api.spi.TrustScoreSource;
import io.casehub.ledger.routing.TrustCandidateClassifier;
import io.casehub.ledger.routing.TrustCandidateClassifier.ClassifiedCandidate;
import io.casehub.ledger.routing.TrustCandidateClassifier.Phase;
import io.casehub.ledger.routing.TrustCandidateClassifier.ScoredCandidate;
import io.casehub.platform.agent.AgentProvider;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * {@link AgentRoutingStrategy} that uses LLM reasoning to select agents from a candidate pool,
 * with optional trust-based classification and filtering.
 *
 * <h3>Operation modes</h3>
 *
 * <ul>
 *   <li><b>Pure LLM mode</b>: when trust services are unavailable, delegates selection entirely to
 *       the LLM. Never escalates to oversight.
 *   <li><b>Trust-filtered LLM mode</b>: when {@link TrustCandidateClassifier}, {@link
 *       TrustScoreSource}, and {@link TrustRoutingPolicyProvider} are available, applies
 *       trust-based pre-screening before LLM selection.
 * </ul>
 *
 * <h3>Trust classification flow</h3>
 *
 * When trust services are present, follows the four-phase trust maturity model:
 *
 * <ol>
 *   <li>Classify all candidates via {@link TrustCandidateClassifier}.
 *   <li>Apply bootstrap guard: if {@code bootstrapEscalationRequired} is {@code true} and only
 *       BOOTSTRAP candidates exist, escalate with {@link EscalationReason#NO_QUALIFIED_AGENT}.
 *   <li>Filter eligible candidates: exclude BORDERLINE, EXCLUDED_PHASE2B, and EXCLUDED_PHASE3.
 *       Also exclude BOOTSTRAP when {@code bootstrapEscalationRequired} is {@code true}.
 *   <li>If eligible pool is empty after filtering, delegate escalation decision to {@link
 *       TrustCandidateClassifier#decide}.
 *   <li>Otherwise, invoke LLM with the filtered pool.
 * </ol>
 *
 * <h3>LLM invocation</h3>
 *
 * Delegates to {@link LlmRoutingSupport} for prompt construction, LLM invocation, and response
 * parsing. Returns {@link AgentAssignment.Unresolvable} on LLM failure, unparseable response, or
 * unknown agent selection.
 *
 * <p>All blocking work runs on {@code Infrastructure.getDefaultWorkerPool()} via {@code
 * Uni.emitOn()}.
 */
@ApplicationScoped
public class LlmAgentRoutingStrategy implements AgentRoutingStrategy {

  private static final System.Logger LOG =
      System.getLogger(LlmAgentRoutingStrategy.class.getName());

  private final @Nullable AgentProvider agentProvider;
  private final @Nullable TrustCandidateClassifier classifier;
  private final @Nullable TrustScoreSource scoreSource;
  private final @Nullable TrustRoutingPolicyProvider policyProvider;

  @Inject
  public LlmAgentRoutingStrategy(
      final Instance<AgentProvider> agentProvider,
      final Instance<TrustCandidateClassifier> classifier,
      final Instance<TrustScoreSource> scoreSource,
      final Instance<TrustRoutingPolicyProvider> policyProvider) {
    this.agentProvider = agentProvider.isUnsatisfied() ? null : agentProvider.get();
    this.classifier = classifier.isUnsatisfied() ? null : classifier.get();
    this.scoreSource = scoreSource.isUnsatisfied() ? null : scoreSource.get();
    this.policyProvider = policyProvider.isUnsatisfied() ? null : policyProvider.get();
  }

  @Override
  public String id() {
    return "llm";
  }

  @Override
  public Uni<AgentAssignment> select(
      final AgentRoutingContext context, final List<AgentCandidate> candidates) {
    if (candidates.isEmpty()) {
      return Uni.createFrom().item(AgentAssignment.unresolvable("no candidates available"));
    }
    if (agentProvider == null) {
      return Uni.createFrom()
          .item(AgentAssignment.unresolvable("AgentProvider not available"));
    }

    return Uni.createFrom()
        .item(() -> doSelect(context, candidates))
        .emitOn(Infrastructure.getDefaultWorkerPool());
  }

  private AgentAssignment doSelect(
      final AgentRoutingContext context, final List<AgentCandidate> candidates) {
    List<AgentCandidate> eligible = candidates;
    List<ClassifiedCandidate> classified = null;

    // Trust classification path
    if (classifier != null && scoreSource != null && policyProvider != null) {
      final TrustRoutingPolicy policy = policyProvider.forCapability(context.capabilityName());
      classified = classifier.classify(candidates, context.capabilityName(), policy, scoreSource);

      // Bootstrap guard: pre-screen before scoring
      if (policy.bootstrapEscalationRequired()) {
        final boolean hasQualified =
            classified.stream().anyMatch(c -> c.phase() == Phase.QUALIFIED);
        final boolean hasBootstrap =
            classified.stream().anyMatch(c -> c.phase() == Phase.BOOTSTRAP);
        if (!hasQualified && hasBootstrap) {
          return AgentAssignment.escalate(
              context.capabilityName(),
              EscalationReason.NO_QUALIFIED_AGENT,
              "bootstrap only — no qualified agents for capability '%s'"
                  .formatted(context.capabilityName()));
        }
      }

      // Filter eligible candidates
      eligible =
          classified.stream()
              .filter(c -> !c.isExcluded())
              .filter(c -> !policy.bootstrapEscalationRequired() || c.phase() != Phase.BOOTSTRAP)
              .map(ClassifiedCandidate::candidate)
              .toList();

      // If no eligible candidates remain, delegate to classifier.decide
      if (eligible.isEmpty()) {
        final List<ScoredCandidate> scored =
            classified.stream().map(c -> new ScoredCandidate(c, 0.0, "excluded")).toList();
        return classifier.decide(classified, scored, context.capabilityName());
      }
    }

    // LLM invocation
    final String caseContextSummary =
        context.caseContext() != null ? context.caseContext().toString() : null;
    final String prompt =
        LlmRoutingSupport.buildUserPrompt(context.capabilityName(), caseContextSummary, eligible);
    final String response =
        LlmRoutingSupport.invokeAndCollect(
            agentProvider, LlmRoutingSupport.SYSTEM_PROMPT, prompt);

    if (response == null) {
      return AgentAssignment.unresolvable("LLM invocation failed or returned no response");
    }

    final String workerId = LlmRoutingSupport.parseSelection(response, eligible);
    if (workerId == null) {
      return AgentAssignment.unresolvable(
          "LLM response unparseable or selected unknown agent: " + response);
    }

    return AgentAssignment.assign(workerId, "LLM selected from %d candidates".formatted(eligible.size()));
  }
}

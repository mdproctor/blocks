package io.casehub.blocks.agentic.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.decomposition.TaskNode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ExecutionPlanTest {

    private static TaskNode.PlannedTask<String> task(String desc) {
        return new TaskNode.PlannedTask<>(java.util.UUID.randomUUID().toString(), java.time.Instant.now(), desc,
                new AgentRef.ExternalAgent(null, s -> java.util.concurrent.CompletableFuture.completedStage(io.casehub.blocks.agentic.AgentResult.success(null, null))), null);
    }

    @Test
    void singleton_creates_single_node_plan() {
        ExecutionPlan<String> plan = ExecutionPlan.singleton(task("A"));
        assertThat(plan.nodes()).hasSize(1);
        assertThat(plan.entryNodeIds()).containsExactly("node-0");
        assertThat(plan.exitNodeIds()).containsExactly("node-0");
    }

    @Test
    void sequence_creates_chain() {
        ExecutionPlan<String> plan = ExecutionPlan.sequence(
                List.of(task("A"), task("B"), task("C")));
        assertThat(plan.nodes()).hasSize(3);
        assertThat(plan.entryNodeIds()).containsExactly("node-0");
        assertThat(plan.exitNodeIds()).containsExactly("node-2");
        assertThat(plan.nodes().get("node-1").dependsOn()).containsExactly("node-0");
        assertThat(plan.nodes().get("node-2").dependsOn()).containsExactly("node-1");
    }

    @Test
    void parallel_creates_independent_nodes() {
        ExecutionPlan<String> plan = ExecutionPlan.parallel(
                List.of(task("A"), task("B"), task("C")));
        assertThat(plan.nodes()).hasSize(3);
        assertThat(plan.entryNodeIds()).containsExactlyInAnyOrder("node-0", "node-1", "node-2");
        assertThat(plan.exitNodeIds()).containsExactlyInAnyOrder("node-0", "node-1", "node-2");
        plan.nodes().values().forEach(n ->
                assertThat(n.dependsOn()).isEmpty());
    }

    @Test
    void fromList_same_as_sequence() {
        var tasks = List.of(task("A"), task("B"));
        ExecutionPlan<String> seq = ExecutionPlan.sequence(tasks);
        ExecutionPlan<String> fromList = ExecutionPlan.fromList(tasks);
        assertThat(fromList.nodes()).hasSameSizeAs(seq.nodes());
    }

    @Test
    void sequentialMerge_chains_subplans() {
        ExecutionPlan<String> a = ExecutionPlan.sequence(List.of(task("A1"), task("A2")));
        ExecutionPlan<String> b = ExecutionPlan.singleton(task("B1"));
        ExecutionPlan<String> merged = ExecutionPlan.sequentialMerge(List.of(a, b));
        assertThat(merged.nodes()).hasSize(3);
        assertThat(merged.entryNodeIds()).hasSize(1);
        assertThat(merged.exitNodeIds()).hasSize(1);
    }

    @Test
    void sequentialMerge_connects_exit_to_entry() {
        ExecutionPlan<String> a = ExecutionPlan.singleton(task("A"));
        ExecutionPlan<String> b = ExecutionPlan.singleton(task("B"));
        ExecutionPlan<String> merged = ExecutionPlan.sequentialMerge(List.of(a, b));

        String bNodeId = merged.exitNodeIds().iterator().next();
        ExecutionPlan.ExecutionNode<String> bNode = merged.nodes().get(bNodeId);
        assertThat(bNode.dependsOn()).hasSize(1);
    }

    @Test
    void sequentialMerge_single_plan_returns_same() {
        ExecutionPlan<String> plan = ExecutionPlan.singleton(task("A"));
        assertThat(ExecutionPlan.sequentialMerge(List.of(plan))).isSameAs(plan);
    }

    @Test
    void empty_nodes_rejected() {
        assertThatThrownBy(() -> new ExecutionPlan<>(Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be empty");
    }

    @Test
    void dangling_dependsOn_rejected() {
        var node = new ExecutionPlan.ExecutionNode<>("n1", task("A"),
                Set.of("nonexistent"), ExecutionPlan.JoinType.ALL_OF);
        assertThatThrownBy(() -> new ExecutionPlan<>(Map.of("n1", node)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-existent");
    }

    @Test
    void cycle_rejected() {
        var n1 = new ExecutionPlan.ExecutionNode<>("n1", task("A"),
                Set.of("n2"), ExecutionPlan.JoinType.ALL_OF);
        var n2 = new ExecutionPlan.ExecutionNode<>("n2", task("B"),
                Set.of("n1"), ExecutionPlan.JoinType.ALL_OF);
        assertThatThrownBy(() -> new ExecutionPlan<>(Map.of("n1", n1, "n2", n2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cycle");
    }

    @Test
    void nodes_immutable() {
        ExecutionPlan<String> plan = ExecutionPlan.singleton(task("A"));
        assertThatThrownBy(() -> plan.nodes().put("x", null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void joinType_defaults_to_ALL_OF() {
        var node = new ExecutionPlan.ExecutionNode<>("n1", task("A"), Set.of(), null);
        assertThat(node.joinType()).isEqualTo(ExecutionPlan.JoinType.ALL_OF);
    }

    @Test
    void topologicalSort_sequence() {
        ExecutionPlan<String> plan = ExecutionPlan.sequence(
                List.of(task("A"), task("B"), task("C")));
        List<ExecutionPlan.ExecutionNode<String>> sorted = plan.topologicalSort();
        assertThat(sorted).hasSize(3);
        assertThat(sorted.get(0).id()).isEqualTo("node-0");
        assertThat(sorted.get(1).id()).isEqualTo("node-1");
        assertThat(sorted.get(2).id()).isEqualTo("node-2");
    }

    @Test
    void topologicalSort_parallel() {
        ExecutionPlan<String> plan = ExecutionPlan.parallel(
                List.of(task("A"), task("B"), task("C")));
        List<ExecutionPlan.ExecutionNode<String>> sorted = plan.topologicalSort();
        assertThat(sorted).hasSize(3);
    }

    @Test
    void topologicalSort_immutable() {
        ExecutionPlan<String> plan = ExecutionPlan.singleton(task("A"));
        assertThatThrownBy(() -> plan.topologicalSort().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void empty_sequence_rejected() {
        assertThatThrownBy(() -> ExecutionPlan.sequence(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void empty_parallel_rejected() {
        assertThatThrownBy(() -> ExecutionPlan.parallel(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void empty_merge_rejected() {
        assertThatThrownBy(() -> ExecutionPlan.sequentialMerge(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void forkJoin_pattern() {
        var root = task("root");
        var branch1 = task("b1");
        var branch2 = task("b2");
        var join = task("join");

        Map<String, ExecutionPlan.ExecutionNode<String>> nodes = new java.util.LinkedHashMap<>();
        nodes.put("root", new ExecutionPlan.ExecutionNode<>("root", root, Set.of(), ExecutionPlan.JoinType.ALL_OF));
        nodes.put("b1", new ExecutionPlan.ExecutionNode<>("b1", branch1, Set.of("root"), ExecutionPlan.JoinType.ALL_OF));
        nodes.put("b2", new ExecutionPlan.ExecutionNode<>("b2", branch2, Set.of("root"), ExecutionPlan.JoinType.ALL_OF));
        nodes.put("join", new ExecutionPlan.ExecutionNode<>("join", join, Set.of("b1", "b2"), ExecutionPlan.JoinType.ALL_OF));

        ExecutionPlan<String> plan = new ExecutionPlan<>(nodes);
        assertThat(plan.entryNodeIds()).containsExactly("root");
        assertThat(plan.exitNodeIds()).containsExactly("join");

        List<ExecutionPlan.ExecutionNode<String>> sorted = plan.topologicalSort();
        assertThat(sorted.get(0).id()).isEqualTo("root");
        assertThat(sorted.get(3).id()).isEqualTo("join");
    }

    @Test
    void anyOf_joinType_preserved() {
        var n1 = new ExecutionPlan.ExecutionNode<>("n1", task("A"), Set.of(), ExecutionPlan.JoinType.ALL_OF);
        var n2 = new ExecutionPlan.ExecutionNode<>("n2", task("B"), Set.of(), ExecutionPlan.JoinType.ALL_OF);
        var join = new ExecutionPlan.ExecutionNode<>("join", task("J"), Set.of("n1", "n2"), ExecutionPlan.JoinType.ANY_OF);
        ExecutionPlan<String> plan = new ExecutionPlan<>(Map.of("n1", n1, "n2", n2, "join", join));
        assertThat(plan.nodes().get("join").joinType()).isEqualTo(ExecutionPlan.JoinType.ANY_OF);
    }
}

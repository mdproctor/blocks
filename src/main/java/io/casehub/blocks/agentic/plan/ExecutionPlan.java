package io.casehub.blocks.agentic.plan;

import io.casehub.blocks.agentic.decomposition.TaskNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A DAG (Directed Acyclic Graph) plan expressing task dependencies and join semantics.
 * Replaces flat {@code List<TaskNode>} with a structure that can express parallel execution
 * and dependency relationships.
 *
 * @param nodes all nodes in the plan, keyed by node ID
 */
public record ExecutionPlan<T>(Map<String, ExecutionNode<T>> nodes) {

    public enum JoinType {
        /** Node fires when every predecessor completes (conjunction, fork-join). */
        ALL_OF,
        /** Node fires when at least one predecessor succeeds (disjunction, structural alternatives). */
        ANY_OF
    }

    public record ExecutionNode<T>(
            String id,
            TaskNode.LeafTask<T> task,
            Set<String> dependsOn,
            JoinType joinType) {
        public ExecutionNode {
            Objects.requireNonNull(id, "id required");
            Objects.requireNonNull(task, "task required");
            dependsOn = dependsOn != null ? Set.copyOf(dependsOn) : Set.of();
            if (joinType == null) joinType = JoinType.ALL_OF;
        }
    }

    public ExecutionPlan {
        Objects.requireNonNull(nodes, "nodes required");
        if (nodes.isEmpty())
            throw new IllegalArgumentException("nodes must not be empty");
        nodes = Map.copyOf(nodes);
        validateReferences(nodes);
        validateNoCycles(nodes);
        if (computeEntryNodes(nodes).isEmpty())
            throw new IllegalArgumentException("plan must have at least one entry node");
    }

    /** Nodes with no predecessors — fire immediately. */
    public Set<String> entryNodeIds() {
        return computeEntryNodes(nodes);
    }

    /** Nodes that no other node depends on — the plan's terminal nodes. */
    public Set<String> exitNodeIds() {
        Set<String> referenced = nodes.values().stream()
                .flatMap(n -> n.dependsOn().stream())
                .collect(Collectors.toSet());
        return nodes.keySet().stream()
                .filter(id -> !referenced.contains(id))
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Topological sort — returns nodes in dependency order. */
    public List<ExecutionNode<T>> topologicalSort() {
        Map<String, Integer> inDegree = new HashMap<>();
        for (var n : nodes.values()) inDegree.put(n.id(), 0);
        for (var n : nodes.values()) {
            for (String dep : n.dependsOn()) {
                inDegree.merge(dep, 0, Integer::sum); // ensure dep is in map
            }
            // each node's dependsOn means THIS node has in-degree from those
            // but we need reverse: for each dep, the current node is a successor
        }
        // recompute: for each node, count how many nodes list it as a dependency target
        inDegree.replaceAll((k, v) -> 0);
        Map<String, List<String>> successors = new HashMap<>();
        for (var n : nodes.values()) {
            for (String dep : n.dependsOn()) {
                successors.computeIfAbsent(dep, k -> new ArrayList<>()).add(n.id());
                inDegree.merge(n.id(), 1, Integer::sum);
            }
        }

        Queue<String> queue = new ArrayDeque<>();
        for (var entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) queue.add(entry.getKey());
        }

        List<ExecutionNode<T>> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            String current = queue.poll();
            sorted.add(nodes.get(current));
            for (String succ : successors.getOrDefault(current, List.of())) {
                int newDegree = inDegree.merge(succ, -1, Integer::sum);
                if (newDegree == 0) queue.add(succ);
            }
        }
        return Collections.unmodifiableList(sorted);
    }

    // --- Factory methods ---

    public static <T> ExecutionPlan<T> singleton(TaskNode.LeafTask<T> task) {
        String id = "node-0";
        return new ExecutionPlan<>(Map.of(id,
                new ExecutionNode<>(id, task, Set.of(), JoinType.ALL_OF)));
    }

    public static <T> ExecutionPlan<T> sequence(List<? extends TaskNode.LeafTask<T>> tasks) {
        if (tasks.isEmpty()) throw new IllegalArgumentException("tasks must not be empty");
        Map<String, ExecutionNode<T>> nodes = new LinkedHashMap<>();
        String previousId = null;
        for (int i = 0; i < tasks.size(); i++) {
            String id = "node-" + i;
            Set<String> deps = previousId != null ? Set.of(previousId) : Set.of();
            nodes.put(id, new ExecutionNode<>(id, tasks.get(i), deps, JoinType.ALL_OF));
            previousId = id;
        }
        return new ExecutionPlan<>(nodes);
    }

    public static <T> ExecutionPlan<T> parallel(List<? extends TaskNode.LeafTask<T>> tasks) {
        if (tasks.isEmpty()) throw new IllegalArgumentException("tasks must not be empty");
        Map<String, ExecutionNode<T>> nodes = new LinkedHashMap<>();
        for (int i = 0; i < tasks.size(); i++) {
            String id = "node-" + i;
            nodes.put(id, new ExecutionNode<>(id, tasks.get(i), Set.of(), JoinType.ALL_OF));
        }
        return new ExecutionPlan<>(nodes);
    }

    public static <T> ExecutionPlan<T> fromList(List<? extends TaskNode.LeafTask<T>> tasks) {
        return sequence(tasks);
    }

    public static <T> ExecutionPlan<T> sequentialMerge(List<ExecutionPlan<T>> subPlans) {
        if (subPlans.isEmpty()) throw new IllegalArgumentException("subPlans must not be empty");
        if (subPlans.size() == 1) return subPlans.get(0);

        Map<String, ExecutionNode<T>> mergedNodes = new LinkedHashMap<>();
        Set<String> previousExitIds = Set.of();

        for (int planIdx = 0; planIdx < subPlans.size(); planIdx++) {
            ExecutionPlan<T> subPlan = subPlans.get(planIdx);
            String prefix = "sub" + planIdx + "-";

            Map<String, String> idMapping = new HashMap<>();
            for (String oldId : subPlan.nodes().keySet()) {
                idMapping.put(oldId, prefix + oldId);
            }

            Set<String> subPlanEntryIds = subPlan.entryNodeIds();

            for (var entry : subPlan.nodes().entrySet()) {
                ExecutionNode<T> oldNode = entry.getValue();
                String newId = idMapping.get(oldNode.id());

                Set<String> newDeps = new HashSet<>();
                for (String dep : oldNode.dependsOn()) {
                    newDeps.add(idMapping.get(dep));
                }
                if (subPlanEntryIds.contains(oldNode.id())) {
                    newDeps.addAll(previousExitIds);
                }

                mergedNodes.put(newId, new ExecutionNode<>(newId, oldNode.task(),
                        newDeps, oldNode.joinType()));
            }

            previousExitIds = new HashSet<>();
            for (String exitId : subPlan.exitNodeIds()) {
                previousExitIds.add(prefix + exitId);
            }
        }

        return new ExecutionPlan<>(mergedNodes);
    }

    // --- Validation ---

    private static <T> void validateReferences(Map<String, ExecutionNode<T>> nodes) {
        for (var node : nodes.values()) {
            for (String dep : node.dependsOn()) {
                if (!nodes.containsKey(dep)) {
                    throw new IllegalArgumentException(
                            "Node '" + node.id() + "' depends on non-existent node '" + dep + "'");
                }
            }
        }
    }

    private static <T> void validateNoCycles(Map<String, ExecutionNode<T>> nodes) {
        Map<String, Integer> inDegree = new HashMap<>();
        for (var n : nodes.values()) inDegree.put(n.id(), 0);
        for (var n : nodes.values()) {
            for (String dep : n.dependsOn()) {
                inDegree.merge(n.id(), 1, Integer::sum);
            }
        }

        Queue<String> queue = new ArrayDeque<>();
        for (var entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) queue.add(entry.getKey());
        }

        int visited = 0;
        Map<String, List<String>> successors = new HashMap<>();
        for (var n : nodes.values()) {
            for (String dep : n.dependsOn()) {
                successors.computeIfAbsent(dep, k -> new ArrayList<>()).add(n.id());
            }
        }

        while (!queue.isEmpty()) {
            String current = queue.poll();
            visited++;
            for (String succ : successors.getOrDefault(current, List.of())) {
                int newDegree = inDegree.merge(succ, -1, Integer::sum);
                if (newDegree == 0) queue.add(succ);
            }
        }

        if (visited != nodes.size()) {
            throw new IllegalArgumentException("Plan contains a cycle");
        }
    }

    private static <T> Set<String> computeEntryNodes(Map<String, ExecutionNode<T>> nodes) {
        return nodes.values().stream()
                .filter(n -> n.dependsOn().isEmpty())
                .map(ExecutionNode::id)
                .collect(Collectors.toUnmodifiableSet());
    }
}

package io.casehub.blocks.agentic.social.goal;

import io.casehub.neocortex.cognitive.CognitiveEmotion;
import io.casehub.neocortex.mindmap.MindMapNode;

import java.util.List;

public record CognitiveGoalState(List<GoalEmotion> goals, List<GoalRevision> revisions) {

    public record GoalEmotion(MindMapNode goal, List<CognitiveEmotion> emotions) {}

    public CognitiveGoalState {
        goals = List.copyOf(goals);
        revisions = List.copyOf(revisions);
    }
}

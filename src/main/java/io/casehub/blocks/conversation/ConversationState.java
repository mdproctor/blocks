package io.casehub.blocks.conversation;

import java.util.List;
import java.util.Map;

public record ConversationState(
        Map<String, ConversationPoint> points,
        List<FlagEntry> humanFlags,
        List<RoundMemo> memos,
        Map<String, SubTaskFinding> subTaskFindings
) {
    public ConversationState {
        points = Map.copyOf(points);
        humanFlags = List.copyOf(humanFlags);
        memos = List.copyOf(memos);
        subTaskFindings = Map.copyOf(subTaskFindings);
    }
}

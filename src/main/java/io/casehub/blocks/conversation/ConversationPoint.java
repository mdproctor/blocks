package io.casehub.blocks.conversation;

import java.util.List;

public record ConversationPoint(
        String id,
        PointClassification classification,
        List<ThreadEntry> thread,
        String status
) {
    public ConversationPoint {
        thread = List.copyOf(thread);
    }
}

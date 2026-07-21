package io.casehub.blocks.conversation;

import io.casehub.qhorus.api.message.ReactionGroup;

import java.util.List;
import java.util.Map;

public record RenderContext(
        Map<Long, List<ReactionGroup>> reactions,
        CommonGroundState commonGround,
        ConvergenceSignal convergence) {

    public static final RenderContext EMPTY =
            new RenderContext(Map.of(), null, null);

    public static RenderContext withReactions(Map<Long, List<ReactionGroup>> reactions) {
        return new RenderContext(reactions, null, null);
    }
}

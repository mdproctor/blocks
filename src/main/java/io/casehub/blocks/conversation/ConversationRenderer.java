package io.casehub.blocks.conversation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure renderer: {@link ConversationState} to markdown string.
 *
 * <p>All vocabulary (status emoji, role labels, entry-type labels, priority labels)
 * is config-driven via {@link ConversationRendererConfig}. Unknown values fall back
 * to sensible defaults (raw strings, default emoji).
 *
 * <p>No clock, no mutable state — a pure function.
 */
public class ConversationRenderer {

    private static final String DEFAULT_EMOJI = "⬜"; // ⬜

    private final ConversationRendererConfig config;

    public ConversationRenderer(ConversationRendererConfig config) {
        this.config = config;
    }

    public String render(ConversationState state) {
        var sb = new StringBuilder();
        sb.append("# Conversation Summary\n\n---\n\n");

        // Group points: unresolved first, then escalated, then resolved
        List<ConversationPoint> unresolved = new ArrayList<>();
        List<ConversationPoint> escalated = new ArrayList<>();
        List<ConversationPoint> resolved = new ArrayList<>();

        for (ConversationPoint point : state.points().values()) {
            if (config.resolvedStatuses().contains(point.status())) {
                resolved.add(point);
            } else if (config.escalatedStatuses().contains(point.status())) {
                escalated.add(point);
            } else {
                unresolved.add(point);
            }
        }

        renderPoints(sb, unresolved, state, false);
        renderPoints(sb, escalated, state, false);
        renderPoints(sb, resolved, state, true);

        // Human flags
        if (!state.humanFlags().isEmpty()) {
            sb.append("⚑ **Human review needed:**\n");
            for (FlagEntry flag : state.humanFlags()) {
                sb.append("- ").append(flag.content()).append("\n");
            }
        }

        // Standalone sub-task findings (null pointId)
        List<SubTaskFinding> standaloneFindings = state.subTaskFindings().values().stream()
                .filter(f -> f.pointId() == null)
                .toList();
        if (!standaloneFindings.isEmpty()) {
            sb.append("\n---\n\n**Sub-task findings**\n\n");
            for (SubTaskFinding f : standaloneFindings) {
                sb.append(renderFinding(f));
            }
        }

        // Memos
        if (!state.memos().isEmpty()) {
            sb.append("\n---\n\n**Agent Memos**\n\n");
            for (RoundMemo memo : state.memos()) {
                String roleDisplay = config.roleLabel().getOrDefault(memo.role(), memo.role());
                sb.append("**").append(roleDisplay).append(" memo — Round ").append(memo.round())
                  .append(":** ").append(memo.content()).append("\n\n");
            }
        }

        return sb.toString();
    }

    private void renderPoints(StringBuilder sb, List<ConversationPoint> points,
                               ConversationState state, boolean strikethrough) {
        for (ConversationPoint point : points) {
            String emoji = config.statusEmoji().getOrDefault(point.status(), DEFAULT_EMOJI);
            String firstContent = point.thread().isEmpty() ? "" : point.thread().get(0).content();

            String priorityDisplay = config.priorityLabel()
                    .getOrDefault(point.classification().priority(),
                                  point.classification().priority().toString());

            String header = "[" + point.id() + "] "
                    + priorityDisplay + " · "
                    + point.classification().scope()
                    + (point.classification().location() != null
                       ? " · " + point.classification().location() : "")
                    + " — " + firstContent;

            if (strikethrough) {
                sb.append("## ").append(emoji).append(" ~~").append(header).append("~~\n");
            } else {
                sb.append("## ").append(emoji).append(" ").append(header).append("\n");
            }

            for (ThreadEntry entry : point.thread()) {
                String typeLabel = config.entryTypeLabel()
                        .getOrDefault(entry.entryType(), entry.entryType().toLowerCase());
                String roleDisplay = config.roleLabel()
                        .getOrDefault(entry.role(), entry.role());
                sb.append("> **").append(roleDisplay).append(" (").append(typeLabel).append("):** ")
                  .append(entry.content()).append("\n");
            }

            // Inline sub-task findings for this point
            state.subTaskFindings().values().stream()
                    .filter(f -> point.id().equals(f.pointId()))
                    .forEach(f -> sb.append(renderFinding(f)));

            sb.append("\n---\n\n");
        }
    }

    private String renderFinding(SubTaskFinding f) {
        return switch (f.status()) {
            case PENDING  -> "  ⏳ **" + f.taskType() + "** pending...\n";
            case FAULTED    -> "  ✗ **" + f.taskType() + "** failed: " + f.errorReason() + "\n";
            case COMPLETED -> "  ⊕ **" + f.taskType() + "** _(fresh context — no prior round knowledge)_\n"
                           + "  " + Objects.requireNonNullElse(f.finding(), "(no finding)") + "\n";
            default -> "  **" + f.taskType() + "** " + f.status() + System.lineSeparator();
        };
    }
}

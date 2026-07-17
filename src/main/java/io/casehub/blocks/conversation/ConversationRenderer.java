package io.casehub.blocks.conversation;

public class ConversationRenderer {

    private static final String DEFAULT_EMOJI = "⬜";

    private final ConversationRendererConfig config;

    public ConversationRenderer(ConversationRendererConfig config) {
        this.config = config;
    }

    public String render(ConversationState state) {
        return render(state, java.util.Map.of());
    }

    public String render(ConversationState state, java.util.Map<Long, java.util.List<io.casehub.qhorus.api.message.ReactionGroup>> reactions) {
        var sb = new StringBuilder();
        sb.append("# Conversation Summary\n\n---\n\n");

        if (config.groupByTopic()) {
            renderByTopic(sb, state, reactions);
        } else {
            renderFlat(sb, state, reactions);
        }

        if (!state.humanFlags().isEmpty()) {
            sb.append("⚑ **Human review needed:**\n");
            for (FlagEntry flag : state.humanFlags()) {
                sb.append("- ").append(flag.content()).append("\n");
            }
        }

        java.util.List<SubTaskFinding> standaloneFindings = state.subTaskFindings().values().stream()
                                                                 .filter(f -> f.pointId() == null)
                                                                 .toList();
        if (!standaloneFindings.isEmpty()) {
            sb.append("\n---\n\n**Sub-task findings**\n\n");
            for (SubTaskFinding f : standaloneFindings) {
                sb.append(renderFinding(f));
            }
        }

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

    private void renderFlat(StringBuilder sb, ConversationState state,
                            java.util.Map<Long, java.util.List<io.casehub.qhorus.api.message.ReactionGroup>> reactions) {
        java.util.List<ConversationPoint> unresolved = new java.util.ArrayList<>();
        java.util.List<ConversationPoint> escalated  = new java.util.ArrayList<>();
        java.util.List<ConversationPoint> resolved   = new java.util.ArrayList<>();

        for (ConversationPoint point : state.points().values()) {
            if (config.resolvedStatuses().contains(point.status())) {
                resolved.add(point);
            } else if (config.escalatedStatuses().contains(point.status())) {
                escalated.add(point);
            } else {
                unresolved.add(point);
            }
        }

        renderPoints(sb, unresolved, state, false, reactions);
        renderPoints(sb, escalated, state, false, reactions);
        renderPoints(sb, resolved, state, true, reactions);
    }

    private void renderByTopic(StringBuilder sb, ConversationState state,
                               java.util.Map<Long, java.util.List<io.casehub.qhorus.api.message.ReactionGroup>> reactions) {
        var topicOrder = new java.util.LinkedHashMap<String, java.util.List<ConversationPoint>>();
        for (ConversationPoint point : state.points().values()) {
            String topic = point.topic() != null ? point.topic() : "general";
            topicOrder.computeIfAbsent(topic, k -> new java.util.ArrayList<>()).add(point);
        }

        for (var entry : topicOrder.entrySet()) {
            sb.append("## ").append(entry.getKey()).append("\n\n");

            if (config.showObligationChain()) {
                String chain = renderObligationChain(entry.getValue());
                if (!chain.isEmpty()) {
                    sb.append(chain).append("\n\n");
                }
            }

            java.util.List<ConversationPoint> unresolved = new java.util.ArrayList<>();
            java.util.List<ConversationPoint> escalated  = new java.util.ArrayList<>();
            java.util.List<ConversationPoint> resolved   = new java.util.ArrayList<>();

            for (ConversationPoint point : entry.getValue()) {
                if (config.resolvedStatuses().contains(point.status())) {
                    resolved.add(point);
                } else if (config.escalatedStatuses().contains(point.status())) {
                    escalated.add(point);
                } else {
                    unresolved.add(point);
                }
            }

            renderPoints(sb, unresolved, state, false, reactions);
            renderPoints(sb, escalated, state, false, reactions);
            renderPoints(sb, resolved, state, true, reactions);
        }
    }

    private String renderObligationChain(java.util.List<ConversationPoint> points) {
        java.util.List<io.casehub.qhorus.api.message.MessageType> types = new java.util.ArrayList<>();
        for (ConversationPoint point : points) {
            for (ThreadEntry entry : point.thread()) {
                if (entry.messageType() != null) {
                    types.add(entry.messageType());
                }
            }
        }
        if (types.isEmpty()) {return "";}

        var labels = new java.util.ArrayList<String>();
        for (var type : types) {
            labels.add(config.messageTypeLabel().getOrDefault(type, type.name().toLowerCase()));
        }
        String chain = String.join(" → ", labels);
        if (types.get(types.size() - 1) == io.casehub.qhorus.api.message.MessageType.DONE) {
            chain += " ✓";
        } else {
            chain += " ⏳";
        }
        return chain;
    }

    private void renderPoints(StringBuilder sb, java.util.List<ConversationPoint> points,
                              ConversationState state, boolean strikethrough,
                              java.util.Map<Long, java.util.List<io.casehub.qhorus.api.message.ReactionGroup>> reactions) {
        for (ConversationPoint point : points) {
            String emoji        = config.statusEmoji().getOrDefault(point.status(), DEFAULT_EMOJI);
            String firstContent = point.thread().isEmpty() ? "" : point.thread().get(0).content();

            String priorityDisplay = config.priorityLabel()
                                           .getOrDefault(point.classification().priority(),
                                                         point.classification().priority() != null
                                                         ? point.classification().priority().toString() : "");

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

                if (entry.messageId() != null && reactions.containsKey(entry.messageId())) {
                    var groups = reactions.get(entry.messageId());
                    if (!groups.isEmpty()) {
                        sb.append("> ");
                        for (int i = 0; i < groups.size(); i++) {
                            if (i > 0) {sb.append(" ");}
                            sb.append(groups.get(i).emoji()).append("×").append(groups.get(i).count());
                        }
                        sb.append("\n");
                    }
                }
            }

            state.subTaskFindings().values().stream()
                 .filter(f -> point.id().equals(f.pointId()))
                 .forEach(f -> sb.append(renderFinding(f)));

            sb.append("\n---\n\n");
        }
    }

    private String renderFinding(SubTaskFinding f) {
        return switch (f.status()) {
            case PENDING -> "  ⏳ **" + f.taskType() + "** pending...\n";
            case FAULTED -> "  ✗ **" + f.taskType() + "** failed: " + f.errorReason() + "\n";
            case COMPLETED -> "  ⊕ **" + f.taskType() + "** _(fresh context — no prior round knowledge)_\n"
                              + "  " + java.util.Objects.requireNonNullElse(f.finding(), "(no finding)") + "\n";
            default -> "  **" + f.taskType() + "** " + f.status() + System.lineSeparator();
        };
    }
}

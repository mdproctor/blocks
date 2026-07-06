package io.casehub.blocks.routing.agent;

import io.casehub.api.spi.routing.AgentCandidate;
import io.casehub.api.spi.routing.AgentRoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

@ApplicationScoped
public class RoutingPromptAssembler {

    private static final System.Logger LOG =
            System.getLogger(RoutingPromptAssembler.class.getName());

    private final List<RoutingPromptSection> sections;

    @Inject
    public RoutingPromptAssembler(Instance<RoutingPromptSection> sections) {
        this.sections = sections.stream()
                .sorted(Comparator.comparingInt(RoutingPromptAssembler::priority))
                .toList();
    }

    public RoutingPromptAssembler(List<RoutingPromptSection> sections) {
        this.sections = sections.stream()
                .sorted(Comparator.comparingInt(RoutingPromptAssembler::priority))
                .toList();
    }

    public @Nullable String assemble(AgentRoutingContext context,
                                      List<AgentCandidate> eligible) {
        var sb = new StringBuilder();
        for (var section : sections) {
            try {
                String rendered = section.render(context, eligible);
                if (rendered != null && !rendered.isBlank()) {
                    if (!sb.isEmpty()) sb.append("\n\n");
                    sb.append(rendered);
                }
            } catch (Exception e) {
                LOG.log(System.Logger.Level.WARNING,
                        "RoutingPromptSection threw — skipping: "
                                + section.getClass().getName(), e);
            }
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private static int priority(RoutingPromptSection section) {
        var annotation = section.getClass().getAnnotation(jakarta.annotation.Priority.class);
        return annotation != null ? annotation.value() : Integer.MAX_VALUE;
    }
}

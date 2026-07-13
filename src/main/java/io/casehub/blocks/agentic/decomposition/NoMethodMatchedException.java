package io.casehub.blocks.agentic.decomposition;

public class NoMethodMatchedException extends IllegalStateException {

    private final String taskName;

    public NoMethodMatchedException(String taskName) {
        super("No decomposition method guard matched for task '" + taskName + "'");
        this.taskName = taskName;
    }

    public String taskName() {
        return taskName;
    }
}

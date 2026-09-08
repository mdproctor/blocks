package io.casehub.blocks.agentic.yaml.runtime;

import io.casehub.blocks.agentic.yaml.compiler.PatternCompiler;
import io.casehub.platform.api.expression.ExpressionEngine;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class AgenticRecorder {

    public PatternCompiler createCompiler(ExpressionEngine expressionEngine) {
        return new PatternCompiler(expressionEngine);
    }
}

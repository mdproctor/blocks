package io.casehub.blocks.agentic.yaml.examples;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.blocks.agentic.yaml.compiler.CognitionCompiler;
import io.casehub.blocks.agentic.yaml.compiler.ObservationFilterRegistry;
import io.casehub.blocks.agentic.yaml.compiler.PatternCompiler;
import io.casehub.blocks.agentic.yaml.compiler.WorldCompiler;
import io.casehub.blocks.agentic.yaml.spec.PatternSpec;
import io.casehub.blocks.agentic.yaml.spec.cognition.CognitionDefinition;
import io.casehub.blocks.agentic.yaml.spec.world.WorldDefinition;
import io.casehub.blocks.summarisation.yaml.PipelineCompiler;
import io.casehub.blocks.summarisation.yaml.PipelineDefinition;
import io.casehub.blocks.summarisation.yaml.SummariserFactory;
import io.casehub.blocks.summarisation.yaml.SummariserRegistry;
import io.casehub.blocks.summarisation.yaml.builtin.CountSummariser;
import io.casehub.blocks.summarisation.yaml.builtin.PhaseDetectSummariser;
import io.casehub.blocks.summarisation.yaml.builtin.ThresholdClassifySummariser;
import io.casehub.platform.api.expression.CompiledExpression;
import io.casehub.platform.api.expression.ExpressionEngine;
import io.casehub.platform.expression.MvelExpressionEngine;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

abstract class ExampleTestBase {

    protected ObjectMapper mapper;
    protected PatternCompiler patternCompiler;
    protected CognitionCompiler cognitionCompiler;
    protected WorldCompiler worldCompiler;
    protected PipelineCompiler pipelineCompiler;
    protected SummariserRegistry summariserRegistry;
    protected MvelExpressionEngine expressionEngine;
    protected ExpressionEngine pipelineExpressionEngine;

    @BeforeEach
    void setUpBase() {
        mapper = new ObjectMapper(new YAMLFactory());
        mapper.registerModule(new JavaTimeModule());
        expressionEngine = new MvelExpressionEngine();
        pipelineExpressionEngine = stubExpressionEngine();
        patternCompiler = new PatternCompiler(expressionEngine);
        cognitionCompiler = new CognitionCompiler();
        worldCompiler = new WorldCompiler(new ObservationFilterRegistry());
        pipelineCompiler = new PipelineCompiler();
        summariserRegistry = new SummariserRegistry();
        summariserRegistry.register("threshold-classify", (SummariserFactory) config -> ThresholdClassifySummariser.create(config, pipelineExpressionEngine));
        summariserRegistry.register("phase-detect", (SummariserFactory) config -> PhaseDetectSummariser.create(config, List.of()));
        summariserRegistry.register("count", (SummariserFactory) config -> CountSummariser.create(config));
    }

    @SuppressWarnings("unchecked")
    private static ExpressionEngine stubExpressionEngine() {
        return new ExpressionEngine() {
            @Override public String type() { return "stub"; }
            @Override public <C, R> CompiledExpression<C, R> compile(String expr, Class<C> ct, Class<R> rt) {
                return new CompiledExpression<>() {
                    @Override public String type() { return "stub"; }
                    @Override public R eval(C context) {
                        if (context instanceof Map<?, ?> m) return (R) m.get(expr);
                        if (context instanceof List<?> l) return (R) Boolean.valueOf(l.size() >= 3);
                        return (R) context;
                    }
                };
            }
            @Override public <C, R> CompiledExpression<C, R> compile(String expr, Class<C> ct, Class<R> rt, Map<String, Object> vars) {
                return compile(expr, ct, rt);
            }
            @Override public void validate(String expr) {}
        };
    }

    protected PatternSpec loadPattern(String scenario) throws IOException {
        return load(scenario, "pattern.yaml", PatternSpec.class);
    }

    protected CognitionDefinition loadCognition(String scenario) throws IOException {
        return load(scenario, "cognition.yaml", CognitionDefinition.class);
    }

    protected WorldDefinition loadWorld(String scenario) throws IOException {
        return load(scenario, "world.yaml", WorldDefinition.class);
    }

    protected PipelineDefinition loadPipeline(String scenario) throws IOException {
        var wrapper = load(scenario, "pipeline.yaml", PipelineWrapper.class);
        return wrapper.pipeline();
    }

    record PipelineWrapper(PipelineDefinition pipeline) {}

    protected <T> T load(String scenario, String file, Class<T> type) throws IOException {
        String path = "/examples/" + scenario + "/" + file;
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is == null) throw new IOException("Resource not found: " + path);
            return mapper.readValue(is, type);
        }
    }
}

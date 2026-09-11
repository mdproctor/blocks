package io.casehub.blocks.agentic.yaml.registry;

import io.casehub.blocks.agentic.yaml.spec.SherpaConfigSpec;
import io.casehub.blocks.speech.sherpa.SherpaConfig;

import java.nio.file.Path;

public class SherpaConfigRegistry {
    public SherpaConfig resolve(SherpaConfigSpec spec) {
        var config = new SherpaConfig(
                Path.of(spec.modelDir()), spec.numThreads(), spec.provider());
        if (spec.punctuationModelDir() != null) {
            config = config.withPunctuation(Path.of(spec.punctuationModelDir()));
        }
        return config;
    }
}

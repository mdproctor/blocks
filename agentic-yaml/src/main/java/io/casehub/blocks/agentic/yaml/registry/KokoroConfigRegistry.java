package io.casehub.blocks.agentic.yaml.registry;

import io.casehub.blocks.agentic.yaml.spec.KokoroConfigSpec;
import io.casehub.blocks.speech.sherpa.KokoroConfig;

import java.nio.file.Path;

public class KokoroConfigRegistry {
    public KokoroConfig resolve(KokoroConfigSpec spec) {
        return new KokoroConfig(
                Path.of(spec.modelDir()), spec.voiceId(),
                spec.lengthScale(), spec.numThreads(), spec.provider());
    }
}

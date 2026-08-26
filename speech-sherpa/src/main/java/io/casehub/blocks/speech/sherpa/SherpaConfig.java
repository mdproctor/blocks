package io.casehub.blocks.speech.sherpa;

import java.nio.file.Path;
import java.util.Objects;

public record SherpaConfig(Path modelDir, int numThreads, String provider,
                           @org.jspecify.annotations.Nullable Path punctuationModelDir) {
    public SherpaConfig {
        Objects.requireNonNull(modelDir, "modelDir");
        Objects.requireNonNull(provider, "provider");
        if (numThreads <= 0) {throw new IllegalArgumentException("numThreads must be positive: " + numThreads);}
    }

    public SherpaConfig(Path modelDir, int numThreads, String provider) {
        this(modelDir, numThreads, provider, null);
    }

    public static SherpaConfig defaults(Path modelDir) {
        return new SherpaConfig(modelDir, 2, "cpu", null);
    }

    public SherpaConfig withPunctuation(Path punctuationModelDir) {
        return new SherpaConfig(modelDir, numThreads, provider, punctuationModelDir);
    }
}

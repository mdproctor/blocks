package io.casehub.blocks.speech.sherpa;

import io.casehub.blocks.speech.TextCleanupService;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public final class SherpaTextCleanup implements TextCleanupService {

    private static final Set<String> FILLERS = Set.of(
            "um", "uh", "uh huh", "uhh", "umm", "er", "err", "hmm", "hm",
            "ah", "ahh", "oh", "ohh", "eh", "mhm", "mm");

    private static final Pattern FILLER_PATTERN = Pattern.compile(
            "\\b(?:um+|uh+|er+|hm+|ah+|oh+|eh+|mhm)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern MULTI_SPACE = Pattern.compile("\\s{2,}");

    private final SherpaLibrary lib;
    private final MemorySegment punct;
    private final Arena punctArena;

    public SherpaTextCleanup(Path modelDir) {
        this(modelDir, SherpaLibrary.load());
    }

    SherpaTextCleanup(Path modelDir, SherpaLibrary lib) {
        this.lib = Objects.requireNonNull(lib);
        this.punctArena = Arena.ofShared();

        MemorySegment config = punctArena.allocate(SherpaLayouts.CONFIG_ALLOC_SIZE);
        config.fill((byte) 0);

        // OnlinePunctuationModelConfig: cnn_bilstm at 0, bpe_vocab at 8, num_threads at 16
        config.set(ValueLayout.ADDRESS, 0,
                punctArena.allocateFrom(modelDir.resolve("model.onnx").toString()));
        config.set(ValueLayout.ADDRESS, 8,
                punctArena.allocateFrom(modelDir.resolve("bpe.vocab").toString()));
        config.set(ValueLayout.JAVA_INT, 16, 2);
        config.set(ValueLayout.ADDRESS, 24, punctArena.allocateFrom("cpu"));

        try {
            this.punct = (MemorySegment) lib.createOnlinePunctuation.invokeExact(config);
        } catch (Throwable t) {
            punctArena.close();
            throw new SherpaException("Failed to create punctuation model", t);
        }
        if (punct.equals(MemorySegment.NULL)) {
            punctArena.close();
            throw new SherpaException("sherpa-onnx returned null punctuation — check model paths in " + modelDir);
        }
    }

    @Override
    public String cleanup(String rawText) {
        if (rawText == null || rawText.isBlank()) {return rawText;}

        String lower    = rawText.toLowerCase();
        String stripped = FILLER_PATTERN.matcher(lower).replaceAll("");
        stripped = MULTI_SPACE.matcher(stripped).replaceAll(" ").trim();

        if (stripped.isEmpty()) {return "";}

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment textSeg = arena.allocateFrom(stripped);
            MemorySegment resultPtr;
            try {
                resultPtr = (MemorySegment) lib.onlinePunctuationAddPunct.invokeExact(punct, textSeg);
            } catch (Throwable t) {
                throw new SherpaException("Punctuation failed", t);
            }
            try {
                return resultPtr.reinterpret(Long.MAX_VALUE).getString(0);
            } finally {
                try {lib.onlinePunctuationFreeText.invokeExact(resultPtr);} catch (Throwable t) { /* cleanup */ }
            }
        }
    }

    public void close() {
        try { lib.destroyOnlinePunctuation.invokeExact(punct); } catch (Throwable t) { /* cleanup */ }
        punctArena.close();
    }
}

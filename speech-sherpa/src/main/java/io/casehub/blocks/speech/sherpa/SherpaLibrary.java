package io.casehub.blocks.speech.sherpa;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;
import java.util.Optional;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;

final class SherpaLibrary {

    private static volatile SherpaLibrary INSTANCE;

    private final SymbolLookup lookup;

    // STT
    final MethodHandle createRecognizer;
    final MethodHandle destroyRecognizer;
    final MethodHandle createStream;
    final MethodHandle destroyStream;
    final MethodHandle acceptWaveform;
    final MethodHandle decodeStream;
    final MethodHandle getResult;
    final MethodHandle destroyResult;

    // TTS
    final MethodHandle createTts;
    final MethodHandle destroyTts;
    final MethodHandle ttsGenerate;
    final MethodHandle destroyGeneratedAudio;

    private SherpaLibrary(SymbolLookup lookup) {
        this.lookup = lookup;
        Linker linker = Linker.nativeLinker();

        // STT handles
        createRecognizer = downcall(linker, "SherpaOnnxCreateOfflineRecognizer",
                FunctionDescriptor.of(ADDRESS, ADDRESS));
        destroyRecognizer = downcall(linker, "SherpaOnnxDestroyOfflineRecognizer",
                FunctionDescriptor.ofVoid(ADDRESS));
        createStream = downcall(linker, "SherpaOnnxCreateOfflineStream",
                FunctionDescriptor.of(ADDRESS, ADDRESS));
        destroyStream = downcall(linker, "SherpaOnnxDestroyOfflineStream",
                FunctionDescriptor.ofVoid(ADDRESS));
        acceptWaveform = downcall(linker, "SherpaOnnxAcceptWaveformOffline",
                FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT, ADDRESS, JAVA_INT));
        decodeStream = downcall(linker, "SherpaOnnxDecodeOfflineStream",
                FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));
        getResult = downcall(linker, "SherpaOnnxGetOfflineStreamResult",
                FunctionDescriptor.of(ADDRESS, ADDRESS));
        destroyResult = downcall(linker, "SherpaOnnxDestroyOfflineRecognizerResult",
                FunctionDescriptor.ofVoid(ADDRESS));

        // TTS handles
        createTts = downcall(linker, "SherpaOnnxCreateOfflineTts",
                FunctionDescriptor.of(ADDRESS, ADDRESS));
        destroyTts = downcall(linker, "SherpaOnnxDestroyOfflineTts",
                FunctionDescriptor.ofVoid(ADDRESS));
        ttsGenerate = downcall(linker, "SherpaOnnxOfflineTtsGenerate",
                FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, JAVA_INT, JAVA_FLOAT));
        destroyGeneratedAudio = downcall(linker, "SherpaOnnxDestroyOfflineTtsGeneratedAudio",
                FunctionDescriptor.ofVoid(ADDRESS));
    }

    static SherpaLibrary load() {
        if (INSTANCE != null) return INSTANCE;
        synchronized (SherpaLibrary.class) {
            if (INSTANCE != null) return INSTANCE;
            SymbolLookup lookup = SymbolLookup.libraryLookup("sherpa-onnx-c-api", Arena.global());
            INSTANCE = new SherpaLibrary(lookup);
            return INSTANCE;
        }
    }

    static SherpaLibrary load(Path libraryPath) {
        SymbolLookup lookup = SymbolLookup.libraryLookup(libraryPath, Arena.global());
        return new SherpaLibrary(lookup);
    }

    static boolean isAvailable() {
        try {
            load();
            return true;
        } catch (UnsatisfiedLinkError | IllegalArgumentException e) {
            return false;
        }
    }

    private MethodHandle downcall(Linker linker, String name, FunctionDescriptor descriptor) {
        Optional<MemorySegment> symbol = lookup.find(name);
        if (symbol.isEmpty()) {
            throw new UnsatisfiedLinkError("Symbol not found in sherpa-onnx: " + name);
        }
        return linker.downcallHandle(symbol.get(), descriptor);
    }
}

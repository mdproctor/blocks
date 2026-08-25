package io.casehub.blocks.speech.sherpa;

import io.casehub.blocks.speech.SynthesisOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SherpaOnnxTextToSpeechTest {

    @TempDir Path tempDir;

    @Test
    void rejectsNullText() {
        var tts = new SherpaOnnxTextToSpeech(SherpaConfig.defaults(tempDir), (SherpaLibrary) null);

        assertThatThrownBy(() -> tts.synthesise(null, SynthesisOptions.defaults()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullOptions() {
        var tts = new SherpaOnnxTextToSpeech(SherpaConfig.defaults(tempDir), (SherpaLibrary) null);

        assertThatThrownBy(() -> tts.synthesise("hello", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @EnabledIf("io.casehub.blocks.speech.sherpa.SherpaLibrary#isAvailable")
    void synthesisesWithSherpa() {
        Path modelDir = Path.of(System.getProperty("sherpa.model.dir", "/usr/local/share/sherpa-onnx/models"));
        var config = SherpaConfig.defaults(modelDir);
        var tts = new SherpaOnnxTextToSpeech(config);

        var result = tts.synthesise("Hello world", SynthesisOptions.defaults());
        assertThat(result).isNotNull();
        assertThat(result.audioData()).isNotEmpty();
        assertThat(result.audioFormat()).isEqualTo("wav");
    }
}

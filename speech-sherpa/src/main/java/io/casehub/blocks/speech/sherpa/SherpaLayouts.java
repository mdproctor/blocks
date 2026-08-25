package io.casehub.blocks.speech.sherpa;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.invoke.VarHandle;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;

// Struct layouts for sherpa-onnx C API (targets 1.10.x on 64-bit).
// All fields must match c-api.h exactly — wrong sizes cause segfaults.
final class SherpaLayouts {

    private SherpaLayouts() {}

    // === Primitive sub-structs ===

    static final GroupLayout FEATURE_CONFIG = MemoryLayout.structLayout(
            JAVA_INT.withName("sample_rate"),
            JAVA_INT.withName("feature_dim")
    );

    static final GroupLayout TRANSDUCER_MODEL = MemoryLayout.structLayout(
            ADDRESS.withName("encoder"),
            ADDRESS.withName("decoder"),
            ADDRESS.withName("joiner")
    );

    static final GroupLayout PARAFORMER_MODEL = MemoryLayout.structLayout(
            ADDRESS.withName("model")
    );

    static final GroupLayout NEMO_CTC_MODEL = MemoryLayout.structLayout(
            ADDRESS.withName("model")
    );

    static final GroupLayout WHISPER_MODEL = MemoryLayout.structLayout(
            ADDRESS.withName("decoder"),
            ADDRESS.withName("encoder"),
            ADDRESS.withName("language"),
            ADDRESS.withName("task"),
            JAVA_INT.withName("tail_paddings"),
            MemoryLayout.paddingLayout(4)
    );

    static final GroupLayout TDNN_MODEL = MemoryLayout.structLayout(
            ADDRESS.withName("model")
    );

    static final GroupLayout SENSE_VOICE_MODEL = MemoryLayout.structLayout(
            ADDRESS.withName("model"),
            ADDRESS.withName("language"),
            JAVA_INT.withName("use_itn"),
            MemoryLayout.paddingLayout(4)
    );

    static final GroupLayout MOONSHINE_MODEL = MemoryLayout.structLayout(
            ADDRESS.withName("preprocessor"),
            ADDRESS.withName("encoder"),
            ADDRESS.withName("uncached_decoder"),
            ADDRESS.withName("cached_decoder")
    );

    // === Composite configs ===

    static final GroupLayout OFFLINE_MODEL_CONFIG = MemoryLayout.structLayout(
            TRANSDUCER_MODEL.withName("transducer"),
            PARAFORMER_MODEL.withName("paraformer"),
            NEMO_CTC_MODEL.withName("nemo_ctc"),
            WHISPER_MODEL.withName("whisper"),
            TDNN_MODEL.withName("tdnn"),
            ADDRESS.withName("tokens"),
            JAVA_INT.withName("num_threads"),
            JAVA_INT.withName("debug"),
            ADDRESS.withName("provider"),
            ADDRESS.withName("model_type"),
            ADDRESS.withName("modeling_unit"),
            ADDRESS.withName("bpe_vocab"),
            ADDRESS.withName("telespeech_ctc"),
            SENSE_VOICE_MODEL.withName("sense_voice"),
            MOONSHINE_MODEL.withName("moonshine")
    );

    static final GroupLayout LM_CONFIG = MemoryLayout.structLayout(
            ADDRESS.withName("model"),
            JAVA_FLOAT.withName("scale"),
            MemoryLayout.paddingLayout(4)
    );

    static final GroupLayout OFFLINE_RECOGNIZER_CONFIG = MemoryLayout.structLayout(
            FEATURE_CONFIG.withName("feat_config"),
            OFFLINE_MODEL_CONFIG.withName("model_config"),
            LM_CONFIG.withName("lm_config"),
            ADDRESS.withName("decoding_method"),
            JAVA_INT.withName("max_active_paths"),
            MemoryLayout.paddingLayout(4),
            ADDRESS.withName("hotwords_file"),
            JAVA_FLOAT.withName("hotwords_score"),
            MemoryLayout.paddingLayout(4),
            ADDRESS.withName("rule_fsts"),
            ADDRESS.withName("rule_fars"),
            JAVA_FLOAT.withName("blank_penalty"),
            MemoryLayout.paddingLayout(4)
    );

    // === TTS structs ===

    static final GroupLayout VITS_MODEL = MemoryLayout.structLayout(
            ADDRESS.withName("model"),
            ADDRESS.withName("lexicon"),
            ADDRESS.withName("tokens"),
            ADDRESS.withName("data_dir"),
            JAVA_FLOAT.withName("noise_scale"),
            JAVA_FLOAT.withName("noise_scale_w"),
            JAVA_FLOAT.withName("length_scale"),
            MemoryLayout.paddingLayout(4),
            ADDRESS.withName("dict_dir")
    );

    static final GroupLayout TTS_MODEL_CONFIG = MemoryLayout.structLayout(
            VITS_MODEL.withName("vits"),
            JAVA_INT.withName("num_threads"),
            JAVA_INT.withName("debug"),
            ADDRESS.withName("provider")
    );

    static final GroupLayout TTS_CONFIG = MemoryLayout.structLayout(
            TTS_MODEL_CONFIG.withName("model"),
            ADDRESS.withName("rule_fsts"),
            JAVA_INT.withName("max_num_sentences"),
            MemoryLayout.paddingLayout(4),
            ADDRESS.withName("rule_fars")
    );

    // === Result structs ===

    static final GroupLayout GENERATED_AUDIO = MemoryLayout.structLayout(
            ADDRESS.withName("samples"),
            JAVA_INT.withName("n"),
            JAVA_INT.withName("sample_rate")
    );

    // === VarHandles for frequently accessed fields ===

    // STT config: feat_config.sample_rate
    static final VarHandle FEAT_SAMPLE_RATE = OFFLINE_RECOGNIZER_CONFIG.varHandle(
            groupElement("feat_config"), groupElement("sample_rate"));

    // STT config: feat_config.feature_dim
    static final VarHandle FEAT_FEATURE_DIM = OFFLINE_RECOGNIZER_CONFIG.varHandle(
            groupElement("feat_config"), groupElement("feature_dim"));

    // STT config: model_config.whisper.encoder
    static final VarHandle WHISPER_ENCODER = OFFLINE_RECOGNIZER_CONFIG.varHandle(
            groupElement("model_config"), groupElement("whisper"), groupElement("encoder"));

    // STT config: model_config.whisper.decoder
    static final VarHandle WHISPER_DECODER = OFFLINE_RECOGNIZER_CONFIG.varHandle(
            groupElement("model_config"), groupElement("whisper"), groupElement("decoder"));

    // STT config: model_config.whisper.language
    static final VarHandle WHISPER_LANGUAGE = OFFLINE_RECOGNIZER_CONFIG.varHandle(
            groupElement("model_config"), groupElement("whisper"), groupElement("language"));

    // STT config: model_config.tokens
    static final VarHandle MODEL_TOKENS = OFFLINE_RECOGNIZER_CONFIG.varHandle(
            groupElement("model_config"), groupElement("tokens"));

    // STT config: model_config.num_threads
    static final VarHandle MODEL_NUM_THREADS = OFFLINE_RECOGNIZER_CONFIG.varHandle(
            groupElement("model_config"), groupElement("num_threads"));

    // STT config: model_config.provider
    static final VarHandle MODEL_PROVIDER = OFFLINE_RECOGNIZER_CONFIG.varHandle(
            groupElement("model_config"), groupElement("provider"));

    // TTS config: model.vits.model
    static final VarHandle VITS_MODEL_PATH = TTS_CONFIG.varHandle(
            groupElement("model"), groupElement("vits"), groupElement("model"));

    // TTS config: model.vits.tokens
    static final VarHandle VITS_TOKENS = TTS_CONFIG.varHandle(
            groupElement("model"), groupElement("vits"), groupElement("tokens"));

    // TTS config: model.vits.data_dir
    static final VarHandle VITS_DATA_DIR = TTS_CONFIG.varHandle(
            groupElement("model"), groupElement("vits"), groupElement("data_dir"));

    // TTS config: model.vits.length_scale
    static final VarHandle VITS_LENGTH_SCALE = TTS_CONFIG.varHandle(
            groupElement("model"), groupElement("vits"), groupElement("length_scale"));

    // TTS config: model.num_threads
    static final VarHandle TTS_NUM_THREADS = TTS_CONFIG.varHandle(
            groupElement("model"), groupElement("num_threads"));

    // TTS config: model.provider
    static final VarHandle TTS_PROVIDER = TTS_CONFIG.varHandle(
            groupElement("model"), groupElement("provider"));

    // Generated audio: samples pointer
    static final VarHandle AUDIO_SAMPLES = GENERATED_AUDIO.varHandle(
            groupElement("samples"));

    // Generated audio: sample count
    static final VarHandle AUDIO_N = GENERATED_AUDIO.varHandle(
            groupElement("n"));

    // Generated audio: sample rate
    static final VarHandle AUDIO_SAMPLE_RATE = GENERATED_AUDIO.varHandle(
            groupElement("sample_rate"));
}

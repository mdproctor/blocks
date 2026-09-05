package io.casehub.blocks.summarisation.yaml;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record SummariserDefinition(String type, Map<String, Object> config) {

    @JsonCreator
    static SummariserDefinition fromJson(
            @JsonProperty("type") String type,
            @JsonAnySetter Map<String, Object> config) {
        return new SummariserDefinition(type, config != null ? Collections.unmodifiableMap(config) : Map.of());
    }
}

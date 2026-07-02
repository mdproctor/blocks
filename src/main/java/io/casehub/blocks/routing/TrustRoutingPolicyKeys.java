package io.casehub.blocks.routing;

import io.casehub.platform.api.preferences.PreferenceKey;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parameterised PreferenceKey definitions for trust routing YAML configuration.
 *
 * <p>The four universal keys (threshold, minimum-observations, borderline-margin, blend-factor)
 * are created from the scope prefix. Domain-specific quality floor keys are added via
 * {@link #withFloor(String, String)}.
 *
 * <p>Resolved at scope: {@code <scopePrefix>/<capabilityName>} by PreferenceProvider.
 */
public final class TrustRoutingPolicyKeys {

    private final PreferenceKey<DoublePreference> threshold;
    private final PreferenceKey<IntPreference> minimumObservations;
    private final PreferenceKey<DoublePreference> borderlineMargin;
    private final PreferenceKey<DoublePreference> blendFactor;
    private final Map<String, PreferenceKey<DoublePreference>> floorKeys;

    private final String scopePrefix;

    private TrustRoutingPolicyKeys(String scopePrefix,
                                   Map<String, PreferenceKey<DoublePreference>> floorKeys) {
        this.scopePrefix = scopePrefix;
        this.threshold = new PreferenceKey<>(scopePrefix, "threshold",
                DoublePreference.of(0.0), DoublePreference::parse);
        this.minimumObservations = new PreferenceKey<>(scopePrefix, "minimum-observations",
                IntPreference.of(0), IntPreference::parse);
        this.borderlineMargin = new PreferenceKey<>(scopePrefix, "borderline-margin",
                DoublePreference.of(0.0), DoublePreference::parse);
        this.blendFactor = new PreferenceKey<>(scopePrefix, "blend-factor",
                DoublePreference.of(0.0), DoublePreference::parse);
        this.floorKeys = Collections.unmodifiableMap(floorKeys);
    }

    public static TrustRoutingPolicyKeys create(String scopePrefix) {
        return new TrustRoutingPolicyKeys(scopePrefix, Map.of());
    }

    public TrustRoutingPolicyKeys withFloor(String dimension, String keySuffix) {
        var newFloors = new LinkedHashMap<>(floorKeys);
        newFloors.put(dimension, new PreferenceKey<>(
                scopePrefix, "floor." + keySuffix,
                DoublePreference.of(0.0), DoublePreference::parse));
        return new TrustRoutingPolicyKeys(scopePrefix, newFloors);
    }

    public PreferenceKey<DoublePreference> threshold() { return threshold; }
    public PreferenceKey<IntPreference> minimumObservations() { return minimumObservations; }
    public PreferenceKey<DoublePreference> borderlineMargin() { return borderlineMargin; }
    public PreferenceKey<DoublePreference> blendFactor() { return blendFactor; }
    public Map<String, PreferenceKey<DoublePreference>> allFloorKeys() { return floorKeys; }
}

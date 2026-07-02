package io.casehub.blocks.routing;

import io.casehub.api.spi.routing.TrustRoutingPolicy;
import io.casehub.platform.api.preferences.PreferenceKey;
import io.casehub.platform.api.preferences.Preferences;
import java.util.HashMap;
import java.util.Map;

/**
 * Resolves a {@link TrustRoutingPolicy} from {@link Preferences} using
 * {@link TrustRoutingPolicyKeys}. Eliminates the duplicated preference-to-policy
 * parsing logic across domain repos.
 */
public final class TrustRoutingPolicyResolver {

    private TrustRoutingPolicyResolver() {}

    public static TrustRoutingPolicy resolve(Preferences prefs, TrustRoutingPolicyKeys keys) {
        return resolve(prefs, keys, TrustRoutingPolicy.DEFAULT.bootstrapEscalationRequired());
    }

    public static TrustRoutingPolicy resolve(Preferences prefs, TrustRoutingPolicyKeys keys,
                                             boolean bootstrapEscalationRequired) {
        DoublePreference threshold = prefs.get(keys.threshold());
        if (threshold == null) {
            return TrustRoutingPolicy.DEFAULT;
        }

        IntPreference minObs = prefs.get(keys.minimumObservations());
        DoublePreference margin = prefs.get(keys.borderlineMargin());
        DoublePreference blend = prefs.get(keys.blendFactor());

        Map<String, Double> qualityFloors = collectFloors(prefs, keys.allFloorKeys());

        return new TrustRoutingPolicy(
                threshold.value(),
                minObs != null ? minObs.value() : TrustRoutingPolicy.DEFAULT.minimumObservations(),
                margin != null ? margin.value() : TrustRoutingPolicy.DEFAULT.borderlineMargin(),
                blend != null ? blend.value() : TrustRoutingPolicy.DEFAULT.blendFactor(),
                Map.copyOf(qualityFloors),
                bootstrapEscalationRequired,
                TrustRoutingPolicy.DEFAULT.fallbackBinding());
    }

    /**
     * Collects quality floors from preferences, skipping absent or zero-valued floors.
     * Useful for hybrid providers that read some fields from a domain registry and
     * only the floors from preferences.
     */
    public static Map<String, Double> collectFloors(Preferences prefs,
                                                    Map<String, PreferenceKey<DoublePreference>> floorKeys) {
        Map<String, Double> floors = new HashMap<>();
        floorKeys.forEach((dimension, key) -> {
            DoublePreference value = prefs.get(key);
            if (value != null && value.value() > 0.0) {
                floors.put(dimension, value.value());
            }
        });
        return floors;
    }
}

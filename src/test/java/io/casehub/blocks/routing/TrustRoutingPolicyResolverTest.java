package io.casehub.blocks.routing;

import io.casehub.api.spi.routing.TrustRoutingPolicy;
import io.casehub.platform.api.preferences.MapPreferences;
import io.casehub.platform.api.preferences.Preferences;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class TrustRoutingPolicyResolverTest {

    private static final TrustRoutingPolicyKeys KEYS =
            TrustRoutingPolicyKeys.create("casehubio.test.trust-routing")
                    .withFloor("accuracy", "accuracy")
                    .withFloor("precision", "precision");

    @Test
    void missingThresholdReturnsDefault() {
        Preferences prefs = new MapPreferences(Map.of());
        TrustRoutingPolicy result = TrustRoutingPolicyResolver.resolve(prefs, KEYS);
        assertThat(result).isEqualTo(TrustRoutingPolicy.DEFAULT);
    }

    @Test
    void allFieldsResolved() {
        Preferences prefs = new MapPreferences(Map.of(
                "casehubio.test.trust-routing.threshold", "0.75",
                "casehubio.test.trust-routing.minimum-observations", "15",
                "casehubio.test.trust-routing.borderline-margin", "0.08",
                "casehubio.test.trust-routing.blend-factor", "0.70",
                "casehubio.test.trust-routing.floor.accuracy", "0.65",
                "casehubio.test.trust-routing.floor.precision", "0.60"
        ));
        TrustRoutingPolicy result = TrustRoutingPolicyResolver.resolve(prefs, KEYS);

        assertThat(result.threshold()).isEqualTo(0.75);
        assertThat(result.minimumObservations()).isEqualTo(15);
        assertThat(result.borderlineMargin()).isEqualTo(0.08);
        assertThat(result.blendFactor()).isEqualTo(0.70);
        assertThat(result.qualityFloors()).containsEntry("accuracy", 0.65);
        assertThat(result.qualityFloors()).containsEntry("precision", 0.60);
    }

    @Test
    void missingFieldsFallBackToDefault() {
        Preferences prefs = new MapPreferences(Map.of(
                "casehubio.test.trust-routing.threshold", "0.80"
        ));
        TrustRoutingPolicy result = TrustRoutingPolicyResolver.resolve(prefs, KEYS);

        assertThat(result.threshold()).isEqualTo(0.80);
        assertThat(result.minimumObservations()).isEqualTo(TrustRoutingPolicy.DEFAULT.minimumObservations());
        assertThat(result.borderlineMargin()).isEqualTo(TrustRoutingPolicy.DEFAULT.borderlineMargin());
        assertThat(result.blendFactor()).isEqualTo(TrustRoutingPolicy.DEFAULT.blendFactor());
        assertThat(result.qualityFloors()).isEmpty();
    }

    @Test
    void zeroFloorValuesExcluded() {
        Preferences prefs = new MapPreferences(Map.of(
                "casehubio.test.trust-routing.threshold", "0.70",
                "casehubio.test.trust-routing.floor.accuracy", "0.0",
                "casehubio.test.trust-routing.floor.precision", "0.50"
        ));
        TrustRoutingPolicy result = TrustRoutingPolicyResolver.resolve(prefs, KEYS);

        assertThat(result.qualityFloors()).doesNotContainKey("accuracy");
        assertThat(result.qualityFloors()).containsEntry("precision", 0.50);
    }

    @Test
    void bootstrapEscalationPassedThrough() {
        Preferences prefs = new MapPreferences(Map.of(
                "casehubio.test.trust-routing.threshold", "0.70"
        ));
        TrustRoutingPolicy result = TrustRoutingPolicyResolver.resolve(prefs, KEYS, true);
        assertThat(result.bootstrapEscalationRequired()).isTrue();
    }

    @Test
    void keysWithNoFloors() {
        TrustRoutingPolicyKeys noFloors = TrustRoutingPolicyKeys.create("casehubio.simple");
        Preferences prefs = new MapPreferences(Map.of(
                "casehubio.simple.threshold", "0.65"
        ));
        TrustRoutingPolicy result = TrustRoutingPolicyResolver.resolve(prefs, noFloors);

        assertThat(result.threshold()).isEqualTo(0.65);
        assertThat(result.qualityFloors()).isEmpty();
    }

    @Test
    void collectFloorsUtility() {
        Preferences prefs = new MapPreferences(Map.of(
                "casehubio.test.trust-routing.floor.accuracy", "0.70",
                "casehubio.test.trust-routing.floor.precision", "0.0"
        ));
        Map<String, Double> floors = TrustRoutingPolicyResolver.collectFloors(prefs, KEYS.allFloorKeys());

        assertThat(floors).containsEntry("accuracy", 0.70);
        assertThat(floors).doesNotContainKey("precision");
    }
}

package io.casehub.blocks.agentic.social.narrative;

import io.casehub.blocks.memory.ReflectionEntry;
import io.casehub.blocks.memory.ReflectionQueryStore;
import io.casehub.blocks.summarisation.EventLevel;
import io.casehub.blocks.summarisation.EventStreamBus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReflectionEventAdapterTest {

    private static final EventLevel LEVEL = new EventLevel("reflections", 0);

    @Test
    void publishNewReflections_publishesToBus() {
        var store = mock(ReflectionQueryStore.class);
        var r1 = new ReflectionEntry("agent-1", "t1", "insight-1",
                Instant.ofEpochMilli(100), List.of());
        when(store.findSince(eq("agent-1"), eq("t1"), any()))
                .thenReturn(List.of(r1));

        var bus = new EventStreamBus<ReflectionEntry>();
        List<ReflectionEntry> received = new ArrayList<>();
        bus.subscribe(e -> true, e -> received.add(e.payload()));

        var adapter = new ReflectionEventAdapter(store, bus, LEVEL);
        adapter.publishNewReflections("agent-1", "t1");

        assertThat(received).containsExactly(r1);
    }

    @Test
    void publishNewReflections_advancesWatermark() {
        var store = mock(ReflectionQueryStore.class);
        var r1 = new ReflectionEntry("agent-1", "t1", "insight-1",
                Instant.ofEpochMilli(100), List.of());
        when(store.findSince("agent-1", "t1", Instant.EPOCH))
                .thenReturn(List.of(r1));
        when(store.findSince("agent-1", "t1", Instant.ofEpochMilli(100)))
                .thenReturn(List.of());

        var bus = new EventStreamBus<ReflectionEntry>();
        List<ReflectionEntry> received = new ArrayList<>();
        bus.subscribe(e -> true, e -> received.add(e.payload()));

        var adapter = new ReflectionEventAdapter(store, bus, LEVEL);
        adapter.publishNewReflections("agent-1", "t1");
        assertThat(received).hasSize(1);

        adapter.publishNewReflections("agent-1", "t1");
        assertThat(received).as("watermark advanced, no new reflections").hasSize(1);
    }

    @Test
    void publishNewReflections_emptyResult_noPublish() {
        var store = mock(ReflectionQueryStore.class);
        when(store.findSince(any(), any(), any())).thenReturn(List.of());

        var bus = new EventStreamBus<ReflectionEntry>();
        List<ReflectionEntry> received = new ArrayList<>();
        bus.subscribe(e -> true, e -> received.add(e.payload()));

        var adapter = new ReflectionEventAdapter(store, bus, LEVEL);
        adapter.publishNewReflections("agent-1", "t1");
        assertThat(received).isEmpty();
    }

    @Test
    void publishNewReflections_setsCorrectTenancyId() {
        var store = mock(ReflectionQueryStore.class);
        var r1 = new ReflectionEntry("agent-1", "tenant-x", "insight",
                Instant.ofEpochMilli(100), List.of());
        when(store.findSince(any(), any(), any())).thenReturn(List.of(r1));

        var bus = new EventStreamBus<ReflectionEntry>();
        List<String> tenancyIds = new ArrayList<>();
        bus.subscribe(e -> true, e -> tenancyIds.add(e.tenancyId()));

        var adapter = new ReflectionEventAdapter(store, bus, LEVEL);
        adapter.publishNewReflections("agent-1", "tenant-x");
        assertThat(tenancyIds).containsExactly("tenant-x");
    }

    @Test
    void publishNewReflections_perAgentIsolation() {
        var store = mock(ReflectionQueryStore.class);
        var r1 = new ReflectionEntry("agent-1", "t1", "a1-insight",
                Instant.ofEpochMilli(100), List.of());
        var r2 = new ReflectionEntry("agent-2", "t1", "a2-insight",
                Instant.ofEpochMilli(200), List.of());
        when(store.findSince("agent-1", "t1", Instant.EPOCH))
                .thenReturn(List.of(r1));
        when(store.findSince("agent-2", "t1", Instant.EPOCH))
                .thenReturn(List.of(r2));

        var bus = new EventStreamBus<ReflectionEntry>();
        List<String> insights = new ArrayList<>();
        bus.subscribe(e -> true, e -> insights.add(e.payload().insight()));

        var adapter = new ReflectionEventAdapter(store, bus, LEVEL);
        adapter.publishNewReflections("agent-1", "t1");
        adapter.publishNewReflections("agent-2", "t1");
        assertThat(insights).containsExactly("a1-insight", "a2-insight");
    }
}

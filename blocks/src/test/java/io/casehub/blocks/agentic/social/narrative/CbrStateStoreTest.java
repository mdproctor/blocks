package io.casehub.blocks.agentic.social.narrative;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CbrStateStoreTest {

    @Test
    void load_parsesPartitionKey() {
        var delegate = mock(CbrNarrativeStore.class);
        var state = new NarrativeState("agent-1", "tenant-1",
                NarrativeScope.INDIVIDUAL, List.of(), Instant.now(), 0);
        when(delegate.load("agent-1", "tenant-1")).thenReturn(state);

        var store = new CbrStateStore(delegate);
        var result = store.load("agent-1:tenant-1");
        assertThat(result).isSameAs(state);
    }

    @Test
    void load_returnsNullWhenNotFound() {
        var delegate = mock(CbrNarrativeStore.class);
        when(delegate.load("agent-1", "tenant-1")).thenReturn(null);

        var store = new CbrStateStore(delegate);
        assertThat(store.load("agent-1:tenant-1")).isNull();
    }

    @Test
    void store_delegatesToCbrStore() {
        var delegate = mock(CbrNarrativeStore.class);
        var state = new NarrativeState("agent-1", "tenant-1",
                NarrativeScope.INDIVIDUAL, List.of(), Instant.now(), 0);

        var store = new CbrStateStore(delegate);
        store.store("agent-1:tenant-1", state);
        verify(delegate).store(state);
    }
}

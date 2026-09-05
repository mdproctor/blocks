package io.casehub.blocks.summarisation.cloudevents;

@FunctionalInterface
public interface EventSink<T> {
    void emit(T event);
}

package io.jafar.parser.internal_api;

@FunctionalInterface
public interface DeserializationHandler<T> {
    T handle(RecordingStream stream);
}

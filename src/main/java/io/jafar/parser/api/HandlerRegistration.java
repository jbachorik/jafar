package io.jafar.parser.api;

public interface HandlerRegistration<T> {
    void destroy(JafarParser cookie);
}

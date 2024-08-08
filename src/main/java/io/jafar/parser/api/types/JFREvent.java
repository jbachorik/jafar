package io.jafar.parser.api.types;

public interface JFREvent {
    long startTime();
    JFRThread eventThread();
    JFRStackTrace stackTrace();
}

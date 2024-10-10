package io.jafar.parser.api.types;

import io.jafar.parser.api.JfrType;

@JfrType("jdk.ExecutionSample")
public interface JFRExecutionSample extends JFREvent {
    JFRThread sampledThread();
    JFRStackTrace stackTrace();
    JFRThreadState state();
}

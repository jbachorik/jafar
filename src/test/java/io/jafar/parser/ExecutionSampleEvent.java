package io.jafar.parser;

import io.jafar.parser.api.JfrType;
import io.jafar.parser.api.types.JFREvent;
import io.jafar.parser.api.types.JFRThread;

@JfrType("datadog.ExecutionSample")
public interface ExecutionSampleEvent extends JFREvent {
    long startTime();
    JFRThread eventThread();
}

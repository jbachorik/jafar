package io.jafar.parser;

import io.jafar.parser.api.JfrField;
import io.jafar.parser.api.JfrType;
import io.jafar.parser.api.types.JFREvent;
import io.jafar.parser.api.types.JFRThread;

@JfrType("jdk.ExecutionSample")
public interface ExecutionSampleEvent extends JFREvent {
    long startTime();
    @JfrField("sampledThread") JFRThread eventThread();
}

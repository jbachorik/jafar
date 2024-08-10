package io.jafar.demo;

import io.jafar.parser.api.JfrField;
import io.jafar.parser.api.JfrType;
import io.jafar.parser.api.types.JFRThread;

@JfrType("jdk.ExecutionSample")
public interface ExecutionSampleEvent {
    long startTime();

    @JfrField("sampledThread")
    JFRThread eventThread();
}

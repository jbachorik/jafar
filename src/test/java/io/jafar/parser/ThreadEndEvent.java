package io.jafar.parser;

import io.jafar.parser.api.JfrType;
import io.jafar.parser.api.types.JFREvent;
import io.jafar.parser.api.types.JFRThread;

@JfrType("jdk.ThreadEnd")
public interface ThreadEndEvent {
    JFRThread thread();
}

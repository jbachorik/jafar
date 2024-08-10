package io.jafar.parser.api.types;

import io.jafar.parser.api.JfrType;

@JfrType("jdk.types.StackTrace")
public interface JFRStackTrace {
    boolean truncated();
    JFRStackFrame[] frames();
}

package io.jafar.parser.api.types;

import io.jafar.parser.api.JfrType;

@JfrType("jdk.types.ClassLoader")
public interface JFRClassLoader {
    JFRClass type();
    JFRSymbol name();
}

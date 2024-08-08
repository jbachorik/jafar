package io.jafar.parser.api.types;

import io.jafar.parser.api.JfrIgnore;
import io.jafar.parser.api.JfrType;

@JfrType("jdk.types.Method")
public interface JFRMethod {
    JFRClass type();
    JFRSymbol name();
    JFRSymbol descriptor();
    int modifiers();
    boolean hidden();

    @JfrIgnore
    default String string() {
        return String.format("%s.%s%s", type().tostring(), name().string(), descriptor().string());
    }
}

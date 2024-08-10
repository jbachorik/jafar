package io.jafar.parser.api.types;

import io.jafar.parser.api.JfrIgnore;
import io.jafar.parser.api.JfrType;

@JfrType("jdk.types.Package")
public interface JFRPackage {
    JFRSymbol name();
    JFRModule module();
    boolean exported();

    @JfrIgnore
    default String string() {
        return name().string();
    }
}

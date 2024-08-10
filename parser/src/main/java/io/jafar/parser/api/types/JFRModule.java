package io.jafar.parser.api.types;

import io.jafar.parser.api.JfrType;

@JfrType("jdk.types.Module")
public interface JFRModule {
    JFRSymbol name();
    JFRSymbol version();
    JFRSymbol location();
    JFRClassLoader classLoader();
}

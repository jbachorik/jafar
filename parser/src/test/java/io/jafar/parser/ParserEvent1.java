package io.jafar.parser;

import io.jafar.parser.api.JfrType;
import io.jafar.parser.api.types.JFREvent;

@JfrType("datadog.ParserEvent")
public interface ParserEvent1 extends JFREvent {
    int value();
}

package io.jafar.parser.api;
import io.jafar.parser.JafarParserImpl;
import io.jafar.parser.api.types.JFREvent;
import io.jafar.parser.api.types.JFRHandler;

import java.io.IOException;
import java.nio.file.Paths;

public interface JafarParser {
    public static JafarParser open(String path) {
        return new JafarParserImpl(Paths.get(path));
    }


    <T extends JFREvent> JafarParser handle(Class<T> clz, JFRHandler<T> handler);

    void run() throws IOException;
}

package io.jafar.parser.api;
import io.jafar.parser.JafarParserImpl;

import java.io.IOException;
import java.nio.file.Paths;

public interface JafarParser extends AutoCloseable{
    static JafarParser open(String path) {
        return new JafarParserImpl(Paths.get(path));
    }


    <T> HandlerRegistration<T> handle(Class<T> clz, JFRHandler<T> handler);

    void run() throws IOException;
}

package io.jafar.parser;

import io.jafar.parser.internal_api.RecordingStream;
import io.jafar.parser.internal_api.metadata.MetadataClass;

import java.io.IOException;

public final class ValueLoader {
    public static void skip(RecordingStream stream, MetadataClass typeDescriptor, boolean isArray, boolean hasConstantPool) throws IOException {
        int len = isArray ? (int) stream.readVarint() : 1;
        if (hasConstantPool) {
            for (int i = 0; i < len; i++) {
                stream.readVarint();
            }
        } else {
            for (int i = 0; i < len; i++) {
                typeDescriptor.skip(stream);
            }
        }
    }
}

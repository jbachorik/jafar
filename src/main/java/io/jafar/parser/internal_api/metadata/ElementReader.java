package io.jafar.parser.internal_api.metadata;

import io.jafar.parser.internal_api.RecordingStream;

import java.io.IOException;

@FunctionalInterface
interface ElementReader {
    AbstractMetadataElement readElement(RecordingStream stream) throws IOException;
}

package io.jafar.parser;

import io.jafar.TestJfrRecorder;
import io.jafar.parser.api.JafarParser;
import io.jafar.parser.api.types.JFRStackFrame;
import io.jafar.parser.api.types.JFRStackTrace;
import org.junit.jupiter.api.Test;
import org.openjdk.jmc.flightrecorder.writer.api.Recording;
import org.openjdk.jmc.flightrecorder.writer.api.Recordings;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class JafarParserTest {
    @Test
    void testEventParsing() throws Exception {

        ByteArrayOutputStream recordingStream = new ByteArrayOutputStream();
        long eventTypeId = -1;
        try (Recording recording = Recordings.newRecording(recordingStream)) {
            TestJfrRecorder rec = new TestJfrRecorder(recording);
            eventTypeId = rec.registerEventType(ParserEvent.class).getId();
            rec.writeEvent(new ParserEvent(10));
        }

        assertNotEquals(-1, eventTypeId);

        Path tmpFile = Files.createTempFile("recording", ".jfr");
        tmpFile.toFile().deleteOnExit();

        Files.write(tmpFile, recordingStream.toByteArray());

        JafarParser parser = JafarParser.open(tmpFile.toString());

        AtomicInteger eventCount = new AtomicInteger(0);
        parser.handle(ParserEvent1.class, (event, ctl) -> {
            eventCount.incrementAndGet();
            assertEquals(10, event.value());
        });

        parser.run();

        assertEquals(1, eventCount.get());
    }

    @Test
    void testRealFile() throws Exception {
        JafarParser p = JafarParser.open(Paths.get("/tmp/main.jfr").toString());
        p.handle(ExecutionSampleEvent.class, (event, ctl) -> {
            System.out.println(event.eventThread().javaName());
        });

        p.run();
    }
}

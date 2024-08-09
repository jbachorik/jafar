package io.jafar.parser;

import io.jafar.TestJfrRecorder;
import io.jafar.parser.api.HandlerRegistration;
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
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        // TODO commented out until LFS is enabled for the GH project
//        JafarParser p = JafarParser.open(Paths.get("/tmp/recording.jfr").toString());
//        AtomicLong eventCount = new AtomicLong(0);
//        HandlerRegistration<ExecutionSampleEvent> h1 = p.handle(ExecutionSampleEvent.class, (event, ctl) -> {
//            assertNotNull(event.eventThread());
//            assertNotNull(event.stackTrace());
//            eventCount.incrementAndGet();
//        });
//
//        // warmup
//        long ts = System.nanoTime();
//        p.run();
//        System.out.println("=== cold access: " + (System.nanoTime() - ts) / 1_000_000 + "ms");
//
//        assertTrue(eventCount.get() > 0);
//        System.out.println("=== event: " + eventCount.get());
//
//        eventCount.set(0);
//        for (int i = 0; i < 100; i++) {
//            ts = System.nanoTime();
//            p.run();
//            System.out.println("=== warmed up access[" + i + "]: " + (System.nanoTime() - ts) / 1_000_000 + "ms");
//        }
//        System.out.println("=== event: " + eventCount.get());
//
//        eventCount.set(0);
//        var h2 = p.handle(ThreadEndEvent.class, (event, ctl) -> {
//            eventCount.incrementAndGet();
//            assertNotNull(event.thread().javaName());
//        });
//        for (int i = 0; i < 100; i++) {
//            ts = System.nanoTime();
//            p.run();
//            System.out.println("=== warmed with two handlers[" + i + "]: " + (System.nanoTime() - ts) / 1_000_000 + "ms");
//        }
//        System.out.println("=== event: " + eventCount.get());
//
//        eventCount.set(0);
//        h2.destroy(p);
//        for (int i = 0; i < 100; i++) {
//            ts = System.nanoTime();
//            p.run();
//            System.out.println("=== warmed with first handler[" + i + "]: " + (System.nanoTime() - ts) / 1_000_000 + "ms");
//        }
//        System.out.println("=== event: " + eventCount.get());
//
//        eventCount.set(0);
//        h1.destroy(p);
//        for (int i = 0; i < 100; i++) {
//            ts = System.nanoTime();
//            p.run();
//            System.out.println("=== warmed with no handlers[" + i + "]: " + (System.nanoTime() - ts) / 1_000_000 + "ms");
//        }
//        System.out.println("=== event: " + eventCount.get());
    }
}

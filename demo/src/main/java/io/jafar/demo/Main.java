package io.jafar.demo;

import io.jafar.parser.api.HandlerRegistration;
import io.jafar.parser.api.JafarParser;
import jdk.jfr.consumer.EventStream;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import org.openjdk.jmc.common.item.IItem;
import org.openjdk.jmc.common.item.IItemCollection;
import org.openjdk.jmc.common.item.IItemIterable;
import org.openjdk.jmc.common.item.ItemFilters;
import org.openjdk.jmc.flightrecorder.CouldNotLoadRecordingException;
import org.openjdk.jmc.flightrecorder.JfrAttributes;
import org.openjdk.jmc.flightrecorder.JfrLoaderToolkit;
import org.openjdk.jmc.flightrecorder.jdk.JdkAttributes;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAccumulator;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: <jafar|jmc|jfr|jfr-stream> <recording>");
        }

        AtomicInteger cnt = new AtomicInteger();
        LongAccumulator sum = new LongAccumulator(Long::sum, 0);
        File file = new File(args[1]).getAbsoluteFile();
        if ("jafar".equalsIgnoreCase(args[0])) {
            runWithJafar(file, sum, cnt);
        } else if ("jmc".equalsIgnoreCase(args[0])) {
            runWithJmc(file, sum, cnt);
        } else if ("jfr".equalsIgnoreCase(args[0])) {
            runWithJfr(file, sum, cnt);
        } else if ("jfr-stream".equalsIgnoreCase(args[0])) {
            runWithJfrStream(file, sum, cnt);
        } else {
            throw new IllegalArgumentException("Unknown parser: " + args[0]);
        }
        System.out.println("Total events: " + cnt.get());
        System.out.println("Sum of thread ids: " + sum.get());
    }

    private static void runWithJmc(File file, LongAccumulator sum, AtomicInteger cnt) throws IOException, CouldNotLoadRecordingException {
        IItemCollection events = JfrLoaderToolkit.loadEvents(file);
        events = events.apply(ItemFilters.type("jdk.ExecutionSample"));
        for (IItemIterable lane : events) {
            var threadIdAccessor = JdkAttributes.EVENT_THREAD_ID.getAccessor(lane.getType());
            var stackAccessor = JfrAttributes.EVENT_STACKTRACE.getAccessor(lane.getType());
            for (IItem event : lane) {
                long threadId = threadIdAccessor.getMember(event).longValue();
                sum.accumulate(threadId);
                sum.accumulate(stackAccessor.getMember(event).getFrames().size());
                cnt.incrementAndGet();
            }
        }
    }

    private static void runWithJafar(File file, LongAccumulator sum, AtomicInteger cnt) throws Exception {
        try (JafarParser p = JafarParser.open(file.getPath())) {
            HandlerRegistration<ExecutionSampleEvent> h1 = p.handle(ExecutionSampleEvent.class, (event, ctl) -> {
                if (event.sampledThread() == null) {
                    throw new RuntimeException();
                }

                sum.accumulate(event.sampledThread().javaThreadId());
                sum.accumulate(event.stackTrace().frames().length);
                cnt.incrementAndGet();
            });

            p.run();
        }
    }

    private static void runWithJfr(File file, LongAccumulator sum, AtomicInteger cnt) throws IOException, CouldNotLoadRecordingException {
        try (RecordingFile recording = new RecordingFile(file.toPath())) {
            while (recording.hasMoreEvents()) {
                RecordedEvent e = recording.readEvent();
                if (e.getEventType().getName().equals("jdk.ExecutionSample")) {
                    sum.accumulate(e.getThread("sampledThread").getJavaThreadId());
                    sum.accumulate(e.getStackTrace().getFrames().size());
                    cnt.incrementAndGet();
                }
            }
        }
    }

    private static void runWithJfrStream(File file, LongAccumulator sum, AtomicInteger cnt) throws IOException, CouldNotLoadRecordingException {
        var es = EventStream.openFile(Paths.get(file.getPath()));
        es.setReuse(true);
        es.setOrdered(false);
        es.onEvent("jdk.ExecutionSample", e -> {
            sum.accumulate(e.getThread("sampledThread").getJavaThreadId());
            sum.accumulate(e.getStackTrace().getFrames().size());
            cnt.incrementAndGet();
        });
        es.start();
        try {
            es.awaitTermination();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

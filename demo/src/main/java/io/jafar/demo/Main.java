package io.jafar.demo;

import io.jafar.parser.api.HandlerRegistration;
import io.jafar.parser.api.JafarParser;
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
            throw new IllegalArgumentException("Usage: <jafar|jmc> <recording>");
        }

        AtomicInteger cnt = new AtomicInteger();
        LongAccumulator sum = new LongAccumulator(Long::sum, 0);
        if ("jafar".equalsIgnoreCase(args[0])) {
            runWithJafar(args, sum, cnt);
        } else if ("jmc".equalsIgnoreCase(args[0])) {
            runWithJmc(args, sum, cnt);
        } else if ("jfr".equalsIgnoreCase(args[0])) {
            runWithJfr(args, sum, cnt);
        } else {
            throw new IllegalArgumentException("Unknown parser: " + args[0]);
        }
        System.out.println("Total events: " + cnt.get());
        System.out.println("Sum of thread ids: " + sum.get());
    }

    private static void runWithJmc(String[] args, LongAccumulator sum, AtomicInteger cnt) throws IOException, CouldNotLoadRecordingException {
        IItemCollection events = JfrLoaderToolkit.loadEvents(new File(args[1]));
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

    private static void runWithJafar(String[] args, LongAccumulator sum, AtomicInteger cnt) throws Exception {
        try (JafarParser p = JafarParser.open(args[1])) {
            HandlerRegistration<ExecutionSampleEvent> h1 = p.handle(ExecutionSampleEvent.class, (event, ctl) -> {
                if (event.eventThread() == null) {
                    throw new RuntimeException();
                }

                sum.accumulate(event.eventThread().javaThreadId());
                sum.accumulate(event.stackTrace().frames().length);
                cnt.incrementAndGet();
            });

            p.run();
        }
    }

    private static void runWithJfr(String[] args, LongAccumulator sum, AtomicInteger cnt) throws IOException, CouldNotLoadRecordingException {
        try (RecordingFile recording = new RecordingFile(Paths.get((args[1])))) {
            while (recording.hasMoreEvents()) {
                RecordedEvent e = recording.readEvent();
                if (e.getEventType().getName().equals("jdk.ExecutionSample")) {
                    sum.accumulate(e.getThread("sampledThread").getId());
                    sum.accumulate(e.getStackTrace().getFrames().size());
                    cnt.incrementAndGet();
                }
            }
        }
    }
}

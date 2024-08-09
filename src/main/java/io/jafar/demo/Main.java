package io.jafar.demo;

import io.jafar.parser.api.HandlerRegistration;
import io.jafar.parser.api.JafarParser;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAccumulator;

public class Main {
    public static void main(String[] args) throws IOException {
        JafarParser p = JafarParser.open(args[0]);
        AtomicInteger cnt = new AtomicInteger();
        LongAccumulator sum = new LongAccumulator(Long::sum, 0);
        HandlerRegistration<ExecutionSampleEvent> h1 = p.handle(ExecutionSampleEvent.class, (event, ctl) -> {
            if (event.eventThread() == null) {
                throw new RuntimeException();
            }
            sum.accumulate(event.eventThread().javaThreadId());
            cnt.incrementAndGet();
        });

        p.run();
        System.out.println("Total events: " + cnt.get());
        System.out.println("Sum of thread ids: " + sum.get());
    }
}

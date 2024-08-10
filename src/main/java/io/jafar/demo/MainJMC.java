package io.jafar.demo;

import org.openjdk.jmc.common.item.IItem;
import org.openjdk.jmc.common.item.IItemCollection;
import org.openjdk.jmc.common.item.IItemFilter;
import org.openjdk.jmc.common.item.IItemIterable;
import org.openjdk.jmc.common.item.ItemFilters;
import org.openjdk.jmc.flightrecorder.JfrLoaderToolkit;
import org.openjdk.jmc.flightrecorder.jdk.JdkAttributes;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.LongAccumulator;

public class MainJMC {
    public static void main(String[] args) throws Exception {
        IItemCollection events = JfrLoaderToolkit.loadEvents(new File(args[0]));
        events = events.apply(ItemFilters.type("jdk.ExecutionSample"));
        LongAccumulator sum = new LongAccumulator(Long::sum, 0);
        int count = 0;
        for (IItemIterable lane : events) {
            var threadIdAccessor = JdkAttributes.EVENT_THREAD_ID.getAccessor(lane.getType());
            for (IItem event : lane) {
                long threadId = threadIdAccessor.getMember(event).longValue();
                sum.accumulate(threadId);
                count++;
            }
        }
        System.out.println("Total events: " + count);
        System.out.println("Sum of thread ids: " + sum.get());
    }
}

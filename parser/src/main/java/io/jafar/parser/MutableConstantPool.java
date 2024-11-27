package io.jafar.parser;

import io.jafar.parser.internal_api.ConstantPool;
import io.jafar.parser.internal_api.RecordingStream;
import io.jafar.parser.internal_api.metadata.MetadataClass;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

public final class MutableConstantPool implements ConstantPool {
    private final Long2LongMap offsets = new Long2LongOpenHashMap(10000, 0.75f);
    private final Long2ObjectMap<Object> entries = new Long2ObjectOpenHashMap<>();

    private final RecordingStream stream;
    private final MetadataClass clazz;

    public MutableConstantPool(RecordingStream chunkStream, long typeId) {
        this.stream = chunkStream;
        var context = chunkStream.getContext();
        clazz = context.getMetadataLookup().getClass(typeId);
    }

    public Object get(long id) {
        long offset = offsets.get(id);
        if (offset > 0) {
            return entries.computeIfAbsent(id, k -> {
                long pos = stream.position();
                try {
                    stream.position(offset);
                    return clazz.read(stream);
                } finally {
                    stream.position(pos);
                }
            });
        }
        return null;
    }

    public void addOffset(long id, long offset) {
        offsets.put(id, offset);
    }

    @Override
    public int size() {
        return entries.size();
    }

    @Override
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    @Override
    public MetadataClass getType() {
        return clazz;
    }
}

package io.jafar.parser;

import io.jafar.parser.internal_api.ConstantPool;
import io.jafar.parser.internal_api.RecordingStream;
import io.jafar.parser.internal_api.metadata.MetadataClass;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.io.IOException;

public final class MutableConstantPool implements ConstantPool {
    private final Long2LongMap offsets;
    private final Long2ObjectMap<Object> entries;

    private final RecordingStream stream;
    private final MetadataClass clazz;

    public MutableConstantPool(RecordingStream chunkStream, long typeId, int count) {
        this.offsets = new Long2LongOpenHashMap(count);
        this.entries = new Long2ObjectOpenHashMap<>(count);
        this.stream = chunkStream;
        var context = chunkStream.getContext();
        clazz = context.getMetadataLookup().getClass(typeId);
    }

    public Object get(long id) {
        try {
            long offset = offsets.get(id);
            if (offset > 0) {
                Object o = entries.get(id);
                if (o == null) {
                    long pos = stream.position();
                    try {
                        stream.position(offsets.get(id));
                        o = clazz.read(stream);
                        entries.put(id, o);
                    } finally {
                        stream.position(pos);
                    }
                }
                return o;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    public boolean containsKey(long key) {
        return offsets.containsKey(key);
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

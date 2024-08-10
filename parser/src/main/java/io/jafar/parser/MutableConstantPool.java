package io.jafar.parser;

import io.jafar.parser.internal_api.ConstantPool;
import io.jafar.parser.internal_api.MetadataLookup;
import io.jafar.parser.internal_api.metadata.MetadataClass;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

public final class MutableConstantPool implements ConstantPool {
    private final Long2ObjectMap<Object> entries = new Long2ObjectOpenHashMap<>();

    private final MetadataLookup metadata;
    private final long typeId;

    public MutableConstantPool(MetadataLookup metadata, long typeId) {
        this.metadata = metadata;
        this.typeId = typeId;
    }

    public Object get(long id) {
        return entries.get(id);
    }

    public void addValue(long id, Object value) {
        entries.putIfAbsent(id, value);
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
        return metadata.getClass(typeId);
    }
}

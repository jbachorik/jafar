package io.jafar.parser;

import io.jafar.parser.internal_api.ConstantPool;
import io.jafar.parser.internal_api.ConstantPools;
import io.jafar.parser.internal_api.MetadataLookup;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.stream.Stream;

public final class MutableConstantPools implements ConstantPools {
    private final Long2ObjectMap<MutableConstantPool> poolMap = new Long2ObjectOpenHashMap<>();

    private final MetadataLookup metadata;

    public MutableConstantPools(MetadataLookup metadata) {
        this.metadata = metadata;
    }

    @Override
    public MutableConstantPool getConstantPool(long typeId) {
        return poolMap.computeIfAbsent(typeId, k -> new MutableConstantPool(metadata, k));
    }

    @Override
    public boolean hasConstantPool(long typeId) {
        return poolMap.containsKey(typeId);
    }

    @Override
    public Stream<? extends ConstantPool> pools() {
        return poolMap.values().stream();
    }
}

package io.jafar.parser.internal_api;

import io.jafar.parser.MutableConstantPools;
import io.jafar.parser.MutableMetadataLookup;
import io.jafar.parser.TypeFilter;
import io.jafar.parser.internal_api.metadata.MetadataClass;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.lang.invoke.MethodHandle;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ParserContext {
    private final MutableMetadataLookup metadataLookup;
    private final MutableConstantPools constantPools;

    private final int chunkIndex;
    private volatile TypeFilter typeFilter;

    private final Map<String, Class<?>> classTargetTypeMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, WeakReference<?>> bag = new ConcurrentHashMap<>();

    private Long2ObjectMap<Class<?>> classTypeMap = null;

    private final ConcurrentMap<MetadataClass, Deserializer<?>> globalDeserializerCache;

    public ParserContext() {
        this.metadataLookup = new MutableMetadataLookup();
        this.constantPools = new MutableConstantPools(metadataLookup);
        this.globalDeserializerCache = new ConcurrentHashMap<>();

        this.typeFilter = null;
        this.chunkIndex = 0;
    }

    public ParserContext(TypeFilter typeFilter, int chunkIndex, MutableMetadataLookup metadataLookup, MutableConstantPools constantPools, ConcurrentMap<MetadataClass, Deserializer<?>> deserializerCache) {
        this.metadataLookup = metadataLookup;
        this.constantPools = constantPools;
        this.globalDeserializerCache = deserializerCache;

        this.typeFilter = typeFilter;
        this.chunkIndex = chunkIndex;
    }

    public void clear() {
        classTargetTypeMap.clear();
        bag.clear();
    }

    public MetadataLookup getMetadataLookup() {
        return metadataLookup;
    }

    public ConstantPools getConstantPools() {
        return constantPools;
    }

    public TypeFilter getTypeFilter() {
        return typeFilter;
    }

    public void setTypeFilter(TypeFilter typeFilter) {
        this.typeFilter = typeFilter;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public <T> void put(String key, Class<T> clz, T value) {
        bag.put(key, new WeakReference<>(value));
    }

    public <T> T get(String key, Class<T> clz) {
        return clz.cast(bag.get(key).get());
    }

    public void addTargetTypeMap(Map<String, Class<?>> map) {
        this.classTargetTypeMap.putAll(map);
    }

    public Class<?> getClassTargetType(String name) {
        return classTargetTypeMap.get(name);
    }

    public void bindDeserializers() {
        metadataLookup.bindDeserializers();
    }

    public void setClassTypeMap(Long2ObjectMap<Class<?>> map) {
        classTypeMap = map;
    }

    public Long2ObjectMap<Class<?>> getClassTypeMap() {
        return classTypeMap;
    }

    public ConcurrentMap<MetadataClass, Deserializer<?>> getDeserializerCache() {
        return globalDeserializerCache;
    }
}

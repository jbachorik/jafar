package io.jafar.parser.internal_api;

import io.jafar.parser.Deserializers;
import io.jafar.parser.MutableConstantPools;
import io.jafar.parser.MutableMetadataLookup;
import io.jafar.parser.TypeFilter;

public final class ParserContext {
    private final MutableMetadataLookup metadataLookup = new MutableMetadataLookup();
    private final MutableConstantPools constantPools = new MutableConstantPools(metadataLookup);
    private final Deserializers deserializers = new Deserializers();

    private volatile TypeFilter typeFilter = null;

    public ParserContext() {}

    public ParserContext(TypeFilter filter) {
        this.typeFilter = filter;
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

    public Deserializers getDeserializers() {
        return deserializers;
    }

    public void setTypeFilter(TypeFilter filter) {
        this.typeFilter = filter;
    }
}

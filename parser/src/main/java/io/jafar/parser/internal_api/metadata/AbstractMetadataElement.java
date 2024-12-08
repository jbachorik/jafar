package io.jafar.parser.internal_api.metadata;

import io.jafar.parser.MutableMetadataLookup;
import io.jafar.parser.internal_api.ParserContext;
import io.jafar.parser.internal_api.RecordingStream;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public abstract class AbstractMetadataElement {
    private final RecordingStream stream;

    final MutableMetadataLookup metadataLookup;

//    private final Map<String, String> attributes = new HashMap<>(8, 0.75f);

    private String id = "-1";
    private String name = null;
    private String simpleName = null;
    private final MetadataElementKind kind;

    AbstractMetadataElement(RecordingStream stream, MetadataElementKind kind) throws IOException {
        this.stream = stream;
        this.kind = kind;
        this.metadataLookup = (MutableMetadataLookup) stream.getContext().getMetadataLookup();
        processAttributes();
    }
    
//    protected final String getAttribute(String key) {
//        return attributes.get(key);
//    }
//
//    protected final String getAttribute(String key, String dflt) {
//        return attributes.getOrDefault(key, dflt);
//    }

    protected final void readSubelements(MetadataEvent event) throws IOException {
        // now inspect all the enclosed elements
        int elemCount = (int) stream.readVarint();
        for (int i = 0; i < elemCount; i++) {
            onSubelement(elemCount, event.readElement(stream));
        }
    }

    protected void onSubelement(int count, AbstractMetadataElement element) {}

    abstract public void accept(MetadataVisitor visitor);

    protected void onAttribute(String key, String value) {}

    protected final void processAttributes() throws IOException {
        int attrCount = (int) stream.readVarint();
        for (int i = 0; i < attrCount; i++) {
            String key = metadataLookup.getString((int) stream.readVarint());
            String value = metadataLookup.getString((int) stream.readVarint());
            if ("name".equals(key)) {
                name = value;
            }
            if ("id".equals(key)) {
                id = value;
            }
            onAttribute(key, value);
        }
    }

    public long getId() {
        return Long.parseLong(id);
    }

    public String getName() {
        return name;
    }

    public String getSimpleName() {
        if (simpleName == null) {
            int idx = name.lastIndexOf('.');
            simpleName = idx == -1 ? name : name.substring(idx + 1);
        }
        return simpleName;
    }

    public MetadataElementKind getKind() {
        return kind;
    }

    public ParserContext getContext() {
        return stream.getContext();
    }
}

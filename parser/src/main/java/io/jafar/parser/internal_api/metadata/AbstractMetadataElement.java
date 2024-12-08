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

    private long id = -1;
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

    protected final void readSubelements(ElementReader reader) throws IOException {
        // now inspect all the enclosed elements
        int elemCount = (int) stream.readVarint();
        for (int i = 0; i < elemCount; i++) {
            onSubelement(elemCount, reader.readElement(stream));
        }
    }

    protected void onSubelement(int count, AbstractMetadataElement element) {}

    abstract public void accept(MetadataVisitor visitor);

    protected void onAttribute(String key, String value) {}

    static int[][] readAttributes(RecordingStream stream) throws IOException {
        int attrCount = (int) stream.readVarint();
        int [][] ret = new int[attrCount][2];
        for (int i = 0; i < attrCount; i++) {
            ret[i][0] = (int) stream.readVarint();
            ret[i][1] = (int) stream.readVarint();
        }
        return ret;
    }

    protected final void processAttributes() throws IOException {
        int[][] attrs = readAttributes(stream);
        for (int i = 0; i < attrs.length; i++) {
            int[] attr = attrs[i];
            String key = metadataLookup.getString(attr[0]);
            String value = metadataLookup.getString(attr[1]);
//            attributes.put(key, value);
            if ("name".equals(key)) {
                name = value;
            }
            if ("id".equals(key)) {
                id = Long.parseLong(value);
            }
            onAttribute(key, value);
        }
    }

    public long getId() {
        return id;
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

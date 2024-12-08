package io.jafar.parser.internal_api.metadata;

import io.jafar.parser.internal_api.RecordingStream;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class MetadataField extends AbstractMetadataElement {
    private boolean hasHashCode = false;
    private int hashCode;

    private List<MetadataAnnotation> annotations = null;
    private long classId;
    private boolean hasConstantPool;
    private int dimension;
    private MetadataClass type = null;

    MetadataField(RecordingStream stream, ElementReader reader, boolean forceConstantPools) throws IOException {
        super(stream, MetadataElementKind.FIELD);
        readSubelements(reader);
    }

    @Override
    protected void onAttribute(String key, String value) {
        switch (key) {
            case "class":
                classId = Long.parseLong(value);
                break;
            case "constantPool":
                hasConstantPool = Boolean.parseBoolean(value);
                break;
            case "dimension":
                dimension = value != null ? Integer.parseInt(value) : -1;
                break;
        }
    }

    public MetadataClass getType() {
        // all events from a single chunk, referencing a particular type will be procesed in a single thread
        // therefore, we are not risiking data race here
        if (type == null) {
            type = metadataLookup.getClass(classId);
        }
        return type;
    }

    public long getTypeId() {
        return classId;
    }

    public boolean hasConstantPool() {
        return hasConstantPool;
    }

    public int getDimension() {
        return dimension;
    }

    @Override
    protected void onSubelement(int count, AbstractMetadataElement element) {
        if (element.getKind() == MetadataElementKind.ANNOTATION) {
            if (annotations == null) {
                annotations = new ArrayList<>(count);
            }
            annotations.add((MetadataAnnotation) element);
        } else {
            throw new IllegalStateException("Unexpected subelement: " + element.getKind());
        }
    }

    @Override
    public void accept(MetadataVisitor visitor) {
        visitor.visitField(this);
        if (annotations != null) {
            annotations.forEach(a -> a.accept(visitor));
        }
        visitor.visitEnd(this);
    }

    @Override
    public String toString() {
        return "MetadataField{" +
                "type='" + (getType() != null ? getType().getName() : classId) + '\'' +
                ", name='" + getName() + "'" +
                ", hasConstantPool=" + hasConstantPool +
                ", dimension=" + dimension +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MetadataField that = (MetadataField) o;
        return classId == that.classId && hasConstantPool == that.hasConstantPool && dimension == that.dimension;
    }

    @Override
    public int hashCode() {
        if (!hasHashCode) {
            hashCode = Objects.hash(classId, hasConstantPool, dimension);
            hasHashCode = true;
        }
        return hashCode;
    }
}

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
    private Long classId;
    private String classIdVal;
    private Boolean hasConstantPool;
    private String hasConstantPoolVal;
    private Integer dimension;
    private String dimensionVal;

    private MetadataClass type = null;

    MetadataField(RecordingStream stream, MetadataEvent event, boolean forceConstantPools) throws IOException {
        super(stream, MetadataElementKind.FIELD);
        readSubelements(event);
    }

    @Override
    protected void onAttribute(String key, String value) {
        switch (key) {
            case "class":
                classIdVal = value;
                break;
            case "constantPool":
                hasConstantPoolVal = value;
                break;
            case "dimension":
                dimensionVal = value;
                break;
        }
    }

    public MetadataClass getType() {
        // all events from a single chunk, referencing a particular type will be procesed in a single thread
        // therefore, we are not risiking data race here
        if (type == null) {
            type = metadataLookup.getClass(getTypeId());
        }
        return type;
    }

    public long getTypeId() {
        if (classId == null) {
            classId = Long.parseLong(classIdVal);
        }
        return classId;
    }

    public boolean hasConstantPool() {
        if (hasConstantPool == null) {
            hasConstantPool = Boolean.parseBoolean(hasConstantPoolVal);
        }
        return hasConstantPool;
    }

    public int getDimension() {
        if (dimension == null) {
            dimension = dimensionVal != null ? Integer.parseInt(dimensionVal) : -1;
        }
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
                "type='" + (getType() != null ? getType().getName() : getTypeId()) + '\'' +
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
        return getTypeId() == that.getTypeId() && hasConstantPool() == that.hasConstantPool() && getDimension() == that.getDimension();
    }

    @Override
    public int hashCode() {
        if (!hasHashCode) {
            long mixed = getTypeId() * 0x9E3779B97F4A7C15L + (hasConstantPool() ? 1 : 0) * 0xC6BC279692B5C323L + getDimension() * 0xD8163841FDE6A8F9L;
            hashCode = Long.hashCode(mixed);
            hasHashCode = true;
        }
        return hashCode;
    }
}

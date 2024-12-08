package io.jafar.parser.internal_api.metadata;

import io.jafar.parser.internal_api.RecordingStream;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class MetadataElement extends AbstractMetadataElement {
    private boolean hasHashCode = false;
    private int hashCode;

    private List<MetadataClass> classes = null;

    MetadataElement(RecordingStream stream, ElementReader reader) throws IOException {
        super(stream, MetadataElementKind.META);
        readSubelements(reader);
    }

    @Override
    protected void onSubelement(int count, AbstractMetadataElement element) {
        if (element.getKind() == MetadataElementKind.CLASS) {
            if (classes == null) {
                classes = new ArrayList<>(count);
            }
            MetadataClass clz = (MetadataClass) element;
            classes.add(clz);
        } else {
            throw new IllegalStateException("Unexpected subelement: " + element.getKind());
        }
    }

    @Override
    public void accept(MetadataVisitor visitor) {
        visitor.visitMetadata(this);
        if (classes != null) {
            classes.forEach(c -> c.accept(visitor));
        }
    }

    @Override
    public String toString() {
        return "MetadataElement";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MetadataElement that = (MetadataElement) o;
        return Objects.equals(classes, that.classes);
    }

    @Override
    public int hashCode() {
        if (!hasHashCode) {
            hashCode = Objects.hash(classes);
            hasHashCode = true;
        }
        return hashCode;
    }
}

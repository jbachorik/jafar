package io.jafar.parser.internal_api.metadata;

import io.jafar.parser.internal_api.RecordingStream;

import java.io.IOException;
import java.util.Objects;

public final class MetadataRoot extends AbstractMetadataElement {
    private boolean hasHashHascode = false;
    private int hashCode;

    private MetadataElement metadata;
    private MetadataRegion region;

    MetadataRoot(RecordingStream stream, ElementReader reader) throws IOException {
        super(stream, MetadataElementKind.ROOT);
        readSubelements(reader);
    }

    @Override
    protected void onSubelement(int count, AbstractMetadataElement element) {
        if (element.getKind() == MetadataElementKind.META) {
            metadata = (MetadataElement) element;
        } else if (element.getKind() == MetadataElementKind.REGION) {
            region = (MetadataRegion) element;
        } else {
            throw new IllegalStateException("Unexpected subelement: " + element.getKind());
        }
    }

    @Override
    public void accept(MetadataVisitor visitor) {
        visitor.visitRoot(this);
        metadata.accept(visitor);
        region.accept(visitor);
        visitor.visitEnd(this);
    }

    @Override
    public String toString() {
        return "MetadataRoot";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MetadataRoot that = (MetadataRoot) o;
        return Objects.equals(metadata, that.metadata) && Objects.equals(region, that.region);
    }

    @Override
    public int hashCode() {
        if (!hasHashHascode) {
            hashCode = Objects.hash(metadata, region);
            hasHashHascode = true;
        }
        return hashCode;
    }
}

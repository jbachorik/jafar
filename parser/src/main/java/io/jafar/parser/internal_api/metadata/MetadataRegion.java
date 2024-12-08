package io.jafar.parser.internal_api.metadata;

import io.jafar.parser.internal_api.RecordingStream;

import java.io.IOException;
import java.util.Objects;

public final class MetadataRegion extends AbstractMetadataElement {
    private boolean hasHashCode = false;
    private int hashCode;

    private long dst;
    private long gmtOffset;
    private String locale;

    MetadataRegion(RecordingStream stream, ElementReader reader) throws IOException {
        super(stream, MetadataElementKind.REGION);
        readSubelements(reader);
    }

    @Override
    protected void onAttribute(String key, String value) {
        switch (key) {
            case "dst":
                dst = value != null ? Long.parseLong(value) : 0L;
                break;
            case "gmtOffset":
                gmtOffset = value!= null ? Long.parseLong(value) : 0L;
                break;
            case "locale":
                locale = value != null ? value : "en_US";
                break;
        }
    }

    public long getDst() {
        return dst;
    }

    public long getGmtOffset() {
        return gmtOffset;
    }

    public String getLocale() {
        return locale;
    }

    @Override
    protected void onSubelement(int count, AbstractMetadataElement element) {
        throw new IllegalStateException("Unexpected subelement: " + element.getKind());
    }

    @Override
    public void accept(MetadataVisitor visitor) {
        visitor.visitRegion(this);
        visitor.visitEnd(this);
    }

    @Override
    public String toString() {
        return "MetadataRegion{" +
                "dst=" + dst +
                ", gmtOffset=" + gmtOffset +
                ", locale='" + locale + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MetadataRegion that = (MetadataRegion) o;
        return dst == that.dst && gmtOffset == that.gmtOffset && Objects.equals(locale, that.locale);
    }

    @Override
    public int hashCode() {
        if (!hasHashCode) {
            hashCode = Objects.hash(dst, gmtOffset, locale);
            hasHashCode = true;
        }
        return hashCode;
    }
}

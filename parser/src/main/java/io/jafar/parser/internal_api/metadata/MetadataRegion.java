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

    MetadataRegion(RecordingStream stream, MetadataEvent event) throws IOException {
        super(stream, MetadataElementKind.REGION);
        readSubelements(event);
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
            long mixed = dst * 0x9E3779B97F4A7C15L + gmtOffset * 0xC6BC279692B5C323L + Objects.hashCode(locale) * 0xD8163841FDE6A8F9L;
            hashCode = Long.hashCode(mixed);
            hasHashCode = true;
        }
        return hashCode;
    }
}

package io.jafar.parser.internal_api.metadata;

import io.jafar.parser.internal_api.RecordingStream;

import java.io.IOException;

final class MetadataSetting extends AbstractMetadataElement {
    private final String value;
    private final long typeId;

    public MetadataSetting(RecordingStream stream, ElementReader reader) throws IOException {
        super(stream, MetadataElementKind.SETTING);
        this.value = getAttribute("defaultValue");
        this.typeId = Long.parseLong(getAttribute("class"));
        resetAttributes();
        readSubelements(reader);
    }

    public String getValue() {
        return value;
    }

    public MetadataClass getType() {
        return metadataLookup.getClass(typeId);
    }

    @Override
    protected void onSubelement(AbstractMetadataElement element) {
        throw new IllegalStateException("Unexpected subelement: " + element.getKind());
    }

    @Override
    public void accept(MetadataVisitor visitor) {
        visitor.visitSetting(this);
        visitor.visitEnd(this);
    }

    @Override
    public String toString() {
        return "MetadataSetting{" +
                "type='" + (getType() != null ? getType().getName() : typeId) + "'" +
                ", name='" + getName() + "'" +
                ", value='" + value + '\'' +
                '}';
    }
}

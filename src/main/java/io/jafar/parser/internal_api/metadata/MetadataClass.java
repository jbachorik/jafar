package io.jafar.parser.internal_api.metadata;

import io.jafar.parser.JFRValueDeserializer;
import io.jafar.parser.internal_api.RecordingStream;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class MetadataClass extends AbstractMetadataElement {
    private static final Set<String> primitiveTypeNames = Set.of("byte", "char", "short", "int", "long", "float", "double", "boolean", "java.lang.String");
    private final Map<String, MetadataSetting> settings = new HashMap<>();
    private final List<MetadataAnnotation> annotations = new ArrayList<>();
    private final Map<String, MetadataField> fieldMap = new HashMap<>();
    private final List<MetadataField> fields = new ArrayList<>();

    private final long id;
    private final String superType;
    private final boolean isSimpleType;
    private final boolean isPrimitive;

    private JFRValueDeserializer<?> deserializer;

    MetadataClass(RecordingStream stream, ElementReader reader) throws IOException {
        super(stream, MetadataElementKind.CLASS);
        this.id = Long.parseLong(getAttribute("id"));
        this.superType = getAttribute("superType");
        this.isSimpleType = Boolean.parseBoolean(getAttribute("simpleType"));
        this.isPrimitive = primitiveTypeNames.contains(getName());
        metadataLookup.addClass(id, this);
        resetAttributes();
        readSubelements(reader);
    }

    public void setDeserializer(JFRValueDeserializer<?> deserializer) {
        this.deserializer = deserializer;
    }

    public JFRValueDeserializer<?> getDeserializer() {
        return deserializer;
    }

    public long getId() {
        return id;
    }

    public String getSuperType() {
        return superType;
    }

    public boolean isSimple() {
        return isSimpleType;
    }

    public boolean isPrimitive() {
        return isPrimitive;
    }

    protected void onSubelement(AbstractMetadataElement element) {
        if (element.getKind() == MetadataElementKind.SETTING) {
            MetadataSetting setting = (MetadataSetting) element;
            settings.put(setting.getName(), setting);
        } else if (element.getKind() == MetadataElementKind.ANNOTATION) {
            annotations.add((MetadataAnnotation) element);
        } else if (element.getKind() == MetadataElementKind.FIELD) {
            MetadataField field = (MetadataField) element;
            fieldMap.put(field.getName(), field);
            fields.add(field);
        } else {
            throw new IllegalStateException("Unexpected subelement: " + element.getKind());
        }
    }

    @Override
    public void accept(MetadataVisitor visitor) {
        visitor.visitClass(this);
        settings.values().forEach(s -> s.accept(visitor));
        annotations.forEach(a -> a.accept(visitor));
        fieldMap.values().forEach(f -> f.accept(visitor));
        visitor.visitEnd(this);
    }

    public List<MetadataField> getFields() {
        return Collections.unmodifiableList(fields);
    }

    public Set<String> getFieldNames() {
        return fieldMap.keySet();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MetadataClass that = (MetadataClass) o;
        return id == that.id && name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name);
    }

    @Override
    public String toString() {
        return "MetadataClass{" +
                "id='" + id + '\'' +
                ", name='" + getName() + "'" +
                ", superType='" + superType + '\'' +
                ", isSimpleType=" + isSimpleType +
                '}';
    }
}

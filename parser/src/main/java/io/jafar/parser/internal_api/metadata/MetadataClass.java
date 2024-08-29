package io.jafar.parser.internal_api.metadata;

import io.jafar.parser.ParsingUtils;
import io.jafar.parser.ValueLoader;
import io.jafar.parser.internal_api.RecordingStream;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
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
    private final boolean simple;

    private final int associatedChunk;

    private volatile MethodHandle skipHandler;

    MetadataClass(RecordingStream stream, ElementReader reader) throws IOException {
        super(stream, MetadataElementKind.CLASS);
        this.id = Long.parseLong(getAttribute("id"));
        this.superType = getAttribute("superType");
        this.isSimpleType = Boolean.parseBoolean(getAttribute("simpleType"));
        this.isPrimitive = primitiveTypeNames.contains(getName());
        this.simple = isSimpleType || isPrimitive(name);
        this.associatedChunk = stream.getContext().getChunkIndex();
        metadataLookup.addClass(id, this);
        resetAttributes();
        readSubelements(reader);
        if (getName().equals("java.lang.String")) {
            try {
                skipHandler = MethodHandles.lookup().findStatic(ParsingUtils.class, "skipUTF8", MethodType.methodType(void.class, RecordingStream.class));
            } catch (Throwable t) {
                // should never happen unless the method name or signature changes
                throw new RuntimeException(t);
            }
        }
    }

    public long getId() {
        return id;
    }

    public String getSuperType() {
        return superType;
    }

    public boolean isSimple() {
        return simple;
    }

    public boolean isPrimitive() {
        return isPrimitive;
    }

    public void setSkipHandler(MethodHandle handler) {
        this.skipHandler = handler;
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

    public void skip(RecordingStream stream) throws IOException {
        try {
            skipHandler.invokeExact(stream);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private static boolean isPrimitive(String typeName) {
        return typeName.equals("byte") ||
                typeName.equals("short") ||
                typeName.equals("char") ||
                typeName.equals("int") ||
                typeName.equals("long") ||
                typeName.equals("float") ||
                typeName.equals("double") ||
                typeName.equals("boolean") ||
                typeName.equals("java.lang.String");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MetadataClass that = (MetadataClass) o;
        return id == that.id && associatedChunk == that.associatedChunk;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, associatedChunk);
    }

    @Override
    public String toString() {
        return "MetadataClass{" +
                "id='" + id + '\'' +
                ", chunk=" + associatedChunk +
                ", name='" + getName() + "'" +
                ", superType='" + superType + '\'' +
                ", isSimpleType=" + isSimpleType +
                '}';
    }
}

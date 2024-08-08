package io.jafar.parser;

import io.jafar.parser.internal_api.ConstantPool;
import io.jafar.parser.internal_api.ConstantPools;
import io.jafar.parser.internal_api.RecordingStream;
import io.jafar.parser.internal_api.metadata.MetadataClass;
import io.jafar.parser.internal_api.metadata.MetadataField;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ValueLoader {
    public static void skip(RecordingStream stream, MetadataClass typeDescriptor, boolean isArray, boolean hasConstantPool) throws IOException {
        if (isArray) {
            int len = (int) stream.readVarint();
            for (int i = 0; i < len; i++) {
                skip(stream, typeDescriptor, false, hasConstantPool);
            }
        } else {
            if (hasConstantPool) {
                stream.readVarint();
            } else {
                if (isSimple(typeDescriptor)) {
                    skipSimpleTypedValue(stream, typeDescriptor);
                } else {
                    List<MetadataField> fields = typeDescriptor.getFields();
                    for (MetadataField fld : fields) {
                        skip(stream, fld.getType(), fld.getDimension() > 0, fld.hasConstantPool());
                    }
                }
            }
        }
    }

    private static void skipSimpleTypedValue(RecordingStream stream, MetadataClass typeDescriptor) throws IOException {
        switch (typeDescriptor.getName()) {
            case "byte", "boolean" -> stream.read();
            case "short", "char", "int", "long" -> stream.readVarint();
            case "float" -> stream.readFloat();
            case "double" -> stream.readDouble();
            case "java.lang.String" -> ParsingUtils.skipUTF8(stream);
            default -> {
                if (typeDescriptor.getFields().size() == 1) {
                    skipSimpleTypedValue(stream, typeDescriptor.getFields().get(0).getType());
                }
            }
        }
    }

    private static boolean isSimple(MetadataClass type) {
        String typeName = type.getName();
        return type.isSimple() ||
                typeName.equals("byte") ||
                typeName.equals("short") ||
                typeName.equals("char") ||
                typeName.equals("int") ||
                typeName.equals("long") ||
                typeName.equals("float") ||
                typeName.equals("double") ||
                typeName.equals("boolean") ||
                typeName.equals("java.lang.String");
    }
}

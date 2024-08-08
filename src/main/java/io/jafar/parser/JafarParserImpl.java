package io.jafar.parser;

import io.jafar.parser.api.Control;
import io.jafar.parser.api.JafarParser;
import io.jafar.parser.api.JfrField;
import io.jafar.parser.api.JfrIgnore;
import io.jafar.parser.api.JfrType;
import io.jafar.parser.api.types.JFREvent;
import io.jafar.parser.api.types.JFRHandler;
import io.jafar.parser.internal_api.ChunkHeader;
import io.jafar.parser.internal_api.ChunkParserListener;
import io.jafar.parser.internal_api.ConstantPool;
import io.jafar.parser.internal_api.ConstantPools;
import io.jafar.parser.internal_api.MetadataLookup;
import io.jafar.parser.internal_api.ParserContext;
import io.jafar.parser.internal_api.RecordingStream;
import io.jafar.parser.internal_api.StreamingChunkParser;
import io.jafar.parser.internal_api.metadata.MetadataClass;
import io.jafar.parser.internal_api.metadata.MetadataEvent;
import io.jafar.parser.internal_api.metadata.MetadataField;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.checkerframework.checker.units.qual.C;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.AccessFlag;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class JafarParserImpl implements JafarParser {
    private final StreamingChunkParser parser;
    private final Path recording;

    private final Map<Class<? extends JFREvent>, List<JFRHandler.Impl<?>>> handlerMap = new HashMap<>();
    private final Long2ObjectMap<Class<?>> typeClassMap = new Long2ObjectOpenHashMap<>();
    private final Map<String, JFRValueDeserializer<?>> deserializerMap = new HashMap<>();
    private final Map<Class<?>, MethodHandle> handlerMethodMap = new HashMap<>();

    public JafarParserImpl(Path recording) {
        this.parser = new StreamingChunkParser();
        this.recording = recording;
    }

    @Override
    public <T extends JFREvent> JafarParser handle(Class<T> clz, JFRHandler<T> handler) {
        addDeserializer(clz);
        handlerMap.computeIfAbsent(clz, k -> new ArrayList<>()).add(new JFRHandler.Impl<>(clz, handler));

        return this;
    }

    private void addDeserializer(Class<?> clz) {
        if (clz.isArray()) {
            clz = clz.getComponentType();
        }
        boolean isPrimitive = clz.isPrimitive() || clz.isAssignableFrom(String.class);

        if (!isPrimitive && !clz.isInterface()) {
            throw new RuntimeException("JFR type handler must be an interface: " + clz.getName());
        }
        String typeName = clz.getName();
        if (!isPrimitive) {
            JfrType typeAnnotation = clz.getAnnotation(JfrType.class);
            if (typeAnnotation == null) {
                throw new RuntimeException("JFR type annotation missing on class: " + clz.getName());
            }
            typeName = typeAnnotation.value();
        }

        if (deserializerMap.containsKey(typeName)) {
            return;
        }
        deserializerMap.put(typeName, JFRValueDeserializer.create(clz));
        if (!isPrimitive) {
            Class<?> superClass = clz.getSuperclass();
            if (superClass != null && superClass.isInterface()) {
                addDeserializer(superClass);
            }
            for (Method m : clz.getMethods()) {
                if (m.getAnnotation(JfrIgnore.class) == null) {
                    addDeserializer(m.getReturnType());
                }
            }
        }

    }

    private static String asDescriptor(Class<?> type) {
        return switch (type.getName()) {
            case "boolean" -> "Z";
            case "byte" -> "B";
            case "char" -> "C";
            case "short" -> "S";
            case "int" -> "I";
            case "long" -> "J";
            case "float" -> "F";
            case "double" -> "D";
            default -> "L" + type.getName().replace('.', '/') + ";";
        };
    }

    private void castAndUnbox(MethodVisitor mv, Class<?> clz) {
        if (!clz.isPrimitive()) {
            throw new RuntimeException("Not a primitive type: " + clz.getName());
        }
        if (clz == int.class) {
            mv.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(Integer.class));
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(Integer.class), "intValue", Type.getMethodDescriptor(Type.INT_TYPE));
        } else if (clz == long.class) {
            mv.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(Long.class));
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(Long.class), "longValue", Type.getMethodDescriptor(Type.LONG_TYPE));
        } else if (clz == short.class) {
            mv.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(Short.class));
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(Short.class), "shortValue", Type.getMethodDescriptor(Type.SHORT_TYPE));
        } else if (clz == char.class) {
            mv.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(Character.class));
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(Character.class), "charValue", Type.getMethodDescriptor(Type.CHAR_TYPE));
        } else if (clz == byte.class) {
            mv.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(Byte.class));
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(Byte.class), "byteValue", Type.getMethodDescriptor(Type.BYTE_TYPE));
        } else if (clz == double.class) {
            mv.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(Double.class));
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(Double.class), "doubleValue", Type.getMethodDescriptor(Type.DOUBLE_TYPE));
        } else if (clz == float.class) {
            mv.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(Float.class));
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(Float.class), "floatValue", Type.getMethodDescriptor(Type.FLOAT_TYPE));
        } else if (clz == boolean.class) {
            mv.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(Boolean.class));
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(Boolean.class), "booleanValue", Type.getMethodDescriptor(Type.BOOLEAN_TYPE), false);
        } else {
            throw new RuntimeException("Unsupported primitive type: " + clz.getName());
        }
    }

    private void addLog(MethodVisitor mv, String msg) {
        if (false) {
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(System.class), "out", Type.getMethodDescriptor(Type.getType(PrintStream.class)), false);
            mv.visitLdcInsn(msg);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(PrintStream.class), "println", Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(String.class)), false);
        }
    }

    private Set<String> collectUsedAttributes(Class<?> clz, Map<String, String> fieldToMethodMap) {
        Set<String> usedAttributes = new HashSet<>();
        Class<?> c = clz;
        while (c != null) {
            usedAttributes.addAll(Arrays.stream(c.getMethods())
                    .filter(m -> m.getAnnotation(JfrIgnore.class) == null)
                    .map(m -> {
                        String name = m.getName();
                        JfrField fieldAnnotation = m.getAnnotation(JfrField.class);
                        if (fieldAnnotation != null) {
                            name = fieldAnnotation.value();
                            fieldToMethodMap.put(name, m.getName());
                        }
                        return name;
                    })
                    .collect(Collectors.toSet()));
            Class<?> superClz = c.getSuperclass();
            if (superClz != null && superClz.isInterface()) {
                c = superClz;
            } else {
                c = null;
            }
        }
        return usedAttributes;
    }

    private void handleFieldRef(ClassVisitor cv, String clzName, MetadataField field, Class<?> fldType, String fldRefName, String methodName) {
        boolean isArray = field.getDimension() > 0;
        cv.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, fldRefName, (isArray ? "[" : "") + "J", null, null).visitEnd();
        MethodVisitor mv = cv.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, methodName, "()" + (isArray ? "[" : "") + Type.getDescriptor(fldType), null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitFieldInsn(Opcodes.GETFIELD, clzName.replace('.', '/'), "context", Type.getDescriptor(ParserContext.class));
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(ParserContext.class), "getConstantPools", Type.getMethodDescriptor(Type.getType(ConstantPools.class)), false);
        mv.visitLdcInsn(field.getTypeId());
        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, Type.getInternalName(ConstantPools.class), "getConstantPool", Type.getMethodDescriptor(Type.getType(ConstantPool.class), Type.LONG_TYPE), true);
        mv.visitInsn(Opcodes.DUP);
        mv.visitVarInsn(Opcodes.ASTORE, 1);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        if (isArray) {
            mv.visitFieldInsn(Opcodes.GETFIELD, clzName.replace('.', '/'), fldRefName, "[" + Type.LONG_TYPE.getDescriptor()); // [fld]
            mv.visitInsn(Opcodes.DUP); // [fld, fld]
            mv.visitVarInsn(Opcodes.ASTORE, 2); // [fld]
            mv.visitFieldInsn(Opcodes.GETFIELD, Type.getInternalName(long[].class), "length", Type.getDescriptor(int.class)); // [int]
            mv.visitInsn(Opcodes.DUP); // [int, int]
            mv.visitVarInsn(Opcodes.ISTORE, 3); // [int]
            mv.visitTypeInsn(Opcodes.ANEWARRAY, Type.getInternalName(fldType)); // [array]
            mv.visitLdcInsn(0); // [array, int]
            mv.visitVarInsn(Opcodes.ISTORE, 4); // [array]
            Label l1 = new Label();
            Label l2 = new Label();
            mv.visitLabel(l1);
            mv.visitVarInsn(Opcodes.ILOAD, 3); // [array, int]
            mv.visitVarInsn(Opcodes.ILOAD, 4); // [array, int, int]
            mv.visitJumpInsn(Opcodes.IF_ICMPEQ, l2); // [array]
            mv.visitInsn(Opcodes.DUP); // [array, array]
            mv.visitVarInsn(Opcodes.ILOAD, 4); // [array, array, int]
            mv.visitInsn(Opcodes.DUP); // [array, array, int, int]
            mv.visitVarInsn(Opcodes.ALOAD, 1); // [array, array, int, int, cp]
            mv.visitVarInsn(Opcodes.ALOAD, 2); // [array, array, int, int, fld]
            mv.visitInsn(Opcodes.SWAP); // [array, array, int, fld, int]
            mv.visitInsn(Opcodes.LALOAD); // [array, array, int, long]
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, Type.getInternalName(ConstantPool.class), "get", Type.getMethodDescriptor(Type.getType(Object.class), Type.LONG_TYPE), true); // [array, array, int, obj]
            mv.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(fldType)); // [array, array, int, fldval]
            mv.visitInsn(Opcodes.AASTORE); // [array]
            mv.visitIincInsn(4, 1); // [array]
            mv.visitJumpInsn(Opcodes.GOTO, l1);
            mv.visitLabel(l2);
        } else {
            mv.visitFieldInsn(Opcodes.GETFIELD, clzName.replace('.', '/'), fldRefName, Type.LONG_TYPE.getDescriptor());
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, Type.getInternalName(ConstantPool.class), "get", Type.getMethodDescriptor(Type.getType(Object.class), Type.LONG_TYPE), true);
            mv.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(fldType));
        }
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void handleField(ClassVisitor cv, String clzName, MetadataField field, Class<?> fldType, String fieldName, String methodName) {
        boolean isArray = field.getDimension() > 0;
        String fldDescriptor = (isArray ? "[" : "") + Type.getDescriptor(fldType);
        cv.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, fieldName, fldDescriptor, null, null).visitEnd();
        MethodVisitor mv = cv.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, methodName, "()" + (isArray ? "[" : "") + Type.getDescriptor(fldType), null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0); // [this]
        mv.visitFieldInsn(Opcodes.GETFIELD, clzName.replace('.', '/'), fieldName, fldDescriptor); // [fld]
        if (isArray) {
            mv.visitInsn(Opcodes.ARETURN);
        } else {
            mv.visitInsn(Type.getType(fldType).getOpcode(Opcodes.IRETURN));
        }
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void prepareConstructor(ClassVisitor cv,  String clzName, MetadataClass clz, List<MetadataField> allFields, Set<MetadataField> appliedFields) {
        MethodVisitor mv = cv.visitMethod(Opcodes.ACC_PUBLIC, "<init>", Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(RecordingStream.class)), null, null);
        mv.visitCode();
        int contextIdx = 2;
        int deserializersIdx = 3;
        int meteadataIdx = 4;
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, Type.getInternalName(Object.class), "<init>", Type.getMethodDescriptor(Type.VOID_TYPE), false);
        // store context field
        addLog(mv, "Reading object of type: " + clz.getName());
        mv.visitVarInsn(Opcodes.ALOAD,0); // [this]
        mv.visitVarInsn(Opcodes.ALOAD,1); // [this, pc]
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(RecordingStream.class), "getContext", Type.getMethodDescriptor(Type.getType(ParserContext.class)), false); // [this, ctx]
        mv.visitInsn(Opcodes.DUP); // [this, ctx, ctx]
        mv.visitVarInsn(Opcodes.ASTORE, contextIdx); // [this, ctx]
        mv.visitInsn(Opcodes.DUP); // [this, ctx, ctx]
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(ParserContext.class), "getDeserializers", Type.getMethodDescriptor(Type.getType(Deserializers.class)), false); // [this, ctx, deserializers]
        mv.visitVarInsn(Opcodes.ASTORE, deserializersIdx); // [this, ctx]
        mv.visitInsn(Opcodes.DUP); // [this, ctx, ctx]
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(ParserContext.class), "getMetadataLookup", Type.getMethodDescriptor(Type.getType(MetadataLookup.class)), false); // [this, ctx, metadata]
        mv.visitVarInsn(Opcodes.ASTORE, meteadataIdx); // [this, ctx]
        mv.visitFieldInsn(Opcodes.PUTFIELD, clzName.replace('.', '/'), "context", Type.getDescriptor(ParserContext.class)); // []

        for (MetadataField fld : allFields) {;
            boolean withConstantPool = fld.hasConstantPool(); // || fld.getType().getName().equals("java.lang.String");
            if (!appliedFields.contains(fld)) {
                // skip
                addLog(mv, "Skipping field: " + fld.getName());
                mv.visitVarInsn(Opcodes.ALOAD, 1); // [stream]
                mv.visitVarInsn(Opcodes.ALOAD, meteadataIdx); // [stream, metadata]
                mv.visitLdcInsn(fld.getTypeId()); // [stream, metadata, long, long]
                mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, Type.getInternalName(MetadataLookup.class), "getClass", Type.getMethodDescriptor(Type.getType(MetadataClass.class), Type.LONG_TYPE), true); // [stream, metadata, class]
                mv.visitLdcInsn(fld.getDimension() > 0 ? 1 : 0); // [stream, metadata, class, boolean]
                mv.visitLdcInsn(withConstantPool ? 1 : 0); // [stream, metadata, class, boolean, boolean]
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(ValueLoader.class), "skip", Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(RecordingStream.class), Type.getType(MetadataClass.class), Type.BOOLEAN_TYPE, Type.BOOLEAN_TYPE), false); // []
                continue;
            }
            if (withConstantPool) {
                String fldRefName = fld.getName() + "_ref";
                if (fld.getDimension() > 0) {
                    int arraySizeIdx = 5;
                    int arrayCounterIdx = 6;
                    Label l1 = new Label();
                    Label l2 = new Label();
                    addLog(mv, "Reading array of refs for field: " + fld.getName());
                    mv.visitVarInsn(Opcodes.ALOAD, 0); // [this]
                    mv.visitVarInsn(Opcodes.ALOAD, 1); // [this, stream]
                    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(RecordingStream.class), "readVarint", Type.getMethodDescriptor(Type.LONG_TYPE), false); // [this, long]
                    mv.visitInsn(Opcodes.L2I); // [this, int]
                    mv.visitInsn(Opcodes.DUP); // [this, int, int]
                    mv.visitVarInsn(Opcodes.ISTORE, arraySizeIdx); // [this, int]
                    mv.visitTypeInsn(Opcodes.NEWARRAY, Type.LONG_TYPE.getInternalName()); // [this, array]
                    mv.visitLdcInsn(0); // [this, array, int]
                    mv.visitVarInsn(Opcodes.ISTORE, arrayCounterIdx); // [this, array]
                    mv.visitLabel(l1);
                    mv.visitVarInsn(Opcodes.ILOAD, arraySizeIdx); // [this, array, int]
                    mv.visitVarInsn(Opcodes.ILOAD, arrayCounterIdx); // [this, array, int, int]
                    mv.visitJumpInsn(Opcodes.IF_ICMPEQ, l2); // [this, array]
                    mv.visitInsn(Opcodes.DUP); // [this, array, array]
                    mv.visitVarInsn(Opcodes.ILOAD, arrayCounterIdx); // [this, array, array, int]
                    mv.visitVarInsn(Opcodes.ALOAD, 1); // [this, array, array, int, stream]
                    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(RecordingStream.class), "readVarint", Type.getMethodDescriptor(Type.LONG_TYPE), false); // [this, array, array, int, long]
                    mv.visitInsn(Opcodes.LASTORE); // [this, array]
                    mv.visitIincInsn(arrayCounterIdx, 1); // [this, array]
                    mv.visitJumpInsn(Opcodes.GOTO, l1); // [this, array]
                    mv.visitLabel(l2);
                    mv.visitFieldInsn(Opcodes.PUTFIELD, clzName.replace('.', '/'), fldRefName, "[" + Type.LONG_TYPE.getDescriptor()); // []
                } else {
                    addLog(mv, "Reading ref for field: " + fld.getName());
                    mv.visitVarInsn(Opcodes.ALOAD, 0); // [this]
                    mv.visitVarInsn(Opcodes.ALOAD, 1); // [this, stream]
                    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(RecordingStream.class), "readVarint", Type.getMethodDescriptor(Type.LONG_TYPE), false); // [this, long]
                    mv.visitFieldInsn(Opcodes.PUTFIELD, clzName.replace('.', '/'), fldRefName, Type.LONG_TYPE.getDescriptor()); // []
                }
            } else {
                Class<?> fldClz = typeClassMap.get(fld.getType().getId());
                if (fldClz == null) {
                    throw new RuntimeException("Unknown field type: " + fld.getType().getName());
                }
                if (fld.getDimension() > 0) {
                    int arraySizeIdx = 5;
                    int arrayCounterIdx = 6;
                    Label l1 = new Label();
                    Label l2 = new Label();
                    addLog(mv, "Reading array field: " + fld.getName());
                    mv.visitVarInsn(Opcodes.ALOAD, 0); // [this]
                    mv.visitVarInsn(Opcodes.ALOAD, 1); // [this, stream]
                    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(RecordingStream.class), "readVarint", Type.getMethodDescriptor(Type.LONG_TYPE), false); // [this, long]
                    mv.visitInsn(Opcodes.L2I); // [this, int]
                    mv.visitInsn(Opcodes.DUP); // [this, int, int]
                    mv.visitVarInsn(Opcodes.ISTORE, arraySizeIdx); // [this, int]
                    mv.visitTypeInsn(Opcodes.ANEWARRAY, Type.getInternalName(fldClz)); // [this, array]
                    mv.visitLdcInsn(0); // [this, array, int]
                    mv.visitVarInsn(Opcodes.ISTORE, arrayCounterIdx); // [this, array]
                    mv.visitLabel(l1);
                    mv.visitVarInsn(Opcodes.ILOAD, arraySizeIdx); // [this, array, int]
                    mv.visitVarInsn(Opcodes.ILOAD, arrayCounterIdx); // [this, array, int, int]
                    mv.visitJumpInsn(Opcodes.IF_ICMPEQ, l2); // [this, array]
                    mv.visitInsn(Opcodes.DUP); // [this, array, array]
                    mv.visitVarInsn(Opcodes.ILOAD, arrayCounterIdx); // [this, array, array, int]
                    mv.visitVarInsn(Opcodes.ALOAD, deserializersIdx); // [this, array, array, int, deserializers]
                    mv.visitLdcInsn(fld.getType().getId()); // [this, array, array, int, deserializers, long]
                    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(Deserializers.class), "getDeserializer", Type.getMethodDescriptor(Type.getType(JFRValueDeserializer.class), Type.LONG_TYPE), false); // [this, array, array, int, deserializer]
                    mv.visitVarInsn(Opcodes.ALOAD, 1); // [this, array, array, int, deserializer, stream]
                    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(JFRValueDeserializer.class), "deserialize", Type.getMethodDescriptor(Type.getType(Object.class), Type.getType(RecordingStream.class)), false); // [this, array, array, int, obj]
                    mv.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(fldClz)); // [this, array, array, int, fldval]
                    mv.visitInsn(Opcodes.AASTORE); // [this, array]
                    mv.visitIincInsn(arrayCounterIdx, 1); // [this, array]
                    mv.visitJumpInsn(Opcodes.GOTO, l1); // [this, array]
                    mv.visitLabel(l2);
                    mv.visitFieldInsn(Opcodes.PUTFIELD, clzName.replace('.', '/'), fld.getName(), "[" + Type.getDescriptor(fldClz)); // []
                } else {
                    addLog(mv, "Reading field: " + fld.getName());
                    mv.visitVarInsn(Opcodes.ALOAD, 0); // [this]
                    mv.visitVarInsn(Opcodes.ALOAD, deserializersIdx); // [this, deserializers]
                    mv.visitLdcInsn(fld.getType().getId()); // [this, deserializers, long]
                    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(Deserializers.class), "getDeserializer", Type.getMethodDescriptor(Type.getType(JFRValueDeserializer.class), Type.LONG_TYPE), false); // [this, deserializer]
                    mv.visitVarInsn(Opcodes.ALOAD, 1); // [this, deserializer, stream]
                    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(JFRValueDeserializer.class), "deserialize", Type.getMethodDescriptor(Type.getType(Object.class), Type.getType(RecordingStream.class)), false); // [this, obj]
                    if (!fldClz.isPrimitive()) {
                        mv.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(fldClz)); // [this, fldval]
                    } else {
                        castAndUnbox(mv, fldClz); // [this, fldval]
                    }
                    mv.visitFieldInsn(Opcodes.PUTFIELD, clzName.replace('.', '/'), fld.getName(), Type.getDescriptor(fldClz)); // []
                }
            }
        }
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private MethodHandle getHandlerMethod(MetadataClass mdClass, Class<?> clz, ParserContext context) {
        Map<String, String> fieldToMethodMap = new HashMap<>();
        MethodHandle mh = handlerMethodMap.get(clz);
        if (mh != null) {
            return mh;
        }
        try {
            if (clz == int.class || clz == Integer.class) {
                mh = MethodHandles.explicitCastArguments(MethodHandles.lookup().findVirtual(RecordingStream.class, "readVarint", MethodType.methodType(long.class)), MethodType.methodType(int.class, RecordingStream.class));
            } else if (clz == long.class || clz == Long.class) {
                mh = MethodHandles.lookup().findVirtual(RecordingStream.class, "readVarint", MethodType.methodType(long.class));
            } else if (clz == short.class || clz == Short.class) {
                mh = MethodHandles.explicitCastArguments(MethodHandles.lookup().findVirtual(RecordingStream.class, "readVarint", MethodType.methodType(long.class)), MethodType.methodType(short.class));
            } else if (clz == char.class || clz == Character.class) {
                mh = MethodHandles.explicitCastArguments(MethodHandles.lookup().findVirtual(RecordingStream.class, "readVarint", MethodType.methodType(long.class)), MethodType.methodType(char.class));
            } else if (clz == byte.class || clz == Byte.class) {
                mh = MethodHandles.explicitCastArguments(MethodHandles.lookup().findVirtual(RecordingStream.class, "readVarint", MethodType.methodType(long.class)), MethodType.methodType(byte.class));
            } else if (clz == double.class || clz == Double.class) {
                mh = MethodHandles.lookup().findVirtual(RecordingStream.class, "readDouble", MethodType.methodType(double.class));
            } else if (clz == float.class || clz == Float.class) {
                mh = MethodHandles.lookup().findVirtual(RecordingStream.class, "readFloat", MethodType.methodType(float.class));
            } else if (clz == boolean.class || clz == Boolean.class) {
                mh = MethodHandles.lookup().findVirtual(RecordingStream.class, "readBoolean", MethodType.methodType(boolean.class));
            } else if (clz == String.class) {
                mh = MethodHandles.lookup().findStatic(ParsingUtils.class, "readUTF8", MethodType.methodType(String.class, RecordingStream.class));
            } else {
                if (!clz.isInterface()) {
                    throw new RuntimeException("Unsupported type: " + clz.getName());
                }
                String clzName = JafarParserImpl.class.getPackage().getName() + "." + clz.getSimpleName() + "$" + UUID.randomUUID();
                // generate handler class
                ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
                cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, clzName.replace('.', '/'), null, "java/lang/Object", new String[]{clz.getName().replace('.', '/')});
                cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "context", Type.getDescriptor(ParserContext.class), null, null).visitEnd();

                Set<String> usedAttributes = collectUsedAttributes(clz, fieldToMethodMap);

                Deque<MetadataClass> stack = new ArrayDeque<>();
                stack.push(mdClass);
                // TODO ignore inheritence for now
//                        while (true) {
//                            MetadataClass superMd = context.getMetadataLookup().;
//                            String superName = mdClass.getSuperType();
//                            if (superName != null) {
//                                stack.push(context.getMetadataLookup().getClass(mdClass.getId()));
//                            } else {
//                                break;
//                            }
//                        }

                List<MetadataField> allFields = new ArrayList<>();
                Set<MetadataField> appliedFields = new HashSet<>();
                while (!stack.isEmpty()) {
                    MetadataClass current = stack.pop();
                    for (MetadataField field : current.getFields()) {
                        String fieldName = field.getName();
                        allFields.add(field);
                        if (!usedAttributes.contains(fieldName)) {
                            continue;
                        }
                        appliedFields.add(field);

                        Class<?> fldClz = typeClassMap.get(field.getType().getId());
                        boolean withConstantPool = field.hasConstantPool();
                        String methodName = fieldToMethodMap.get(fieldName);
                        if (methodName == null) {
                            methodName = fieldName;
                        }
                        if (withConstantPool) {
                            handleFieldRef(cw, clzName, field, fldClz, fieldName + "_ref", methodName);
                        } else {
                            handleField(cw, clzName, field, fldClz, fieldName, methodName);
                        }
                    }
                    prepareConstructor(cw, clzName, current, allFields, appliedFields);
                }
                cw.visitEnd();
                byte[] classData = cw.toByteArray();

                Files.write(Paths.get("/tmp/"+ clz.getSimpleName() + ".class"), classData);

                MethodHandles.Lookup lkp = MethodHandles.lookup().defineHiddenClass(classData, true, MethodHandles.Lookup.ClassOption.NESTMATE);
                mh = lkp.findConstructor(lkp.lookupClass(), MethodType.methodType(void.class, RecordingStream.class));
            }
            return mh;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @Override
    public void run() throws IOException {
        // parse JFR and run handlers
        parser.parse(openJfrStream(recording), new ChunkParserListener() {
            private final Control ctl = new Control();

            @Override
            public boolean onChunkStart(int chunkIndex, ChunkHeader header, ParserContext context) {
                if (!deserializerMap.isEmpty()) {
                    context.setTypeFilter(t -> deserializerMap.containsKey(t.getName()));
                    context.getDeserializers().clear();
                    typeClassMap.clear();
                    return true;
                }
                return false;
            }

            @Override
            public boolean onMetadata(MetadataEvent metadata) {
                for (MetadataClass clz : metadata.getClasses()) {
                    JFRValueDeserializer<?> deserializer = deserializerMap.get(clz.getName());
                    if (deserializer != null) {
                        metadata.getContext().getDeserializers().addDeserializer(clz.getId(), deserializer);
                        clz.setDeserializer(deserializer);
                        typeClassMap.putIfAbsent(clz.getId(), deserializer.getClazz());
                    }
                }

                for (MetadataClass clz : metadata.getClasses()) {
                    JFRValueDeserializer<?> deserializer = deserializerMap.get(clz.getName());
                    if (deserializer != null) {
                        deserializer.setHandler(getHandlerMethod(clz, deserializer.getClazz(), metadata.getContext()));
                    }
                }
                return true;
            }

            @Override
            public boolean onEvent(long typeId, RecordingStream stream, long payloadSize) {
                Class<?> typeClz = typeClassMap.get(typeId);
                if (typeClz == null) {
                    return true;
                }
                if (JFREvent.class.isAssignableFrom(typeClz) && handlerMap.containsKey(typeClz)) {
                    String typeName = typeClz.getAnnotation(JfrType.class).value();
                    JFRValueDeserializer<?> deserializer = deserializerMap.get(typeName);
                    if (deserializer != null) {
                        Object deserialized = deserializer.deserialize(stream);
                        if (deserialized instanceof JFREvent event) {
                            for (JFRHandler.Impl<?> handler : handlerMap.get(typeClz)) {
                                handler.handle(event, null);
                            }
                        }
                    }
                }

                return true;
            };
        });
    }

    private static ByteBuffer openJfrStream(Path jfrFile) {
        try (RandomAccessFile raf = new RandomAccessFile(jfrFile.toFile(), "r");
             FileChannel channel = raf.getChannel()) {
            return channel.map(FileChannel.MapMode.READ_ONLY, 0, raf.length());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

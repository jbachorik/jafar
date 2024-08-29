package io.jafar.parser;

import io.jafar.parser.internal_api.ConstantPool;
import io.jafar.parser.internal_api.ConstantPools;
import io.jafar.parser.internal_api.DeserializationHandler;
import io.jafar.parser.internal_api.MetadataLookup;
import io.jafar.parser.internal_api.ParserContext;
import io.jafar.parser.internal_api.RecordingStream;
import io.jafar.parser.internal_api.metadata.MetadataClass;
import io.jafar.parser.internal_api.metadata.MetadataField;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.PrintStream;
import java.util.List;
import java.util.Set;

final class CodeGenerator {

    private static final boolean LOGS_ENABLED = false;

    private static void castAndUnbox(MethodVisitor mv, Class<?> clz) {
        if (!clz.isPrimitive()) {
            throw new RuntimeException("Not a primitive type: " + clz.getName());
        }
        if (clz == int.class) {
            mv.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(Integer.class));
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(Integer.class), "intValue", Type.getMethodDescriptor(Type.INT_TYPE), false);
        } else if (clz == long.class) {
            mv.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(Long.class));
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(Long.class), "longValue", Type.getMethodDescriptor(Type.LONG_TYPE), false);
        } else if (clz == short.class) {
            mv.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(Short.class));
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(Short.class), "shortValue", Type.getMethodDescriptor(Type.SHORT_TYPE), false);
        } else if (clz == char.class) {
            mv.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(Character.class));
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(Character.class), "charValue", Type.getMethodDescriptor(Type.CHAR_TYPE), false);
        } else if (clz == byte.class) {
            mv.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(Byte.class));
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(Byte.class), "byteValue", Type.getMethodDescriptor(Type.BYTE_TYPE), false);
        } else if (clz == double.class) {
            mv.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(Double.class));
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(Double.class), "doubleValue", Type.getMethodDescriptor(Type.DOUBLE_TYPE), false);
        } else if (clz == float.class) {
            mv.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(Float.class));
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(Float.class), "floatValue", Type.getMethodDescriptor(Type.FLOAT_TYPE), false);
        } else if (clz == boolean.class) {
            mv.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(Boolean.class));
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(Boolean.class), "booleanValue", Type.getMethodDescriptor(Type.BOOLEAN_TYPE), false);
        } else {
            throw new RuntimeException("Unsupported primitive type: " + clz.getName());
        }
    }

    private static void addLog(MethodVisitor mv, String msg) {
        if (LOGS_ENABLED) {
            mv.visitFieldInsn(Opcodes.GETSTATIC, Type.getInternalName(System.class), "out", Type.getDescriptor(PrintStream.class));
            mv.visitLdcInsn(msg);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(PrintStream.class), "println", Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(String.class)), false);
        }
    }

    private static void addLogIntWithMsg(MethodVisitor mv, String msg) {
        if (LOGS_ENABLED) {
            // [int]
            mv.visitInsn(Opcodes.DUP); // [int, int]
            mv.visitFieldInsn(Opcodes.GETSTATIC, Type.getInternalName(System.class), "out", Type.getDescriptor(PrintStream.class)); // [int, int, out]
            mv.visitInsn(Opcodes.DUP); // [int, int, out, out]
            mv.visitLdcInsn(msg); // [int, int, out, out, msg]
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(PrintStream.class), "print", Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(String.class)), false); // [int, int, out]
            mv.visitInsn(Opcodes.SWAP); // [int, out, int]
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(PrintStream.class), "println", Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(int.class)), false); // [int]
        }
    }

    static void handleFieldRef(ClassVisitor cv, String clzName, MetadataField field, Class<?> fldType, String fldRefName, String methodName) {
        // generate the cache-per-field fields
        cv.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, fldRefName + "_cls", Type.getDescriptor(MetadataClass.class), null, null).visitEnd();
        cv.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, fldRefName + "_hdl", Type.getDescriptor(DeserializationHandler.class), null, null).visitEnd();
        if (fldType == null) {
            // field is never accessed directly, can skip the rest
            return;
        }
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
            mv.visitInsn(Opcodes.ARRAYLENGTH); // [int]
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
            mv.visitInsn(Opcodes.SWAP); // [array, array, int, cp, int]
            mv.visitVarInsn(Opcodes.ALOAD, 2); // [array, array, int, cp, int, fld]
            mv.visitInsn(Opcodes.SWAP); // [array, array, int, cp, fld, int]
            mv.visitInsn(Opcodes.LALOAD); // [array, array, int, cp, long]
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

    static void handleField(ClassVisitor cv, String clzName, MetadataField field, Class<?> fldType, String fieldName, String methodName) {
        // generate the cache-per-field fields
        cv.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, fieldName + "_cls", Type.getDescriptor(MetadataClass.class), null, null).visitEnd();
        cv.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, fieldName + "_hdl", Type.getDescriptor(DeserializationHandler.class), null, null).visitEnd();
        if (fldType == null) {
            // field is never accessed directly, can skip the rest
            return;
        }

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

    static void addFieldSkipper(MethodVisitor mv, MetadataField fld, int streamIdx, int lastVarIdx) {
        // stack: [stream]
        if (fld.hasConstantPool()) {
            if (fld.getDimension() > 0) {
                skipArrayRef(lastVarIdx, mv); // []
            } else {
                skipSimpleRef(mv); // []
            }
        } else {
            if (fld.getDimension() > 0) {
                skipArrayField(fld, streamIdx, lastVarIdx, mv); // []
            } else {
                skipSimpleField(fld.getType(), streamIdx, lastVarIdx, false, mv); // []
            }
        }
    }

    static void addFieldLoader(MethodVisitor mv, MetadataField fld, String className, int streamIdx, int deserializersIdx, int lastVarIdx, Long2ObjectMap<Class<?>> typeClassMap) {
        // stack: [this, stream]
        if (fld.hasConstantPool()) {
            if (fld.getDimension() > 0) {
                handleArrayRef(fld, className,lastVarIdx, mv); // []
            } else {
                handleSimpleRef(fld, className, mv); // []
            }
        } else {
            if (fld.getDimension() > 0) {
                handleArrayField(fld, className, streamIdx, deserializersIdx, lastVarIdx, typeClassMap, mv); // []
            } else {
                handleSimpleField(fld, className, deserializersIdx, typeClassMap, mv); // []
            }
        }
    }

    private static void skipArrayRef(int lastVarIdx, MethodVisitor mv) {
        // stack: [stream]
        int arraySizeIdx = lastVarIdx + 1;
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(RecordingStream.class), "readVarint", Type.getMethodDescriptor(Type.LONG_TYPE), false); // [stream, long]
        mv.visitInsn(Opcodes.L2I);
        mv.visitVarInsn(Opcodes.ISTORE, arraySizeIdx); // [stream]
        Label l1 = new Label();
        mv.visitLabel(l1);
        mv.visitInsn(Opcodes.DUP); // [stream, stream]
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(RecordingStream.class), "readVarint", Type.getMethodDescriptor(Type.LONG_TYPE), false); // [stream, long]
        mv.visitInsn(Opcodes.POP2); // [stream]
        mv.visitVarInsn(Opcodes.ILOAD, arraySizeIdx); // [stream, int]
        mv.visitLdcInsn(1); // [stream, int, 1]
        mv.visitInsn(Opcodes.ISUB); // [stream, int]
        mv.visitInsn(Opcodes.DUP); // [stream, int, int]
        mv.visitVarInsn(Opcodes.ISTORE, arraySizeIdx); // [stream, int]
        mv.visitJumpInsn(Opcodes.IFNE, l1); // [stream]
        mv.visitInsn(Opcodes.POP); // []
    }

    private static void handleArrayRef(MetadataField fld, String className, int lastVarIdx, MethodVisitor mv) {
        // stack: [this, stream]
        int arrayCounterIdx = lastVarIdx + 1;
        int arraySizeIdx = arrayCounterIdx + 1;
        Label l1 = new Label();
        Label l2 = new Label();
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(RecordingStream.class), "readVarint", Type.getMethodDescriptor(Type.LONG_TYPE), false); // [this, long]
        mv.visitInsn(Opcodes.L2I); // [this, int]
        mv.visitInsn(Opcodes.DUP); // [this, int, int]
        mv.visitVarInsn(Opcodes.ISTORE, arraySizeIdx); // [this, int]
        mv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_LONG); // [this, array]
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
        mv.visitFieldInsn(Opcodes.PUTFIELD, className, fld.getName() + "_ref", "[" + Type.LONG_TYPE.getDescriptor()); // []
    }

    private static void skipSimpleRef(MethodVisitor mv) {
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(RecordingStream.class), "readVarint", Type.getMethodDescriptor(Type.LONG_TYPE), false); // [long]
        mv.visitInsn(Opcodes.POP2); // []
    }

    private static void handleSimpleRef(MetadataField fld, String className, MethodVisitor mv) {
        // stack: [this, stream]
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(RecordingStream.class), "readVarint", Type.getMethodDescriptor(Type.LONG_TYPE), false); // [this, long]
        mv.visitFieldInsn(Opcodes.PUTFIELD, className, fld.getName() + "_ref", Type.LONG_TYPE.getDescriptor()); // []
    }

    private static void skipArrayField(MetadataField fld, int streamIdx, int lastVarIdx, MethodVisitor mv) {
        // stack: [stream]
        String fldTypeName = fld.getType().getName();
        int arraySizeIdx = lastVarIdx + 1;
        lastVarIdx = arraySizeIdx;

        Type fldType = null;
        Type dataType = null;

        switch (fldTypeName) {
            case "byte", "boolean": {
                fldType = fldTypeName.equals("byte") ? Type.BYTE_TYPE : Type.BOOLEAN_TYPE;
                dataType = Type.BYTE_TYPE;
                break;
            }
            case "short", "char", "int", "long": {
                dataType = Type.LONG_TYPE;
                switch (fldTypeName) {
                    case "short": {
                        fldType = Type.SHORT_TYPE;
                        break;
                    }
                    case "char": {
                        fldType = Type.CHAR_TYPE;
                        break;
                    }
                    case "int": {
                        fldType = Type.INT_TYPE;
                        break;
                    }
                    case "long": {
                        fldType = Type.LONG_TYPE;
                        break;
                    }
                }
                break;
            }
            case "float": {
                dataType = Type.FLOAT_TYPE;
                fldType = Type.FLOAT_TYPE;
                break;
            }
            case "double": {
                dataType = Type.DOUBLE_TYPE;
                fldType = Type.DOUBLE_TYPE;
                break;
            }
        }
        if (fldType != null) {
            skipPrimitiveArray(dataType, arraySizeIdx, mv); // []
        } else if (fldTypeName.equals("java.lang.String")) {
            skipStringArray(arraySizeIdx, mv); // []
        } else {
            skipObjectArray(fld.getType(), arraySizeIdx, streamIdx, lastVarIdx, false, mv); // []
        }
    }

    private static void skipPrimitiveArray(Type dataType, int arraySizeIdx, MethodVisitor mv) {
        String operation = getPrimitiveReadOperation(dataType);
        // stack: [stream]
        Label l1 = new Label();
        Label l2 = new Label();
        Label l3 = new Label();
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(RecordingStream.class), "readVarint", Type.getMethodDescriptor(Type.LONG_TYPE), false); // [this, long]
        mv.visitInsn(Opcodes.L2I); // [this, int]
        mv.visitInsn(Opcodes.DUP); // [this, int, int]
        mv.visitJumpInsn(Opcodes.IFEQ, l2); // [this, int]
        mv.visitVarInsn(Opcodes.ISTORE, arraySizeIdx); // [stream]
        mv.visitLabel(l1);
        mv.visitInsn(Opcodes.DUP); // [stream, stream]
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(RecordingStream.class), operation, dataType.getDescriptor(), false); // [stream, value]
        mv.visitInsn(dataType.getSize() == 2 ? Opcodes.POP2 : Opcodes.POP); // [stream]
        mv.visitVarInsn(Opcodes.ILOAD, arraySizeIdx); // [stream, int]
        mv.visitLdcInsn(1); // [stream, int, 1]
        mv.visitInsn(Opcodes.ISUB); // [stream, int]
        mv.visitInsn(Opcodes.DUP); // [stream, int, int]
        mv.visitVarInsn(Opcodes.ISTORE, arraySizeIdx); // [stream, int]
        mv.visitJumpInsn(Opcodes.IFNE, l1); // [stream]
        mv.visitInsn(Opcodes.POP); // []
        mv.visitJumpInsn(Opcodes.GOTO, l3);
        mv.visitLabel(l2); // [int]
        mv.visitInsn(Opcodes.POP); // []
        mv.visitLabel(l3); // []
    }

    private static void skipStringArray(int arraySizeIdx, MethodVisitor mv) {
        // stack: [stream]
        Label l1 = new Label();
        Label l2 = new Label();
        Label l3 = new Label();
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(RecordingStream.class), "readVarint", Type.getMethodDescriptor(Type.LONG_TYPE), false); // [this, long]
        mv.visitInsn(Opcodes.L2I); // [this, int]
        mv.visitInsn(Opcodes.DUP); // [this, int, int]
        mv.visitJumpInsn(Opcodes.IFEQ, l2); // [this, int]
        mv.visitVarInsn(Opcodes.ISTORE, arraySizeIdx); // [stream]
        mv.visitLabel(l1);
        mv.visitInsn(Opcodes.DUP); // [stream, stream]
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(ParsingUtils.class), "skipUTF8", Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(RecordingStream.class)), false); // [stream]
        mv.visitVarInsn(Opcodes.ILOAD, arraySizeIdx); // [stream, int]
        mv.visitLdcInsn(1); // [stream, int, 1]
        mv.visitInsn(Opcodes.ISUB); // [stream, int]
        mv.visitInsn(Opcodes.DUP); // [stream, int, int]
        mv.visitVarInsn(Opcodes.ISTORE, arraySizeIdx); // [stream, int]
        mv.visitJumpInsn(Opcodes.IFNE, l1); // [stream]
        mv.visitInsn(Opcodes.POP); // []
        mv.visitJumpInsn(Opcodes.GOTO, l3);
        mv.visitLabel(l2); // [int]
        mv.visitInsn(Opcodes.POP); // []
        mv.visitLabel(l3); // []
    }

    private static void skipObjectArray(MetadataClass fldType, int arraySizeIdx, int streamIdx, int lastVarIdx, boolean keepStream, MethodVisitor mv) {
        // stack: [stream]
        Label l1 = new Label();
        Label l2 = new Label();
        Label l3 = new Label();
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(RecordingStream.class), "readVarint", Type.getMethodDescriptor(Type.LONG_TYPE), false); // [long]
        mv.visitInsn(Opcodes.L2I); // [int]
        addLogIntWithMsg(mv, "Array size: "); // [int]
        mv.visitInsn(Opcodes.DUP); // [int, int]
        mv.visitJumpInsn(Opcodes.IFEQ, l2); // [int]
        mv.visitVarInsn(Opcodes.ISTORE, arraySizeIdx); // []
        mv.visitVarInsn(Opcodes.ALOAD, streamIdx); // [stream]
        mv.visitLabel(l1);
        skipSimpleField(fldType, streamIdx, lastVarIdx, true, mv); // [stream]
        mv.visitIincInsn(arraySizeIdx, -1); // [stream]
        mv.visitVarInsn(Opcodes.ILOAD, arraySizeIdx); // [stream, int]
        mv.visitJumpInsn(Opcodes.IFNE, l1); // [stream]
        if (!keepStream) {
            mv.visitInsn(Opcodes.POP); // []
        }
        mv.visitJumpInsn(Opcodes.GOTO, l3);
        mv.visitLabel(l2); // [int]
        mv.visitInsn(Opcodes.POP); // []
        mv.visitLabel(l3); // []
    }

    private static void skipSimpleField(MetadataClass fldType, int streamIdx, int lastVarIdx, boolean keepStream, MethodVisitor mv) {
        // stack: [stream]
        String fldTypeName = fldType.getName();
        switch (fldTypeName) {
            case "byte", "boolean": {
                if (keepStream) {
                    mv.visitInsn(Opcodes.DUP); // [stream, stream]
                }
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(RecordingStream.class), "read", Type.getMethodDescriptor(Type.BYTE_TYPE), false); // [<stream>, byte]
                mv.visitInsn(Opcodes.POP); // [<stream>]
                return;
            }
            case "short":
            case "char":
            case "int":
            case "long": {
                if (keepStream) {
                    mv.visitInsn(Opcodes.DUP); // [stream, stream]
                }
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(RecordingStream.class), "readVarint", Type.getMethodDescriptor(Type.LONG_TYPE), false); // [<stream>, long]
                mv.visitInsn(Opcodes.POP2); // [<stream>]
                return;
            }
            case "float": {
                if (keepStream) {
                    mv.visitInsn(Opcodes.DUP); // [stream, stream]
                }
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(RecordingStream.class), "readFloat", Type.getMethodDescriptor(Type.FLOAT_TYPE), false); // [<stream>, float]
                mv.visitInsn(Opcodes.POP); // [<stream>]
                return;
            }
            case "double": {
                if (keepStream) {
                    mv.visitInsn(Opcodes.DUP); // [stream, stream]
                }
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(RecordingStream.class), "readDouble", Type.getMethodDescriptor(Type.DOUBLE_TYPE), false); // [<stream>, double]
                mv.visitInsn(Opcodes.POP2); // [<stream>]
                return;
            }
            case "java.lang.String": {
                if (keepStream) {
                    mv.visitInsn(Opcodes.DUP); // [stream, stream]
                }
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(ParsingUtils.class), "skipUTF8", Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(RecordingStream.class)), false); // [<stream>]
                return;
            }
        }
        for (MetadataField fld : fldType.getFields()) {
            mv.visitInsn(Opcodes.DUP); // [stream, stream]
            addFieldSkipper(mv, fld, streamIdx, lastVarIdx); // [stream]
        }
        if (!keepStream) {
            mv.visitInsn(Opcodes.POP); // []
        }
    }

    private static void handleArrayField(MetadataField fld, String className, int streamIdx, int deserializersIdx, int lastVarIdx, Long2ObjectMap<Class<?>> typeClassMap, MethodVisitor mv) {
        // stack: [this, stream]
        int arrayCounterIdx = lastVarIdx + 1;
        int arraySizeIdx = arrayCounterIdx + 1;
        lastVarIdx = arraySizeIdx;

        String fldTypeName = fld.getType().getName();
        Type fldType = null;
        Type dataType = null;
        int arrayOpcode = 0;

        switch (fldTypeName) {
            case "byte", "boolean": {
                fldType = fldTypeName.equals("byte") ? Type.BYTE_TYPE : Type.BOOLEAN_TYPE;
                arrayOpcode = fldTypeName.equals("byte") ? Opcodes.T_BYTE : Opcodes.T_BOOLEAN;
                dataType = Type.BYTE_TYPE;
                break;
            }
            case "short", "char", "int", "long": {
                dataType = Type.LONG_TYPE;
                switch (fldTypeName) {
                    case "short": {
                        fldType = Type.SHORT_TYPE;
                        arrayOpcode = Opcodes.T_SHORT;
                        break;
                    }
                    case "char": {
                        fldType = Type.CHAR_TYPE;
                        arrayOpcode = Opcodes.T_CHAR;
                        break;
                    }
                    case "int": {
                        fldType = Type.INT_TYPE;
                        arrayOpcode = Opcodes.T_INT;
                        break;
                    }
                    case "long": {
                        fldType = Type.LONG_TYPE;
                        arrayOpcode = Opcodes.T_LONG;
                        break;
                    }
                }
                break;
            }
            case "float": {
                dataType = Type.FLOAT_TYPE;
                fldType = Type.FLOAT_TYPE;
                arrayOpcode = Opcodes.T_FLOAT;
                break;
            }
            case "double": {
                dataType = Type.DOUBLE_TYPE;
                fldType = Type.DOUBLE_TYPE;
                arrayOpcode = Opcodes.T_DOUBLE;
                break;
            }
        }
        if (fldType != null) {
            readIntoPrimitiveArray(mv, className, fld.getName(), fldType, arrayOpcode, dataType, streamIdx, arraySizeIdx, arrayCounterIdx);
        } else if (fldTypeName.equals("java.lang.String")) {
            readIntoStringArray(className, fld.getName(), streamIdx, arraySizeIdx, arrayCounterIdx, mv);
        } else {
            // fall-back to the registered deserializer
            Class<?> fldClz = typeClassMap.get(fld.getType().getId());
            if (fldClz == null) {
                throw new RuntimeException("No class found for type: " + fld.getType().getName());
            }
            readIntoObjectArray(className, fld.getName(), fld.getType().getName(), Type.getType(fldClz), streamIdx, arraySizeIdx, arrayCounterIdx, deserializersIdx, lastVarIdx, mv);
        }
    }

    private static void readIntoPrimitiveArray(MethodVisitor mv, String className, String fldName, Type fldType, int arrayType, Type dataType, int streamIdx, int arraySizeIdx, int arrayCounterIdx) {
        String operation = getPrimitiveReadOperation(fldType);
        // stack: [this, stream]
        Label l1 = new Label();
        Label l2 = new Label();
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(RecordingStream.class), "readVarint", Type.getMethodDescriptor(Type.LONG_TYPE), false); // [this, long]
        mv.visitInsn(Opcodes.L2I); // [this, int]
        mv.visitInsn(Opcodes.DUP); // [this, int, int]
        mv.visitVarInsn(Opcodes.ISTORE, arraySizeIdx); // [this, int]
        mv.visitIntInsn(Opcodes.NEWARRAY, arrayType); // [this, array]
        mv.visitLdcInsn(0); // [this, array, int]
        mv.visitVarInsn(Opcodes.ISTORE, arrayCounterIdx); // [this, array]
        mv.visitLabel(l1);
        mv.visitVarInsn(Opcodes.ILOAD, arraySizeIdx); // [this, array, int]
        mv.visitVarInsn(Opcodes.ILOAD, arrayCounterIdx); // [this, array, int, int]
        mv.visitJumpInsn(Opcodes.IF_ICMPEQ, l2); // [this, array]
        mv.visitInsn(Opcodes.DUP); // [this, array, array]
        mv.visitVarInsn(Opcodes.ILOAD, arrayCounterIdx); // [this, array, array, int]
        mv.visitVarInsn(Opcodes.ALOAD, streamIdx); // [this, array, array, int, stream]
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(RecordingStream.class), operation, Type.getMethodDescriptor(dataType), false); // [this, array, array, int, value]
        if (dataType.getSort() == Type.LONG && fldType.getSort() != Type.LONG) {
            // the value is read as a long varint, need to convert to the target type (int, short, char)
            mv.visitInsn(Opcodes.L2I); // [this, array, array, int, int_value]
        }
        mv.visitInsn(fldType.getOpcode(Opcodes.IASTORE)); // [this, array]
        mv.visitIincInsn(arrayCounterIdx, 1); // [this, array]
        mv.visitJumpInsn(Opcodes.GOTO, l1); // [this, array]
        mv.visitLabel(l2);
        mv.visitFieldInsn(Opcodes.PUTFIELD, className, fldName, "[" + fldType.getDescriptor()); // []
    }

    private static void readIntoStringArray(String className, String fldName, int streamIdx, int arraySizeIdx, int arrayCounterIdx, MethodVisitor mv) {
        // stack: [this, stream]
        Label l1 = new Label();
        Label l2 = new Label();
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(RecordingStream.class), "readVarint", Type.getMethodDescriptor(Type.LONG_TYPE), false); // [this, long]
        mv.visitInsn(Opcodes.L2I); // [this, int]
        mv.visitInsn(Opcodes.DUP); // [this, int, int]
        mv.visitVarInsn(Opcodes.ISTORE, arraySizeIdx); // [this, int]
        mv.visitTypeInsn(Opcodes.ANEWARRAY, Type.getType(String.class).getInternalName()); // [this, array]
        mv.visitLdcInsn(0); // [this, array, int]
        mv.visitVarInsn(Opcodes.ISTORE, arrayCounterIdx); // [this, array]
        mv.visitLabel(l1);
        mv.visitVarInsn(Opcodes.ILOAD, arraySizeIdx); // [this, array, int]
        mv.visitVarInsn(Opcodes.ILOAD, arrayCounterIdx); // [this, array, int, int]
        mv.visitJumpInsn(Opcodes.IF_ICMPEQ, l2); // [this, array]
        mv.visitInsn(Opcodes.DUP); // [this, array, array]
        mv.visitVarInsn(Opcodes.ILOAD, arrayCounterIdx); // [this, array, array, int]
        mv.visitVarInsn(Opcodes.ALOAD, streamIdx); // [this, array, array, int, stream]
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(ParsingUtils.class), "readUTF8", Type.getMethodDescriptor(Type.getType(String.class), Type.getType(RecordingStream.class)), false); // [this, string]
        mv.visitInsn(Opcodes.AASTORE); // [this, array]
        mv.visitIincInsn(arrayCounterIdx, 1); // [this, array]
        mv.visitJumpInsn(Opcodes.GOTO, l1); // [this, array]
        mv.visitLabel(l2);
        mv.visitFieldInsn(Opcodes.PUTFIELD, className, fldName, "[" + Type.getType(String.class).getDescriptor()); // []
    }

    private static void readIntoObjectArray(String className, String fldName, String fldTypeName, Type fldType, int streamIdx, int arraySizeIdx, int arrayCounterIdx, int deserializersIdx, int lastVarIdx, MethodVisitor mv) {
        // stack: [this, stream]
        int deserializerIdx = lastVarIdx + 1;
        Label l1 = new Label();
        Label l2 = new Label();
        Label l3 = new Label();
        mv.visitFieldInsn(Opcodes.GETSTATIC, className, fldName + "_hdl", Type.getDescriptor(DeserializationHandler.class)); // [this, stream, deserializer]
        mv.visitInsn(Opcodes.DUP); // [this, stream, deserializer, deserializer]
        mv.visitJumpInsn(Opcodes.IFNONNULL, l1); // [this, stream, deserializer]
        mv.visitInsn(Opcodes.POP); // [this, stream]
        mv.visitVarInsn(Opcodes.ALOAD, deserializersIdx); // [this, stream, deserializers]
        mv.visitLdcInsn(fldTypeName); // [this, stream, deserializers, name]
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(Deserializers.class), "getDeserializer", Type.getMethodDescriptor(Type.getType(DeserializationHandler.class), Type.getType(String.class)), false); // [this, stream, deserializer]
        addLog(mv, "Got deserializers for " + fldTypeName);
        mv.visitInsn(Opcodes.DUP); // [this, stream, deserializer, deserializer]
        mv.visitFieldInsn(Opcodes.PUTSTATIC, className, fldName + "_hdl", Type.getDescriptor(DeserializationHandler.class)); // [this, stream, deserializer]
        mv.visitLabel(l1);
        mv.visitVarInsn(Opcodes.ASTORE, deserializerIdx); // [this, stream]
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(RecordingStream.class), "readVarint", Type.getMethodDescriptor(Type.LONG_TYPE), false); // [this, long]
        mv.visitInsn(Opcodes.L2I); // [this, int]
        mv.visitInsn(Opcodes.DUP); // [this, int, int]
        mv.visitVarInsn(Opcodes.ISTORE, arraySizeIdx); // [this, int]
        mv.visitTypeInsn(Opcodes.ANEWARRAY, fldType.getInternalName()); // [this, array]
        mv.visitLdcInsn(0); // [this, array, int]
        mv.visitVarInsn(Opcodes.ISTORE, arrayCounterIdx); // [this, array]
        mv.visitLabel(l2);
        mv.visitVarInsn(Opcodes.ILOAD, arraySizeIdx); // [this, array, int]
        mv.visitJumpInsn(Opcodes.IFEQ, l3); // [this, array]
        mv.visitInsn(Opcodes.DUP); // [this, array, array]
        mv.visitVarInsn(Opcodes.ILOAD, arrayCounterIdx); // [this, array, array, int]
        mv.visitVarInsn(Opcodes.ALOAD, deserializerIdx); // [this, array, array, int, deserializer]
        mv.visitVarInsn(Opcodes.ALOAD, streamIdx); // [this, array, array, int, deserializer,  stream]
        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, Type.getInternalName(DeserializationHandler.class), "handle", Type.getMethodDescriptor(Type.getType(Object.class), Type.getType(RecordingStream.class)), true); // [this, obj]
        mv.visitTypeInsn(Opcodes.CHECKCAST, fldType.getInternalName()); // [this, fldval]
        mv.visitInsn(Opcodes.AASTORE); // [this, array]
        mv.visitIincInsn(arrayCounterIdx, 1); // [this, array]
        mv.visitIincInsn(arraySizeIdx, -1); // [this, array]
        mv.visitJumpInsn(Opcodes.GOTO, l2); // [this, array]
        mv.visitLabel(l3);
        mv.visitFieldInsn(Opcodes.PUTFIELD, className, fldName, "[" + fldType.getDescriptor()); // []
    }

    private static String getPrimitiveReadOperation(Type type) {
        return switch (type.getSort()) {
            case Type.BYTE -> "read";
            case Type.BOOLEAN -> "read";
            case Type.SHORT -> "readVarint";
            case Type.CHAR -> "readVarint";
            case Type.INT -> "readVarint";
            case Type.LONG -> "readVarint";
            case Type.FLOAT -> "readFloat";
            case Type.DOUBLE -> "readDouble";
            default -> throw new RuntimeException("Unexpected type: " + type.getDescriptor());
        };
    }

    private static void handleSimpleField(MetadataField fld, String className, int deserializersIdx, Long2ObjectMap<Class<?>> typeClassMap, MethodVisitor mv) {
        // stack: [this, stream]
        String fldTypeName = fld.getType().getName();
        switch (fldTypeName) {
            case "byte", "boolean": {
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(RecordingStream.class), "read", Type.getMethodDescriptor(Type.BYTE_TYPE), false); // [this, int]
                mv.visitFieldInsn(Opcodes.PUTFIELD, className, fld.getName(), fld.getType().getName().equals("byte") ? Type.BYTE_TYPE.getDescriptor() : Type.BOOLEAN_TYPE.getDescriptor()); // []
                break;
            }
            case "short", "char", "int", "long": {
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(RecordingStream.class), "readVarint", Type.getMethodDescriptor(Type.LONG_TYPE), false); // [this, long]
                switch (fldTypeName) {
                    case "short": {
                        mv.visitInsn(Opcodes.L2I); // [this, int]
                        mv.visitFieldInsn(Opcodes.PUTFIELD, className, fld.getName(), Type.SHORT_TYPE.getDescriptor()); // []
                        break;
                    }
                    case "char": {
                        mv.visitInsn(Opcodes.L2I); // [this, int]
                        mv.visitFieldInsn(Opcodes.PUTFIELD, className, fld.getName(), Type.CHAR_TYPE.getDescriptor()); // []
                        break;
                    }
                    case "int": {
                        mv.visitInsn(Opcodes.L2I); // [this, int]
                        mv.visitFieldInsn(Opcodes.PUTFIELD, className, fld.getName(), Type.INT_TYPE.getDescriptor()); // []
                        break;
                    }
                    case "long": {
                        mv.visitFieldInsn(Opcodes.PUTFIELD, className, fld.getName(), Type.LONG_TYPE.getDescriptor()); // []
                        break;
                    }
                }
                break;
            }
            case "float": {
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(RecordingStream.class), "readFloat", Type.getMethodDescriptor(Type.FLOAT_TYPE), false); // [this, float]
                mv.visitFieldInsn(Opcodes.PUTFIELD, className, fld.getName(), Type.FLOAT_TYPE.getDescriptor()); // []
                break;
            }
            case "double": {
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(RecordingStream.class), "readDouble", Type.getMethodDescriptor(Type.DOUBLE_TYPE), false); // [this, double]
                mv.visitFieldInsn(Opcodes.PUTFIELD, className, fld.getName(), Type.DOUBLE_TYPE.getDescriptor()); // []
                break;
            }
            case "java.lang.String":
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(ParsingUtils.class), "readUTF8", Type.getMethodDescriptor(Type.getType(String.class), Type.getType(RecordingStream.class)), false); // [this, string]
                mv.visitFieldInsn(Opcodes.PUTFIELD, className, fld.getName(), Type.getType(String.class).getDescriptor()); // []
                break;
            default: {
                // fall-back to the registered deserializer
                Class<?> fldClz = typeClassMap.get(fld.getType().getId());
                if (fldClz == null) {
                    throw new RuntimeException("No class found for type: " + fld.getType().getName());
                }
                Label l1 = new Label();
                mv.visitFieldInsn(Opcodes.GETSTATIC, className, fld.getName() + "_hdl", Type.getDescriptor(DeserializationHandler.class)); // [this, stream, deserializer]
                mv.visitInsn(Opcodes.DUP); // [this, stream, deserializer, deserializer]
                mv.visitJumpInsn(Opcodes.IFNONNULL, l1); // [this, stream, deserializer]
                mv.visitInsn(Opcodes.POP); // [this, stream]
                mv.visitVarInsn(Opcodes.ALOAD, deserializersIdx); // [this, stream, deserializers]
                mv.visitLdcInsn(fld.getType().getName()); // [this, stream, deserializers, name]
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(Deserializers.class), "getDeserializer", Type.getMethodDescriptor(Type.getType(DeserializationHandler.class), Type.getType(String.class)), false); // [this, stream, deserializer]
                addLog(mv, "Got deserializers for " + fld.getType().getName());
                mv.visitInsn(Opcodes.DUP); // [this, stream, deserializer, deserializer]
                mv.visitFieldInsn(Opcodes.PUTSTATIC, className, fld.getName() + "_hdl", Type.getDescriptor(DeserializationHandler.class)); // [this, stream, deserializer]
                mv.visitLabel(l1);
                mv.visitInsn(Opcodes.SWAP); // [this, deserializer, stream]
                mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, Type.getInternalName(DeserializationHandler.class), "handle", Type.getMethodDescriptor(Type.getType(Object.class), Type.getType(RecordingStream.class)), true); // [this, obj]
                addLog(mv, "Got deserializer");
                mv.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(fldClz)); // [this, fldval]
                mv.visitFieldInsn(Opcodes.PUTFIELD, className, fld.getName(), Type.getDescriptor(fldClz)); // []
            }
        }
    }

    static void prepareConstructor(ClassVisitor cv, String clzName, MetadataClass clz, List<MetadataField> allFields, Set<MetadataField> appliedFields, Long2ObjectMap<Class<?>> typeClassMap) {
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
        mv.visitVarInsn(Opcodes.ALOAD,1); // [this, stream]
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
            if (!appliedFields.contains(fld)) {
                // skip
                addLog(mv, "Skipping field: " + fld.getName());
                mv.visitVarInsn(Opcodes.ALOAD, 1); // [stream]
                addFieldSkipper(mv, fld, 1, meteadataIdx); // []
                continue;
            }
            mv.visitVarInsn(Opcodes.ALOAD, 0); // [this]
            mv.visitVarInsn(Opcodes.ALOAD, 1); // [this, stream]
            addFieldLoader(mv, fld, clzName.replace('.', '/'),  1, deserializersIdx, meteadataIdx, typeClassMap); // []
        }
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    static void prepareSkipHandler(ClassVisitor cv, MetadataClass clz) {
        MethodVisitor mv = cv.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, "skip", Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(RecordingStream.class)), null, null);
        mv.visitCode();
        int streamIdx = 0;
        int lastVarIdx = streamIdx;

        for (MetadataField fld : clz.getFields()) {
            mv.visitVarInsn(Opcodes.ALOAD, streamIdx); // [stream]
            addFieldSkipper(mv, fld, 0, lastVarIdx); // []
        }
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }
}

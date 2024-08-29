package io.jafar.parser;

import io.jafar.parser.api.Control;
import io.jafar.parser.api.HandlerRegistration;
import io.jafar.parser.api.JafarParser;
import io.jafar.parser.api.JfrField;
import io.jafar.parser.api.JfrIgnore;
import io.jafar.parser.api.JfrType;
import io.jafar.parser.api.types.JFRHandler;
import io.jafar.parser.internal_api.CheckpointEvent;
import io.jafar.parser.internal_api.ChunkHeader;
import io.jafar.parser.internal_api.ChunkParserListener;
import io.jafar.parser.internal_api.DeserializationHandler;
import io.jafar.parser.internal_api.ParserContext;
import io.jafar.parser.internal_api.RecordingStream;
import io.jafar.parser.internal_api.StreamingChunkParser;
import io.jafar.parser.internal_api.metadata.MetadataClass;
import io.jafar.parser.internal_api.metadata.MetadataEvent;
import io.jafar.parser.internal_api.metadata.MetadataField;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
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
import java.util.stream.Collectors;

public final class JafarParserImpl implements JafarParser {
    private record Handlers(MethodHandle ctr, MethodHandle skip) {}

    private final class HandlerRegistrationImpl<T> implements HandlerRegistration<T> {
        private final WeakReference<Class<?>> clzRef;
        private final WeakReference<JafarParser> cookieRef;
        HandlerRegistrationImpl(Class<?> clz, JafarParser cookie) {
            this.clzRef = new WeakReference<>(clz);
            this.cookieRef = new WeakReference<>(cookie);
        }

        @Override
        public void destroy(JafarParser cookie) {
            if (cookie != null && cookie.equals(cookieRef.get())) {
                Class<?> clz = clzRef.get();
                if (clz != null) {
                    handlerMap.remove(clz);
                    globalDeserializerMap.clear();
                    handlerMap.keySet().forEach(JafarParserImpl.this::addDeserializer);
                    chunkHandlerMethodMap.forEach((i, map) -> {
                        map.remove(clz);
                    });
                }
            }
        }
    }
    private final StreamingChunkParser parser;
    private final Path recording;

    private final Map<Class<?>, List<JFRHandler.Impl<?>>> handlerMap = new HashMap<>();
    private final Int2ObjectMap<Long2ObjectMap<Class<?>>> chunkTypeClassMap = new Int2ObjectOpenHashMap<>();

    private final Map<String, JFRValueDeserializer<?>> globalDeserializerMap = new HashMap<>();
    private final Int2ObjectMap<Map<Class<?>, MethodHandle>> chunkHandlerMethodMap = new Int2ObjectOpenHashMap<>();

    private final ThreadLocal<Long2ObjectMap<Class<?>>> typeClassMapRef = new ThreadLocal<>();
    private final ThreadLocal<Map<Class<?>, MethodHandle>> deserializerMethodMapRef = new ThreadLocal<>();
    private final ThreadLocal<Map<MetadataClass, MethodHandle>> skipperMethodMapRef = ThreadLocal.withInitial(HashMap::new);

    private boolean closed = false;

    public JafarParserImpl(Path recording) {
        this.parser = new StreamingChunkParser();
        this.recording = recording;
    }

    @Override
    public <T> HandlerRegistration<T> handle(Class<T> clz, JFRHandler<T> handler) {
        addDeserializer(clz);
        handlerMap.computeIfAbsent(clz, k -> new ArrayList<>()).add(new JFRHandler.Impl<>(clz, handler));

        return new HandlerRegistrationImpl<>(clz, this);
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

        if (globalDeserializerMap.containsKey(typeName)) {
            return;
        }
        globalDeserializerMap.put(typeName, JFRValueDeserializer.create(clz));
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

    private Handlers getHandlerMethods(int chunk, MetadataClass mdClass, Class<?> clz, Long2ObjectMap<Class<?>> typeClassMap) {
        Map<Class<?>, MethodHandle> deserializerMethodMap = deserializerMethodMapRef.get();
        Map<MetadataClass, MethodHandle> skipperMethodMap = skipperMethodMapRef.get();

        Map<String, String> fieldToMethodMap = new HashMap<>();
        MethodHandle deserializer = clz != null ? deserializerMethodMap.get(clz) : null;
        MethodHandle skipper = skipperMethodMap.get(mdClass);

        if (deserializer != null || skipper != null) {
            return new Handlers(deserializer, skipper);
        }
        MethodHandle ctrHandle;
        MethodHandle skipHandle;
        try {
            if (clz != null && !clz.isInterface()) {
                throw new RuntimeException("Unsupported type: " + clz.getName());
            }
            String origClzName = clz != null ? clz.getName() : mdClass.getName();
            String origSimpleName = clz != null ? clz.getSimpleName() : mdClass.getSimpleName();
            String clzName = JafarParserImpl.class.getPackage().getName() + "." + (clz != null ? clz.getSimpleName() : mdClass.getSimpleName()) + "$" + chunk;
            // generate handler class
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, clzName.replace('.', '/'), null, "java/lang/Object", clz != null ? new String[]{origClzName.replace('.', '/')} : null);
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
                if (clz != null) {
                    for (MetadataField field : current.getFields()) {
                        String fieldName = field.getName();
                        allFields.add(field);
                        if (usedAttributes.contains(fieldName)) {
                            appliedFields.add(field);
                        }

                        Class<?> fldClz = typeClassMap.get(field.getType().getId());
                        boolean withConstantPool = field.hasConstantPool();
                        String methodName = fieldToMethodMap.get(fieldName);
                        if (methodName == null) {
                            methodName = fieldName;
                        }
                        if (withConstantPool) {
                            CodeGenerator.handleFieldRef(cw, clzName, field, fldClz, fieldName + "_ref", methodName);
                        } else {
                            CodeGenerator.handleField(cw, clzName, field, fldClz, fieldName, methodName);
                        }
                    }

                    CodeGenerator.prepareConstructor(cw, clzName, current, allFields, appliedFields, typeClassMap);
                }
                CodeGenerator.prepareSkipHandler(cw, current);;
            }
            cw.visitEnd();
            byte[] classData = cw.toByteArray();

            Files.write(Paths.get("/tmp/"+ origSimpleName + ".class"), classData);

            MethodHandles.Lookup lkp = MethodHandles.lookup().defineHiddenClass(classData, true, MethodHandles.Lookup.ClassOption.NESTMATE);
            ctrHandle = clz != null ? lkp.findConstructor(lkp.lookupClass(), MethodType.methodType(void.class, RecordingStream.class)) : null;
            skipHandle = lkp.findStatic(lkp.lookupClass(), "skip", MethodType.methodType(void.class, RecordingStream.class));
            deserializerMethodMap.put(clz, ctrHandle);
            skipperMethodMap.put(mdClass, skipHandle);
            return new Handlers(ctrHandle, skipHandle);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to resolve handler method for " + mdClass.getName(), t);
        }
    }

    @Override
    public void run() throws IOException {
        if (closed) {
            throw new IOException("Parser is closed");
        }
        // parse JFR and run handlers
        parser.parse(openJfrStream(recording), new ChunkParserListener() {
            private final Control ctl = new Control();

            @Override
            public void onRecordingStart(ParserContext context) {
                if (!globalDeserializerMap.isEmpty()) {
                    context.setTypeFilter(t -> globalDeserializerMap.containsKey(t.getName()));
                }
            }

            @Override
            public boolean onChunkStart(int chunkIndex, ChunkHeader header, ParserContext context) {
                if (!globalDeserializerMap.isEmpty()) {
                    synchronized (this) {
                        typeClassMapRef.set(chunkTypeClassMap.computeIfAbsent(chunkIndex, k -> new Long2ObjectOpenHashMap<>()));
                        deserializerMethodMapRef.set(chunkHandlerMethodMap.computeIfAbsent(chunkIndex, k -> new HashMap<>()));

                        Deserializers target = context.getDeserializers();
                        for (Map.Entry<String, JFRValueDeserializer<?>> e : globalDeserializerMap.entrySet()) {
                            target.putIfAbsent(e.getKey(), e.getValue().duplicate());
                        }
                    }
                    return true;
                }
                return false;
            }

            @Override
            public boolean onChunkEnd(int chunkIndex, boolean skipped) {
                typeClassMapRef.remove();
                deserializerMethodMapRef.remove();
                return true;
            }

            @Override
            public boolean onMetadata(MetadataEvent metadata) {
                Long2ObjectMap<Class<?>> typeClassMap = typeClassMapRef.get();

                Deserializers deserializers = metadata.getContext().getDeserializers();
                // typeClassMap must be fully intialized before trying to resolve/generate the handlers
                for (MetadataClass clz : metadata.getClasses()) {
                    JFRValueDeserializer<?> deserializer = (JFRValueDeserializer<?>) deserializers.getDeserializer(clz.getName());
                    if (deserializer != null) {
                        typeClassMap.putIfAbsent(clz.getId(), deserializer.getClazz());
                    }
                }
                for (MetadataClass clz : metadata.getClasses()) {
                    if (clz.isPrimitive() || (clz.getSuperType() != null && clz.getSuperType().contains("Annotation"))) {
                        continue;
                    }
                    JFRValueDeserializer<?> deserializer = (JFRValueDeserializer<?>) deserializers.getDeserializer(clz.getName());
                    // TODO: Perhaps put deserializer/skipper to MetadataClass ?
                    Handlers handlers = getHandlerMethods(metadata.getContext().getChunkIndex(), clz, deserializer != null ? deserializer.getClazz() : null, typeClassMap);
                    if (deserializer != null) {
                        deserializer.setHandler(handlers.ctr());
                    }
                    clz.setSkipHandler(handlers.skip());
//                    System.out.println("===> Resolved handlers for " + clz.getName() + "(" + clz + ") :: " + handlers);
                }

                return true;
            }

            @Override
            public boolean onCheckpoint(CheckpointEvent checkpoint) {
                return ChunkParserListener.super.onCheckpoint(checkpoint);
            }

            @Override
            public boolean onEvent(long typeId, RecordingStream stream, long payloadSize) {
                Deserializers deserializers = stream.getContext().getDeserializers();
                Long2ObjectMap<Class<?>> typeClassMap = typeClassMapRef.get();
                Class<?> typeClz = typeClassMap.get(typeId);
                if (typeClz == null) {
                    return true;
                }
                if (handlerMap.containsKey(typeClz)) {
                    String typeName = typeClz.getAnnotation(JfrType.class).value();
                    DeserializationHandler<?> deserializer = deserializers.getDeserializer(typeName);
                    if (deserializer != null) {
                        Object deserialized = deserializer.handle(stream);
                        for (JFRHandler.Impl<?> handler : handlerMap.get(typeClz)) {
                            handler.handle(deserialized, null);
                        }
                    }
                }

                return true;
            };
        });
    }

    @Override
    public void close() throws Exception {
        if (!closed) {
            closed = true;

            parser.close();
            chunkTypeClassMap.clear();
            chunkHandlerMethodMap.clear();
            handlerMap.clear();
            globalDeserializerMap.clear();
        }
    }

    private static ByteBuffer openJfrStream(Path jfrFile) {
        try (RandomAccessFile raf = new RandomAccessFile(jfrFile.toFile(), "r");
             FileChannel channel = raf.getChannel()) {
            MappedByteBuffer buf = channel.map(FileChannel.MapMode.READ_ONLY, 0, raf.length());
            buf.order(ByteOrder.BIG_ENDIAN);
            buf.load();
            return buf;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

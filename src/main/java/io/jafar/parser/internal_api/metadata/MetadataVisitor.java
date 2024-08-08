package io.jafar.parser.internal_api.metadata;

public interface MetadataVisitor {
    default void visitRoot(MetadataRoot root) {};
    default void visitEnd(MetadataRoot root) {};
    default void visitMetadata(MetadataElement metadata) {};
    default void visitEnd(MetadataElement metadata) {};
    default void visitClass(MetadataClass clz) {};
    default void visitEnd(MetadataClass clz) {};
    default void visitSetting(MetadataSetting setting) {};
    default void visitEnd(MetadataSetting setting) {};
    default void visitAnnotation(MetadataAnnotation annotation) {};
    default void visitEnd(MetadataAnnotation annotation) {};
    default void visitField(MetadataField field) {}
    default void visitEnd(MetadataField field) {}
    default void visitRegion(MetadataRegion region) {}
    default void visitEnd(MetadataRegion region) {}
}

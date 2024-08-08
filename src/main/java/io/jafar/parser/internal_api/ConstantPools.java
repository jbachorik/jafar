package io.jafar.parser.internal_api;

import java.util.stream.Stream;

public interface ConstantPools {
    ConstantPool getConstantPool(long typeId);
    boolean hasConstantPool(long typeId);

    Stream<? extends ConstantPool> pools();
}

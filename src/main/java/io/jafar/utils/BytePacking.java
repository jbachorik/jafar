package io.jafar.utils;

import java.nio.ByteOrder;

public class BytePacking {
    public static int pack(ByteOrder order, char a, char b, char c, char d) {
        assert ((a | b | c | d) & 0xFF00) == 0 : "not ASCII";
        int packed = (d << 24) | (c << 16) | (b << 8) | a;
        return order == ByteOrder.BIG_ENDIAN ? Integer.reverseBytes(packed) : packed;
    }
}

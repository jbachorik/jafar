package io.jafar.parser.internal_api;

import io.jafar.parser.ParsingUtils;

import java.io.IOException;

public final class TypeSkipper {
    public static final class Instructions {
        public static final int ARRAY = 1;
        public static final int BYTE = 2;
        public static final int FLOAT = 3;
        public static final int DOUBLE = 4;
        public static final int STRING = 5;
        public static final int VARINT = 6;
        public static final int CP_ENTRY = 7;
    }

    private final int[] instructions;

    public TypeSkipper(int[] instructions) {
        this.instructions = instructions;
    }

    public void skip(RecordingStream stream) throws IOException {
        for (int i = 0; i < instructions.length; i++) {
            int instruction = instructions[i];
            if (instruction == Instructions.ARRAY) {
                int endIndex = (++i) + instructions[i++]; // next instruction for array is encoding the number of instructions per array item
                int cnt = (int)stream.readVarint();
                if (cnt == 0) {
                    i = endIndex;
                    continue;
                }
                int savedIndex = i;
                for (int j = 0; j < cnt; ) {
                    skip(instructions[i], stream);
                    if (endIndex == i++) {
                        i = savedIndex;
                        j++;
                    }
                }
                i = endIndex;
                continue;
            }
            skip(instruction, stream);
        }
    }

    private static void skip(int instruction, RecordingStream stream) throws IOException {
        switch (instruction) {
            case Instructions.VARINT:
            case Instructions.CP_ENTRY: stream.readVarint(); break;
            case Instructions.BYTE: stream.skip(1); break;
            case Instructions.FLOAT: stream.skip(4); break;
            case Instructions.DOUBLE: stream.skip(8); break;
            case Instructions.STRING: ParsingUtils.skipUTF8(stream); break;
        }
    }
}

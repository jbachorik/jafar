package io.jafar.parser.internal_api;

import io.jafar.parser.ParsingUtils;

import java.io.IOException;

public final class TypeSkipper {
    public enum Instruction {
        ARRAY, ARRAY_END, BYTE, VARINT, FLOAT, DOUBLE, STRING, CP_ENTRY;
    }

    private final Instruction[] instructions;

    public TypeSkipper(Instruction[] instructions) {
        this.instructions = instructions;
    }

    public void skip(RecordingStream stream) throws IOException {
        for (int i = 0; i < instructions.length; i++) {
            Instruction instruction = instructions[i];
            if (instruction == Instruction.ARRAY) {
                int cnt = (int)stream.readVarint();
                int savedIndex = ++i;
                int lastIndex = -1;
                for (int j = 0; j < cnt; j++) {
                    while ((instruction = instructions[i]) != Instruction.ARRAY_END) {
                        skip(instruction, stream);
                        i++;
                    }
                    lastIndex = i;
                    i = savedIndex;
                }
                i = lastIndex;
                continue;
            }
            skip(instruction, stream);
        }
    }

    private static void skip(Instruction instruction, RecordingStream stream) throws IOException {
        switch (instruction) {
            case VARINT:
            case CP_ENTRY: stream.readVarint(); break;
            case BYTE: stream.skip(1); break;
            case FLOAT: stream.skip(4); break;
            case DOUBLE: stream.skip(8); break;
            case STRING: ParsingUtils.skipUTF8(stream); break;
        }
    }
}

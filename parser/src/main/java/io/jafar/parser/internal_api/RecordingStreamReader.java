package io.jafar.parser.internal_api;

import io.jafar.utils.CustomByteBuffer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public abstract class RecordingStreamReader {
    public static final class MappedRecordingStreamReader extends RecordingStreamReader {
        private final CustomByteBuffer buffer;
        private final long length;
        private final boolean nativeOrder;
        private final int alignementOffset;

        private long remaining;

        public MappedRecordingStreamReader(Path path) throws IOException {
            this(CustomByteBuffer.map(path, Integer.MAX_VALUE), Files.size(path), 0);
        }

        private MappedRecordingStreamReader(CustomByteBuffer buffer, long length, int alignementOffset) {
            this.buffer = buffer;
            this.length = length;
            this.nativeOrder = buffer.isNativeOrder();
            this.alignementOffset = alignementOffset;
            this.remaining = length;
        }

        @Override
        public RecordingStreamReader slice() {
            long sliceLength = buffer.remaining();
            return new MappedRecordingStreamReader(buffer.slice(), sliceLength, (int)(alignementOffset + buffer.position()) % 8);
        }

        @Override
        public RecordingStreamReader slice(long pos, long size) {
            return new MappedRecordingStreamReader(buffer.slice(pos, size), size, (int)(alignementOffset + pos) % 8);
        }

        @Override
        public long length() {
            return length;
        }

        @Override
        public long remaining() {
            return remaining;
        }

        @Override
        public long position() {
            return buffer.position();
        }

        @Override
        public void position(long newPosition) {
            remaining = length - newPosition;
            buffer.position(newPosition);
        }

        @Override
        public void skip(long n) {
            remaining -= n;
            buffer.position(buffer.position() + n);
        }

        @Override
        public byte read() {
            remaining--;
            return buffer.get();
        }

        @Override
        public void read(byte[] b, int off, int len) {
            remaining -= len;
            buffer.get(b, off, len);
        }

        @Override
        public boolean readBoolean() {
            remaining--;
            return buffer.get() != 0;
        }

        @Override
        public short readShort() {
            remaining -= 2;
            short s = buffer.getShort();
            return nativeOrder ? s : Short.reverseBytes(s);
        }

        @Override
        public int readInt() {
            remaining -= 4;
            int i = buffer.getInt();
            return nativeOrder ? i : Integer.reverseBytes(i);
        }

        @Override
        public long readLong() {
            remaining -= 8;
            long l = buffer.getLong();
            return nativeOrder ? l : Long.reverseBytes(l);
        }

        private static float reverseBytes(float f) {
            int i = Float.floatToRawIntBits(f);
            return Float.intBitsToFloat(Integer.reverseBytes(i));
        }

        private static double reverseBytes(double d) {
            long l = Double.doubleToRawLongBits(d);
            return Double.longBitsToDouble(Long.reverseBytes(l));
        }

        @Override
        public float readFloat() {
            remaining -= 4;
            float f = buffer.getFloat();
            return nativeOrder ? f : reverseBytes(f);
        }

        @Override
        public double readDouble() {
            remaining -= 8;
            double d = buffer.getDouble();
            return nativeOrder ? d : reverseBytes(d);
        }

        private static int findFirstUnset8thBit(long value) {
            // Step 1: Mask out the 8th bits of each byte
            long mask = 0x8080808080808080L;
            long eighthBits = value & mask;

            // Step 2: Identify which bytes have the 8th bit unset
            long unsetBits = (~eighthBits) & mask;

            // Step 3: Collapse each byte to a single bit
            long collapsed = unsetBits * 0x0101010101010101L;

            // Step 4: Find the first unset byte
            return Long.numberOfTrailingZeros(collapsed) / 8;
        }

        private static final boolean VARINT_FROM_LONG = Boolean.getBoolean("io.jafar.parser.varint_from_long");

        @Override
        public long readVarint() {
            if (VARINT_FROM_LONG) {
                // TODO: Experimental - tries optimizing varint decoding by loading 8 bytes at once
                // So far it looks this is actually slowing down the decoding, but I will leave the code
                // here so it can be revisited later
                // The guard flag is false, unless a system property is provided so the condition will
                // be elided
                long pos = checkVarintFromLongPos();
                if (pos > -1) {
                    return readVarintFromLong(pos);
                }
            }
            return readVarintSeq();
        }

        private long checkVarintFromLongPos() {
            long pos = buffer.position();
            if (((pos + alignementOffset) & 7) == 0) {
                if (remaining >= 8) {
                    return pos;
                }
            }
            return -1;
        }

        private long readVarintFromLong(long pos) {
            long value = buffer.getLong();

            int parts = findFirstUnset8thBit(value) + 1;
            long l = value;
            if (parts < 8) {
                long mask = (0XFFFFFFFFFFFFFFFFL >>> (8 - parts) * 8);
                l = l & mask;
            }

            long extracted = l & 0x7F7F7F7F7F7F7F7FL;  // Extract lower 7 bits
            long result =
                    ((extracted & 0x000000000000007FL)) |
                            ((extracted & 0x0000000000007F00L) >> 1) |
                            ((extracted & 0x00000000007F0000L) >> 2) |
                            ((extracted & 0x000000007F000000L) >> 3) |
                            ((extracted & 0x0000007F00000000L) >> 4) |
                            ((extracted & 0x00007F0000000000L) >> 5) |
                            ((extracted & 0x007F000000000000L) >> 6) |
                            ((extracted & 0x7F00000000000000L) >> 7);

            if (parts == 9) {
                byte b = buffer.get();
                result |= (b & 0x7FL) << 56;
            } else {
                position(pos + parts);
            }
            remaining -= parts;
            return result;
        }

        private long readVarintSeq() {
            byte b0 = buffer.get();
            remaining--;
            long ret = (b0 & 0x7FL);
            if (b0 >= 0) {
                return ret;
            }
            int b1 = buffer.get();
            remaining--;
            ret += (b1 & 0x7FL) << 7;
            if (b1 >= 0) {
                return ret;
            }
            int b2 = buffer.get();
            remaining--;
            ret += (b2 & 0x7FL) << 14;
            if (b2 >= 0) {
                return ret;
            }
            int b3 = buffer.get();
            remaining--;
            ret += (b3 & 0x7FL) << 21;
            if (b3 >= 0) {
                return ret;
            }
            int b4 = buffer.get();
            remaining--;
            ret += (b4 & 0x7FL) << 28;
            if (b4 >= 0) {
                return ret;
            }
            int b5 = buffer.get();
            remaining--;
            ret += (b5 & 0x7FL) << 35;
            if (b5 >= 0) {
                return ret;
            }
            int b6 = buffer.get();
            remaining--;
            ret += (b6 & 0x7FL) << 42;
            if (b6 >= 0) {
                return ret;
            }
            int b7 = buffer.get();
            remaining--;
            ret += (b7 & 0x7FL) << 49;
            if (b7 >= 0) {
                return ret;
            }
            int b8 = buffer.get();// read last byte raw
            remaining--;
            return ret + (((long) (b8 & 0XFF)) << 56);
        }

        @Override
        public void close() throws IOException {

        }
    }

    public abstract RecordingStreamReader slice();
    public abstract RecordingStreamReader slice(long pos, long size);
    public abstract long length();
    public abstract long remaining();
    public abstract long position();
    public abstract void position(long newPosition);
    public abstract void skip(long n);
    public abstract byte read();
    public abstract void read(byte[] b, int off, int len);
    public abstract boolean readBoolean();
    public abstract short readShort();
    public abstract int readInt();
    public abstract long readLong();
    public abstract float readFloat();
    public abstract double readDouble();
    public abstract long readVarint();
    public abstract void close() throws IOException;

    public static RecordingStreamReader mapped(Path path) throws IOException {
        return new MappedRecordingStreamReader(path);
    }
}

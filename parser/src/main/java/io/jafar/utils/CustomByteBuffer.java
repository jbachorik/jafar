package io.jafar.utils;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;

public interface CustomByteBuffer {
    static CustomByteBuffer map(Path channel) throws IOException {
        return map(channel, Integer.MAX_VALUE);
    }

    static CustomByteBuffer map(Path path, int spliceSize) throws IOException  {
        long size = Files.size(path);
        if (size > spliceSize) {
            return new SplicedMappedByteBuffer(path, spliceSize);
        } else {
            try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r"); FileChannel channel = raf.getChannel()) {
                return new ByteBufferWrapper(channel.map(FileChannel.MapMode.READ_ONLY, 0, size));
            }
        }
    }

    CustomByteBuffer slice();
    CustomByteBuffer slice(long pos, long len);

    CustomByteBuffer order(ByteOrder bigEndian);
    ByteOrder order();

    boolean isNativeOrder();

    void position(long position);

    long position();

    long remaining();

    void get(byte[] buffer, int offset, int length);

    byte get();

    short getShort();

    int getInt();

    float getFloat();

    double getDouble();

    void mark();

    void reset();

    long getLong();

    class ByteBufferWrapper implements CustomByteBuffer {
        private final MappedByteBuffer delegate;
        private final boolean nativeOrder;

        public ByteBufferWrapper(MappedByteBuffer delegate) {
            this.delegate = delegate;
            this.nativeOrder = delegate.order() == ByteOrder.nativeOrder();
            delegate.order(ByteOrder.nativeOrder());
        }

        @Override
        public boolean isNativeOrder() {
            return nativeOrder;
        }

        @Override
        public CustomByteBuffer slice(long pos, long len) {
            return new ByteBufferWrapper(delegate.slice((int)pos, (int)len));
        }

        @Override
        public CustomByteBuffer slice() {
            return new ByteBufferWrapper(delegate.slice());
        }

        @Override
        public CustomByteBuffer order(ByteOrder order) {
            delegate.order(order);
            return this;
        }

        @Override
        public ByteOrder order() {
            return delegate.order();
        }

        @Override
        public void position(long position) {
            delegate.position((int)position);
//            this.position = (int) position;
        }

        @Override
        public long position() {
            return delegate.position(); //position;
        }

        @Override
        public long remaining() {
            return delegate.remaining(); //length - position;
        }

        @Override
        public void get(byte[] buffer, int offset, int length) {
            delegate.get(buffer, offset, length);
//            delegate.get(position, buffer, offset, length);
//            position += length;
        }

        @Override
        public byte get() {
            return delegate.get();
//            return delegate.get(position++);
        }

        @Override
        public short getShort() {
            return delegate.getShort();
//            short s = delegate.getShort(position);
//            position += 2;
//            return s;
        }

        @Override
        public int getInt() {
            return delegate.getInt();
//            int i = delegate.getInt(position);
//            position += 4;
//            return i;
        }

        @Override
        public float getFloat() {
            return delegate.getFloat();
//            float f = delegate.getFloat(position);
//            position += 4;
//            return f;
        }

        @Override
        public double getDouble() {
            return delegate.getDouble();
//            double d = delegate.getDouble(position);
//            position += 8;
//            return d;
        }

        @Override
        public long getLong() {
            return delegate.getLong();
//            long l = delegate.getLong(position);
//            position += 8;
//            return l;
        }

        @Override
        public void mark() {
            delegate.mark();
//            mark = position;
        }

        @Override
        public void reset() {
            delegate.reset();
//            if (mark > -1) {
//                position = mark;
//            }
        }
    }
}

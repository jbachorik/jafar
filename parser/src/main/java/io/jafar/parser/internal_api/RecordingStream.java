package io.jafar.parser.internal_api;

import java.io.EOFException;
import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class RecordingStream implements AutoCloseable {
  private final ByteBuffer delegate;

  private final ParserContext context;

  RecordingStream(ByteBuffer buffer) {
    this(buffer, new ParserContext());
  }

  public RecordingStream slice(int pos, int len, ParserContext context) {
    return new RecordingStream(delegate.slice(pos, len), context);
  }

  private RecordingStream(ByteBuffer buffer, ParserContext context) {
    if (buffer.order() == ByteOrder.LITTLE_ENDIAN) {
      this.delegate = buffer.slice().order(ByteOrder.BIG_ENDIAN);
    } else {
      this.delegate = buffer;
    }
    this.context = context;
  }

  public ParserContext getContext() {
    return context;
  }

  public void position(int position) {
    delegate.position(position);
  }

  public int position() {
    return delegate.position();
  }

  public void read(byte[] buffer, int offset, int length) throws IOException {
    try {
      if (delegate.remaining() < length) {
        throw new EOFException("unexpected EOF");
      }
      delegate.get(buffer, offset, length);
    } catch (BufferUnderflowException | BufferOverflowException e) {
      throw new IOException(e);
    }
  }

  public byte read() throws IOException {
    try {
      return delegate.get();
    } catch (BufferUnderflowException | BufferOverflowException e) {
      throw new IOException(e);
    }
  }

  public short readShort() throws IOException {
    try {
      return delegate.getShort();
    } catch (BufferUnderflowException | BufferOverflowException e) {
      throw new IOException(e);
    }
  }

  public int readInt() throws IOException {
    try {
      return delegate.getInt();
    } catch (BufferUnderflowException | BufferOverflowException e) {
      throw new IOException(e);
    }
  }

  public long readLong() throws IOException {
    try {
      return delegate.getLong();
    } catch (BufferUnderflowException | BufferOverflowException e) {
      throw new IOException(e);
    }
  }

  public float readFloat() throws IOException {
    try {
      return delegate.getFloat();
    } catch (BufferUnderflowException | BufferOverflowException e) {
      throw new IOException(e);
    }
  }

  public double readDouble() throws IOException {
    try {
      return delegate.getDouble();
    } catch (BufferUnderflowException | BufferOverflowException e) {
      throw new IOException(e);
    }
  }

  private long readFullLong() {
    int offset = 8 - delegate.remaining();
    if (offset > 0) {
      position(delegate.position() - offset);
      return (delegate.getLong() & (0xffffffffffffffffL << offset * 8)) << offset * 8;
    } else {
      return delegate.getLong();
    }
  }

  public long readVarint() throws IOException {
    byte b0 = delegate.get();
    long ret = (b0 & 0x7FL);
    if (b0 >= 0) {
      return ret;
    }
    int b1 = delegate.get();
    ret += (b1 & 0x7FL) << 7;
    if (b1 >= 0) {
      return ret;
    }
    int b2 = delegate.get();
    ret += (b2 & 0x7FL) << 14;
    if (b2 >= 0) {
      return ret;
    }
    int b3 = delegate.get();
    ret += (b3 & 0x7FL) << 21;
    if (b3 >= 0) {
      return ret;
    }
    int b4 = delegate.get();
    ret += (b4 & 0x7FL) << 28;
    if (b4 >= 0) {
      return ret;
    }
    int b5 = delegate.get();
    ret += (b5 & 0x7FL) << 35;
    if (b5 >= 0) {
      return ret;
    }
    int b6 = delegate.get();
    ret += (b6 & 0x7FL) << 42;
    if (b6 >= 0) {
      return ret;
    }
    int b7 = delegate.get();
    ret += (b7 & 0x7FL) << 49;
    if (b7 >= 0) {
      return ret;
    }
    int b8 = delegate.get();// read last byte raw
    return ret + (((long) (b8 & 0XFF)) << 56);
  }

  public boolean readBoolean() throws IOException {
    return read() != 0;
  }

  public int available() {
    return delegate.remaining();
  }

  public void skip(int bytes) throws IOException {
    try {
      delegate.position(delegate.position() + bytes);
    } catch (BufferUnderflowException | BufferOverflowException e) {
      throw new IOException(e);
    }
  }

  public void mark() {
    delegate.mark();
  }

  public void reset() {
    delegate.reset();
  }

  @Override
  public void close() {
  }
}

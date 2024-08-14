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
    try {
      int result = 0;
      for (int shift = 0; ; shift += 7) {
        byte b = delegate.get();
        result |= (b & 0x7f) << shift;
        if (b >= 0) {
          return result;
        }
      }
    } catch (BufferUnderflowException | BufferOverflowException e) {
      throw new IOException(e);
    }
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

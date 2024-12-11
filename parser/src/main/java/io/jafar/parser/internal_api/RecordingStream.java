package io.jafar.parser.internal_api;

import java.io.EOFException;
import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.file.Path;

public final class RecordingStream implements AutoCloseable {
  private final RecordingStreamReader reader;

  private final ParserContext context;
  private long mark = -1;


  RecordingStream(Path path) throws IOException {
    this(RecordingStreamReader.mapped(path), new ParserContext());
  }

  public RecordingStream slice(long pos, long len, ParserContext context) throws IOException {
    return new RecordingStream(reader.slice(pos, len), context);
  }

  public RecordingStream(RecordingStreamReader reader, ParserContext context) throws IOException {
    this.reader = reader;
    this.context = context;
  }

  public ParserContext getContext() {
    return context;
  }

  public void position(long position) throws IOException {
    reader.position(position);
  }

  public long position() throws IOException {
    return reader.position();
  }

  public void read(byte[] buffer, int offset, int length) throws IOException {
    try {
      if (available() < length) {
        throw new EOFException("unexpected EOF");
      }
      reader.read(buffer, offset, length);

    } catch (BufferUnderflowException | BufferOverflowException e) {
      throw new IOException(e);
    }
  }

  public byte read() throws IOException {
    try {
      return reader.read();
    } catch (BufferUnderflowException | BufferOverflowException e) {
      throw new IOException(e);
    }
  }

  public short readShort() throws IOException {
    try {
      return reader.readShort();
    } catch (BufferUnderflowException | BufferOverflowException e) {
      throw new IOException(e);
    }
  }

  public int readInt() throws IOException {
    try {
      return reader.readInt();
    } catch (BufferUnderflowException | BufferOverflowException e) {
      throw new IOException(e);
    }
  }

  public long readLong() throws IOException {
    try {
      return reader.readLong();
    } catch (BufferUnderflowException | BufferOverflowException e) {
      throw new IOException(e);
    }
  }

  public float readFloat() throws IOException {
    try {
      return reader.readFloat();
    } catch (BufferUnderflowException | BufferOverflowException e) {
      throw new IOException(e);
    }
  }

  public double readDouble() throws IOException {
    try {
      return reader.readDouble();
    } catch (BufferUnderflowException | BufferOverflowException e) {
      throw new IOException(e);
    }
  }

  public long readVarint() throws IOException {
    return reader.readVarint();
  }
  
  public boolean readBoolean() throws IOException {
    return reader.readBoolean();
  }

  public long available() throws IOException {
    return reader.remaining();
  }

  public void skip(int bytes) throws IOException {
    try {
      reader.skip(bytes);
    } catch (BufferUnderflowException | BufferOverflowException e) {
      throw new IOException(e);
    }
  }

  public void mark() throws IOException {
    mark = reader.position();
  }

  public void reset() throws IOException {
    if (mark > -1) {
      position(mark);
      mark = -1;
    }
  }

  @Override
  public void close() {
    try {
      reader.close();
    } catch (IOException ignored) {}
  }
}

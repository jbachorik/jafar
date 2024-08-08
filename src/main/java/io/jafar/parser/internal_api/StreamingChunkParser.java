package io.jafar.parser.internal_api;

import io.jafar.parser.internal_api.metadata.MetadataEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Streaming, almost zero-allocation, JFR chunk parser implementation. <br>
 * This is an MVP of a chunk parser allowing to stream the JFR events efficiently. The parser
 * notifies its listeners as the data becomes available. Because of this it is possible for the
 * metadata events to come 'out-of-band' (although not very probable) and it is up to the caller to
 * deal with that eventuality. <br>
 */
public final class StreamingChunkParser {
  private static final Logger log = LoggerFactory.getLogger(StreamingChunkParser.class);

  /**
   * Parse the given JFR recording stream.<br>
   * The parser will process the recording stream and call the provided listener in this order:
   *
   * <ol>
   *   <li>listener.onRecordingStart()</li>
   *   <li>listener.onChunkStart()</li>
   *   <li>listener.onMetadata()</li>
   *   <li>listener.onCheckpoint()</li>
   *   <li>listener.onEvent()</li>
   *   <li>listener.onChunkEnd()</li>
   *   <li>listener.onRecordingEnd()</li>
   * </ol>
   *
   * @param buffer the JFR recording buffer. If this buffer holds a filehandle,
   *               the caller is responsible for freeing it.
   * @param listener the parser listener
   * @throws IOException
   */
  public void parse(ByteBuffer buffer, ChunkParserListener listener) throws IOException {
    try (RecordingStream stream = new RecordingStream(buffer)) {
      parse(stream, listener, false);
    }
  }

  public void parse(ByteBuffer buffer, ChunkParserListener listener, boolean forceConstantPools) throws IOException {
    try (RecordingStream stream = new RecordingStream(buffer)) {
      parse(stream, listener, forceConstantPools);
    }
  }

  private void parse(RecordingStream stream, ChunkParserListener listener, boolean forceConstantPools) throws IOException {
    if (stream.available() == 0) {
      return;
    }
    try {
      listener.onRecordingStart(stream.getContext());
      int chunkCounter = 1;
      outer:
      while (stream.available() > 0) {
        ChunkHeader header = new ChunkHeader(stream, chunkCounter);
        if (!listener.onChunkStart(chunkCounter, header, stream.getContext())) {
          log.debug(
                  "'onChunkStart' returned false. Skipping metadata and events for chunk {}",
                  chunkCounter);
          stream.skip(header.size - (stream.position() - header.offset));
          listener.onChunkEnd(chunkCounter, true);
          chunkCounter++;
          continue;
        }
        int remainder = (stream.position() - header.offset);
        RecordingStream chunkStream = stream.slice(header.offset, header.size, true);
        stream.position(header.offset + header.size);
        // read metadata
        if (!readMetadata(chunkStream, header, listener, forceConstantPools)) {
          log.debug(
                  "'onMetadata' returned false. Skipping events for chunk {}", chunkCounter);
          listener.onChunkEnd(chunkCounter, true);
          chunkCounter++;
          continue;
        }
        if (!readConstantPool(chunkStream, header, listener)) {
          log.debug(
                  "'onCheckpoint' returned false. Skipping the rest of the chunk {}",chunkCounter);
          listener.onChunkEnd(chunkCounter, true);
          chunkCounter++;
          continue;
        }
        chunkStream.position(remainder);
        while (chunkStream.position() < header.size) {
          int eventStartPos = chunkStream.position();
          chunkStream.mark(); // max 2 varints ahead
          int eventSize = (int) chunkStream.readVarint();
          if (eventSize > 0) {
            long eventType = chunkStream.readVarint();
            if (eventType > 1) { // skip metadata and checkpoint events
              long currentPos = chunkStream.position();
              if (!listener.onEvent(eventType, chunkStream, eventSize - (currentPos - eventStartPos))) {
                log.debug(
                        "'onEvent({}, stream, {})' returned false. Skipping the rest of the chunk {}",
                        eventType,
                        eventSize - (currentPos - eventStartPos),
                        chunkCounter);
                listener.onChunkEnd(chunkCounter, true);
                chunkCounter++;
                continue outer;
              }
            }
            // always skip any unconsumed event data to get the stream into consistent state
            chunkStream.position(eventStartPos + eventSize);
          }
        }
        if (!listener.onChunkEnd(chunkCounter, false)) {
          return;
        }
        chunkCounter++;
      }
    } catch(EOFException e) {
      throw new IOException("Invalid buffer", e);
    } catch (Throwable t) {
      t.printStackTrace();
      throw t;
    } finally {
      listener.onRecordingEnd();
    }
  }

  private boolean readMetadata(RecordingStream stream, ChunkHeader header, ChunkParserListener listener, boolean forceConstantPools) throws IOException {
    stream.mark();
    stream.position(header.metaOffset);
    MetadataEvent m = new MetadataEvent(stream, forceConstantPools);
    if (!listener.onMetadata(m)) {
      return false;
    }
    stream.reset();
    return true;
  }

  private boolean readConstantPool(RecordingStream stream, ChunkHeader header, ChunkParserListener listener) throws IOException {
    stream.mark();
    if (readConstantPool(stream, header.cpOffset, listener)) {
      stream.reset();
      return true;
    }
    return false;
  }

  private boolean readConstantPool(RecordingStream stream, int position, ChunkParserListener listener) throws IOException {
    stream.position(position);
    // checkpoint event
    CheckpointEvent event = new CheckpointEvent(stream);
    event.readConstantPools();
    if (!listener.onCheckpoint(event)) {
      return false;
    }
//
//    int delta = event.nextOffsetDelta;
//    event = null;
//    if (delta != 0) {
//      return readConstantPool(stream, position + delta, listener);
//    }
    return true;
  }
}

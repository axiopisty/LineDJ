package de.oliver_heger.jplaya.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import de.oliver_heger.jplaya.engine.AudioBuffer;
import de.oliver_heger.jplaya.engine.AudioStreamData;
import de.oliver_heger.jplaya.engine.ChainedInputStream;
import de.oliver_heger.jplaya.engine.ChainedInputStreamCallBack;
import de.oliver_heger.jplaya.engine.DataBufferEvent;
import de.oliver_heger.jplaya.engine.DataBufferListener;

/**
 * Test class for audio buffer.
 *
 * @author Oliver Heger
 * @version $Id$
 */
public class TestAudioBuffer
{
    /** Constant for the cache directory. */
    private static final String CACHE_DIR = "target/cache";

    /** Constant for the prefix of the name of a test stream. */
    private static final String NAME_PREFIX = "TestStream";

    /** Constant for the chunk size. */
    private static final long CHUNK_SIZE = 100;

    /** Constant for the sleep time. */
    private static final long SLEEP_TIME = 150;

    /** Constant for the number of chunks. */
    private static final int CHUNK_COUNT = 2;

    /** Stores the buffer to be tested. */
    private AudioBufferTestImpl buffer;

    @Before
    public void setUp() throws Exception
    {
        buffer =
                new AudioBufferTestImpl(CACHE_DIR, CHUNK_SIZE, CHUNK_COUNT,
                        true);
    }

    /**
     * Clears any used resources. Ensures that the cache directory is cleared
     * and removed.
     */
    @After
    public void tearDown() throws Exception
    {
        buffer.close();
        buffer.clear();
        removeCacheDir();
    }

    /**
     * Removes the cache directory if it exists.
     */
    private void removeCacheDir()
    {
        File cache = new File(CACHE_DIR);
        if (cache.exists())
        {
            assertTrue("Cache directory cannot be removed", cache.delete());
        }
    }

    /**
     * Creates a stream that contains test data of the given length. Creates a
     * string with the test data and constructs a byte array stream from it.
     *
     * @param len the desired length of the test data
     * @return the stream with the test data
     */
    private TestInputStream createTestStream(int len)
    {
        return new TestInputStream(StreamHelper.createTestStream(len));
    }

    /**
     * Returns a mock audio stream data object with the specified parameters.
     *
     * @param name the name of the stream
     * @param id the Id of the stream
     * @param size the size of the stream
     * @return the initialized mock object
     */
    private AudioStreamData createStreamData(String name, Object id, long size)
    {
        AudioStreamData mockData = EasyMock.createMock(AudioStreamData.class);
        EasyMock.expect(mockData.getName()).andStubReturn(name);
        EasyMock.expect(mockData.getID()).andStubReturn(id);
        EasyMock.expect(mockData.size()).andStubReturn(size);
        EasyMock.replay(mockData);
        return mockData;
    }

    /**
     * Writes a stream with test data into the audio buffer. This method creates
     * a test data stream and then writes it into the buffer using the specified
     * buffer size.
     *
     * @param index the index of the stream (used for creating a name for the
     *        stream)
     * @param len the length of the test stream
     * @param bufSize the buffer size for writing the single chunks
     * @throws IOException if an IO error occurs
     * @throws InterruptedException if the operation is interrupted
     */
    private void writeTestData(int index, int len, int bufSize)
            throws IOException, InterruptedException
    {
        buffer.addNewStream(createStreamData(NAME_PREFIX + index,
                Integer.valueOf(index), len));
        InputStream in = createTestStream(len);
        byte[] buffy = new byte[bufSize];
        int read;

        while ((read = in.read(buffy)) != -1)
        {
            buffer.addChunk(buffy, 0, read);
        }
        buffer.streamFinished();
    }

    /**
     * Checks the data that was added to the chained streams created by the
     * buffer.
     *
     * @param from the from index of the part list
     * @param to the to index of the part list (including)
     * @param size the expected stream size
     * @param callBackFile the index of the data file, for which a call back is
     *        expected or -1 for none
     */
    private void checkChunkParts(int from, int to, long size, int callBackFile)
    {
        List<File> files = buffer.getDataFiles();
        assertTrue("Too few chunk parts", buffer.getChildStreamCount() > to);
        for (int i = from; i <= to; i++)
        {
            assertEquals("Wrong size for stream at index " + i, size,
                    buffer.getChildStreamSize(i));
            if (callBackFile >= 0)
            {
                assertEquals("Wrong call back reference", buffer,
                        buffer.getChildStreamCallBack(i));
                assertEquals("Wrong call back file param",
                        files.get(callBackFile),
                        buffer.getChildStreamCallBackParam(i));
            }
            else
            {
                assertNull("A call back was registered",
                        buffer.getChildStreamCallBack(i));
                assertNull("A call back param was given",
                        buffer.getChildStreamCallBackParam(i));
            }
        }
    }

    /**
     * Tests whether the correct cache directory is set.
     */
    @Test
    public void testGetCacheDirectory()
    {
        assertEquals("Wrong cache directory set", new File(CACHE_DIR),
                buffer.getCacheDirectory());
    }

    /**
     * Tests whether the buffer was correctly initialized.
     */
    @Test
    public void testInit()
    {
        assertEquals("Wrong chunk size", CHUNK_SIZE, buffer.getChunkSize());
        assertEquals("Wrong chunk count", CHUNK_COUNT, buffer.getChunkCount());
        assertEquals("Wrong number of data files", 0, buffer.getDataFiles()
                .size());
        assertEquals("Wrong buffer size", 0, buffer.getCurrentSize());
        assertEquals("Wrong number of current chunks", 0,
                buffer.getCurrentChunkCount());
        assertEquals("Wrong number of allowed chunks", CHUNK_COUNT,
                buffer.getAllowedChunkCount());
    }

    /**
     * Tests creating an audio buffer with an undefined directory.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testInitInvalidDir() throws IOException
    {
        new AudioBuffer(null, CHUNK_SIZE, CHUNK_COUNT);
    }

    /**
     * Tests creating an audio buffer with an invalid chunk count argument.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testInitInvalidChunkCount() throws IOException
    {
        new AudioBuffer(CACHE_DIR, CHUNK_SIZE, 0);
    }

    /**
     * Tests creating an audio buffer with an invalid chunk size argument.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testInitInvalidChunkSize() throws IOException
    {
        new AudioBuffer(CACHE_DIR, 0, CHUNK_COUNT);
    }

    /**
     * Tests writing some test streams.
     */
    @Test
    public void testWriteData() throws IOException, InterruptedException
    {
        final int chunks = 5;
        final int size = (int) CHUNK_SIZE / chunks;
        TestWriterThread thread = new TestWriterThread(chunks, size, 16);
        thread.start();
        thread.waitForStream(chunks);
        assertEquals("Wrong number of data files", 1, buffer.getDataFiles()
                .size());
        assertEquals("Wrong number of current chunks", 1,
                buffer.getCurrentChunkCount());
        assertEquals("Wrong buffer size", chunks * size,
                buffer.getCurrentSize());
        File cache = new File(CACHE_DIR);
        assertTrue("Cache directory not created", cache.exists());
        buffer.close();
        assertEquals("Wrong number of available streams", chunks + 1,
                buffer.availableStreams());
        checkChunkParts(0, chunks - 2, size, -1);
        checkChunkParts(chunks - 1, chunks - 1, size, 0);

        for (int i = 0; i < chunks; i++)
        {
            AudioStreamData asd = buffer.nextAudioStream();
            StreamHelper.checkTestData(asd.getStream(), size);
            assertEquals("Wrong name for stream", NAME_PREFIX + i,
                    asd.getName());
        }
    }

    /**
     * Tests writing more data than the buffer is capable. The write operations
     * should block then.
     */
    @Test
    public void testWriteDataBlocking() throws InterruptedException
    {
        TestWriterThread thread =
                new TestWriterThread(CHUNK_COUNT + 5, (int) CHUNK_SIZE, 32);
        thread.start();
        thread.waitForStream(2);
        Thread.sleep(SLEEP_TIME);
        assertEquals("Wrong number of chunks", CHUNK_COUNT,
                buffer.getCurrentChunkCount());
        assertEquals("Wrong current size", CHUNK_SIZE * CHUNK_COUNT,
                buffer.getCurrentSize());
        assertEquals("Wrong number of chunk parts", 2,
                buffer.getChildStreamCount());
        checkChunkParts(0, 0, (int) CHUNK_SIZE, 0);
        checkChunkParts(1, 1, (int) CHUNK_SIZE, 1);
    }

    /**
     * Tests writing a large block at once that spans multiple chunks.
     */
    @Test
    public void testWriteLargeBlock() throws IOException, InterruptedException
    {
        writeTestData(0, CHUNK_COUNT * (int) CHUNK_SIZE, CHUNK_COUNT
                * (int) CHUNK_SIZE);
        assertEquals("Wrong number of available streams", 1,
                buffer.availableStreams());
        checkChunkParts(0, 0, (int) CHUNK_SIZE, 0);
        buffer.close();
        checkChunkParts(1, 1, (int) CHUNK_SIZE, 1);
    }

    /**
     * Tests writing only a part of a chunk. The data will become available only
     * after close() was called.
     */
    @Test
    public void testWriteWithClose() throws IOException, InterruptedException
    {
        final int len = (int) CHUNK_SIZE / 2;
        writeTestData(0, len, len);
        assertEquals("Already streams available", 0, buffer.availableStreams());
        buffer.close();
        assertEquals("Wrong number of streams available", 2,
                buffer.availableStreams());
        StreamHelper.checkTestData(buffer.nextAudioStream().getStream(), len);
    }

    /**
     * Tests the streamRead() call back. If this call back arrives, a new chunk
     * can be opened.
     */
    @Test
    public void testStreamRead() throws InterruptedException
    {
        final int count = 5;
        final int size = (int) CHUNK_SIZE;
        TestWriterThread thread = new TestWriterThread(count, size, size / 2);
        thread.start();
        thread.waitForStream(CHUNK_COUNT);
        Thread.sleep(SLEEP_TIME);
        assertEquals("Wrong number of chunks", CHUNK_COUNT,
                buffer.getCurrentChunkCount());
        assertEquals("Wrong number of allowed chunks", CHUNK_COUNT,
                buffer.getAllowedChunkCount());
        TestInputStream in = createTestStream(size);
        buffer.streamRead(in, size, buffer.getDataFiles().get(0));
        assertFalse("Stream was closed", in.isClosed);
        assertEquals("No additional chunks allowed", CHUNK_COUNT + 1,
                buffer.getAllowedChunkCount());
        for (int i = 3; i <= count; i++)
        {
            thread.waitForStream(i);
            buffer.streamRead(createTestStream(size), (i - 1) * size, buffer
                    .getDataFiles().get(i - 2));
        }
        thread.join();
        assertEquals("Wrong number of chunks at end", count,
                buffer.getCurrentChunkCount());
    }

    /**
     * Tests invoking the stream read multiple times for the same stream. Only
     * the first invocation should have an effect.
     */
    @Test
    public void testStreamReadMultipleTimes() throws InterruptedException
    {
        final int count = CHUNK_COUNT + 2;
        final int size = (int) CHUNK_SIZE;
        TestWriterThread thread = new TestWriterThread(count, size, size / 2);
        thread.start();
        thread.waitForStream(CHUNK_COUNT);
        TestInputStream in = createTestStream(size);
        buffer.streamRead(in, size, buffer.getDataFiles().get(0));
        thread.waitForStream(CHUNK_COUNT + 1);
        buffer.streamRead(in, 2 * size, buffer.getDataFiles().get(0));
        Thread.sleep(SLEEP_TIME);
        assertEquals("Number of chunks was increased", CHUNK_COUNT + 1,
                buffer.getCurrentChunkCount());
        assertEquals("Number of allowed chunks was increased", CHUNK_COUNT + 1,
                buffer.getAllowedChunkCount());
        buffer.streamRead(createTestStream(size), 3 * size, buffer
                .getDataFiles().get(1));
        thread.join();
    }

    /**
     * Tests the stream completed call back.
     */
    @Test
    public void testStreamCompleted() throws InterruptedException
    {
        final int count = CHUNK_COUNT + 1;
        final int size = (int) CHUNK_SIZE;
        TestWriterThread thread = new TestWriterThread(count, size, size / 2);
        thread.start();
        thread.waitForStream(CHUNK_COUNT);
        File f = buffer.getDataFiles().get(0);
        buffer.streamCompleted(buffer.getChildStream(0), size, f);
        assertEquals("Number of allowed chunks is wrong", CHUNK_COUNT,
                buffer.getAllowedChunkCount());
        assertFalse("File was not deleted", f.exists());
        thread.join();
    }

    /**
     * Tests both call backs together.
     */
    @Test
    public void testStreamReadAndCompleted() throws InterruptedException
    {
        final int count = CHUNK_COUNT + 2;
        final int size = (int) CHUNK_SIZE;
        TestWriterThread thread = new TestWriterThread(count, size, size / 2);
        thread.start();
        thread.waitForStream(CHUNK_COUNT);
        TestInputStream in = new TestInputStream(buffer.getChildStream(0));
        File f = buffer.getDataFiles().get(0);
        buffer.streamRead(in, size, f);
        thread.waitForStream(CHUNK_COUNT + 1);
        buffer.streamCompleted(in, size, f);
        assertEquals("Wrong number of allowed chunks", CHUNK_COUNT,
                buffer.getAllowedChunkCount());
        assertEquals("Wrong number of data files", 2, buffer.getDataFiles()
                .size());
        buffer.streamCompleted(createTestStream(size), 2 * size, buffer
                .getDataFiles().get(0));
        thread.join();
        assertEquals("Number of allowed chunks too small", CHUNK_COUNT,
                buffer.getAllowedChunkCount());
    }

    /**
     * Tests calling clear() without calling close() first. This should cause an
     * exception.
     */
    @Test(expected = IllegalStateException.class)
    public void testClearWithoutClose()
    {
        buffer.clear();
    }

    /**
     * Tests the close() method when a writer thread is blocking.
     */
    @Test
    public void testCloseWithBlockingThread() throws IOException,
            InterruptedException
    {
        TestWriterThread thread =
                new TestWriterThread(CHUNK_COUNT + 2, (int) CHUNK_SIZE,
                        (int) CHUNK_SIZE / 2);
        thread.start();
        thread.waitForStream(CHUNK_COUNT);
        checkClose(thread);
    }

    /**
     * Tests the close() method with a (probably) running writer thread.
     */
    @Test
    public void testCloseWithRunningThread() throws IOException,
            InterruptedException
    {
        TestWriterThread thread =
                new TestWriterThread(CHUNK_COUNT + 2, (int) CHUNK_SIZE, 1);
        thread.start();
        checkClose(thread);
    }

    /**
     * Tests the close() method together with a writer thread.
     *
     * @param thread the thread
     * @throws IOException if an IO error occurs
     * @throws InterruptedException if the operation is interrupted
     */
    private void checkClose(TestWriterThread thread) throws IOException,
            InterruptedException
    {
        buffer.close();
        assertTrue("Close flag is not set", buffer.isClosed());
        // The thread should now terminate
        thread.join();
        // Check if the close indicator empty stream was written
        AudioStreamData data;
        do
        {
            data = buffer.nextAudioStream();
        } while (data.size() >= 0);
        assertEquals("Data available after close indicator", 0,
                buffer.availableStreams());
    }

    /**
     * Exposes an ArrayIndexOutOfBoundsException in addChunk.
     */
    @Test
    public void testAddChunkOverflow() throws IOException, InterruptedException
    {
        buffer.addNewStream(createStreamData(NAME_PREFIX, NAME_PREFIX, 0L));
        byte[] chunk = new byte[(int) (2 * CHUNK_SIZE) - 1];
        buffer.addChunk(chunk, 0, chunk.length);
    }

    /**
     * Tests event notifications sent to a registered listener.
     */
    @Test
    public void testBufferListener() throws Exception
    {
        final int count = 10;
        AudioBufferListenerTestImpl l = new AudioBufferListenerTestImpl(buffer);
        buffer.addBufferListener(l);
        TestWriterThread writer =
                new TestWriterThread(count, (int) CHUNK_SIZE,
                        (int) (CHUNK_SIZE) / 2, true);
        writer.start();
        writer.waitForStream(CHUNK_COUNT);
        Thread.sleep(SLEEP_TIME);
        readStreamsFromBuffer(0);
        l.checkEventType(DataBufferEvent.Type.BUFFER_FREE, null);
        l.checkEventType(DataBufferEvent.Type.BUFFER_FULL, null);
        l.checkEventType(DataBufferEvent.Type.CHUNK_COUNT_CHANGED, null);
        // The CLOSED event may arrive later, so wait
        while (l.getEventCount(DataBufferEvent.Type.BUFFER_CLOSED) < 1)
        {
            Thread.sleep(SLEEP_TIME);
        }
        l.checkEventType(DataBufferEvent.Type.DATA_ADDED, null);
        l.checkEventType(DataBufferEvent.Type.BUFFER_CLOSED, 1);
    }

    /**
     * Tries to add a null listener. This should cause an exception.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testBufferListenerNull()
    {
        buffer.addBufferListener(null);
    }

    /**
     * Tests whether a buffer listener can be successfully removed.
     */
    @Test
    public void testRemoveBufferListener() throws Exception
    {
        final int count = 10;
        AudioBufferListenerTestImpl l = new AudioBufferListenerTestImpl(buffer);
        buffer.addBufferListener(l);
        TestWriterThread writer =
                new TestWriterThread(count, (int) CHUNK_SIZE,
                        (int) (CHUNK_SIZE) / 2, true);
        writer.start();
        readStreamsFromBuffer(count / 2);
        buffer.removeBufferListener(l);
        Map<DataBufferEvent.Type, Integer> oldEvents =
                new HashMap<DataBufferEvent.Type, Integer>(l.events);
        readStreamsFromBuffer(0);
        for (DataBufferEvent.Type t : oldEvents.keySet())
        {
            assertEquals("Event count changed after remove: " + t.name(),
                    oldEvents.get(t), l.events.get(t));
        }
    }

    /**
     * Reads a number of streams from the audio buffer.
     *
     * @param count the number of streams to read; can be 0, then the buffer
     *        will be completely read
     * @throws IOException if an IO error occurs
     * @throws InterruptedException if waiting is interrupted
     */
    private void readStreamsFromBuffer(int count) throws IOException,
            InterruptedException
    {
        int maxStreams = (count < 1) ? Integer.MAX_VALUE : count;
        for (int streamsRead = 0; streamsRead < maxStreams; streamsRead++)
        {
            AudioStreamData data = buffer.nextAudioStream();
            InputStream in = data.getStream();
            int streamSize = 0;
            while (in != null && in.read() != -1)
            {
                streamSize++;
            }
            if (streamSize < 1)
            {
                break;
            }
            in.close();
        }
    }

    /**
     * Tests clearing data from the cache directory.
     */
    @Test
    public void testClearCacheDirectory() throws IOException
    {
        for (int i = 0; i < 20; i++)
        {
            createTestFile(CACHE_DIR, i);
        }
        buffer.clearCacheDirectory();
    }

    /**
     * Tests whether sub directories are not affected by clearCacheDirectory().
     */
    @Test
    public void testClearCacheDirectorySubDir() throws IOException
    {
        createTestFile(CACHE_DIR, 1);
        File subDir = new File(CACHE_DIR, "sub");
        assertTrue("Sub dir cannot be created", subDir.mkdir());
        File testFile = createTestFile(subDir.getAbsolutePath(), 2);
        assertTrue("File does not exist", testFile.exists());
        buffer.clearCacheDirectory();
        assertTrue("File has been deleted", testFile.exists());
        assertTrue("Cannot delete test file", testFile.delete());
        assertTrue("Cannot delete sub dir", subDir.delete());
    }

    /**
     * Tests initializing a buffer without clearing the test directory.
     */
    @Test
    public void testInitNoClearCacheDirectory() throws IOException
    {
        File f = createTestFile(CACHE_DIR, 5);
        AudioBuffer buf = new AudioBuffer(CACHE_DIR, CHUNK_SIZE, CHUNK_COUNT);
        assertTrue("File was removed", f.exists());
        buf.close();
        buf.clear();
        assertTrue("Test file cannot be removed", f.delete());
    }

    /**
     * Tests the isFull() method.
     */
    @Test
    public void testIsFull() throws InterruptedException
    {
        assertFalse("Buffer already full", buffer.isFull());
        TestWriterThread thread =
                new TestWriterThread(CHUNK_COUNT + 5, (int) CHUNK_SIZE, 16);
        thread.start();
        thread.waitForStream(CHUNK_COUNT);
        Thread.sleep(SLEEP_TIME);
        assertTrue("Buffer not full", buffer.isFull());
    }

    /**
     * Tests whether the size of the involved input streams is correctly
     * maintained.
     */
    @Test
    public void testStreamSize() throws InterruptedException
    {
        final int streamSize = 10123;
        TestWriterThread writer =
                new TestWriterThread(1, streamSize, (int) CHUNK_SIZE, true);
        writer.start();
        Thread.sleep(SLEEP_TIME);
        AudioStreamData data = buffer.nextAudioStream();
        assertEquals("Wrong size of audio stream", streamSize, data.size());
    }

    /**
     * Tests whether the properties of an audio stream data object are correctly
     * copied.
     */
    @Test
    public void testAudioStreamDataProperties() throws InterruptedException,
            IOException
    {
        AudioStreamData orgData = EasyMock.createMock(AudioStreamData.class);
        final int index = 11;
        EasyMock.expect(orgData.getID()).andReturn(CHUNK_SIZE).anyTimes();
        EasyMock.expect(orgData.getName()).andReturn(NAME_PREFIX).anyTimes();
        EasyMock.expect(orgData.getIndex()).andReturn(index);
        EasyMock.expect(orgData.size()).andReturn(
                Long.valueOf(NAME_PREFIX.length()));
        EasyMock.replay(orgData);
        buffer.addNewStream(orgData);
        buffer.addChunk(NAME_PREFIX.getBytes(), 0, NAME_PREFIX.length());
        buffer.streamFinished();
        buffer.close();
        AudioStreamData data = buffer.nextAudioStream();
        assertEquals("Wrong ID", Long.valueOf(CHUNK_SIZE), data.getID());
        assertEquals("Wrong name", NAME_PREFIX, data.getName());
        assertEquals("Wrong index", index, data.getIndex());
        EasyMock.verify(orgData);
    }

    /**
     * Helper method for testing a shutdown operation.
     *
     * @param ex the exception to be thrown by close()
     */
    private void checkShutdown(IOException ex)
    {
        buffer.mockShutdownMethods(ex);
        buffer.shutdown();
        assertEquals("Wrong methods", "close()clear()", buffer.getMethods());
    }

    /**
     * Tests whether the buffer can be shut down.
     */
    @Test
    public void testShutdown()
    {
        checkShutdown(null);
    }

    /**
     * Tests shutdown() if an IOException is thrown.
     */
    @Test
    public void testShutdownEx()
    {
        checkShutdown(new IOException("Test exception!"));
    }

    /**
     * Creates a test file. Used for setting up a directory structure that can
     * be deleted by clearCacheDirectory().
     *
     * @param dir the directory
     * @param idx a numeric index, from which the file name will be derived
     * @return the created test file
     * @throws IOException if an io error occurs
     */
    private File createTestFile(String dir, int idx) throws IOException
    {
        File cache = new File(dir);
        if (!cache.exists())
        {
            assertTrue("Could not create cache directory", cache.mkdirs());
        }
        File f = new File(cache, "testfile" + idx + ".dat");
        FileOutputStream out = new FileOutputStream(f);
        try
        {
            InputStream in = createTestStream((int) CHUNK_SIZE);
            byte[] buf = new byte[128];
            int read;
            while ((read = in.read(buf)) != -1)
            {
                out.write(buf, 0, read);
            }
            in.close();
        }
        finally
        {
            out.close();
        }
        return f;
    }

    /**
     * A test input stream class. This class allows to find out whether the
     * close() method has been invoked.
     */
    static class TestInputStream extends FilterInputStream
    {
        public boolean isClosed;

        public TestInputStream(InputStream in)
        {
            super(in);
        }

        @Override
        public void close() throws IOException
        {
            super.close();
            isClosed = true;
        }
    }

    /**
     * A specific audio buffer implementation that is used for testing. This
     * class overrides some methods so that some of the internals can be tested.
     */
    static class AudioBufferTestImpl extends AudioBuffer
    {
        /** Stores a list with the added child streams. */
        private List<Object[]> childStreams;

        /** A buffer for recording method invocations. */
        private StringBuilder bufMethods;

        /** An exception to be thrown by close(). */
        private IOException closeException;

        /** A flag whether close() and clear() are to be mocked. */
        private boolean mockShutdownMethods;

        public AudioBufferTestImpl(String dir, long chunkSize, int chunks)
        {
            super(dir, chunkSize, chunks);
            childStreams = new ArrayList<Object[]>();
        }

        public AudioBufferTestImpl(String dir, long chunkSize, int chunks,
                boolean clearCache)
        {
            super(dir, chunkSize, chunks, clearCache);
            childStreams = new ArrayList<Object[]>();
        }

        /**
         * Prepares this object to mock the methods related to a shutdown.
         * Optionally, an exception to be thrown by close() can be specified.
         *
         * @param closeEx the exception for close() (can be null)
         */
        public void mockShutdownMethods(IOException closeEx)
        {
            bufMethods = new StringBuilder();
            closeException = closeEx;
            mockShutdownMethods = true;
        }

        /**
         * Returns the content of the buffer with method invocations.
         *
         * @return the methods that have been invoked
         */
        public String getMethods()
        {
            return bufMethods.toString();
        }

        /**
         * Optionally mocks this method. If an exception has been provided, it
         * is thrown now.
         */
        @Override
        public void close() throws IOException
        {
            final String method = "close()";
            if (shouldMock(method))
            {
                bufMethods.append(method);
                if (closeException != null)
                {
                    IOException ex = closeException;
                    closeException = null;
                    throw ex;
                }
            }
            else
            {
                super.close();
            }
        }

        /**
         * Optionally mocks this method.
         */
        @Override
        public void clear()
        {
            final String method = "clear()";
            if (shouldMock(method))
            {
                bufMethods.append(method);
            }
            else
            {
                super.clear();
            }
        }

        @Override
        void appendStream(ChainedInputStream stream, InputStream child,
                long len, ChainedInputStreamCallBack callBack, File param)
        {
            Object[] data = new Object[4];
            data[0] = Long.valueOf(len);
            data[1] = callBack;
            data[2] = param;
            data[3] = stream;
            childStreams.add(data);
            super.appendStream(stream, child, len, callBack, param);
        }

        /**
         * Returns the number of added child streams.
         *
         * @return the number of added child streams
         */
        public int getChildStreamCount()
        {
            return childStreams.size();
        }

        /**
         * Returns the size of the child stream with the given index.
         *
         * @param idx the index
         * @return the size of this stream
         */
        public long getChildStreamSize(int idx)
        {
            return (Long) childStreams.get(idx)[0];
        }

        /**
         * Returns the call back for the child stream with the given index.
         *
         * @param idx the index
         * @return the call back registered for that child stream
         */
        public ChainedInputStreamCallBack getChildStreamCallBack(int idx)
        {
            return (ChainedInputStreamCallBack) childStreams.get(idx)[1];
        }

        /**
         * Returns the call back param for the child stream with the given
         * index.
         *
         * @param idx the index
         * @return the param for this stream's call back
         */
        public File getChildStreamCallBackParam(int idx)
        {
            return (File) childStreams.get(idx)[2];
        }

        /**
         * Returns the child input stream with the given index.
         *
         * @param idx the index
         * @return the input stream for this index
         */
        public InputStream getChildStream(int idx)
        {
            return (InputStream) childStreams.get(idx)[3];
        }

        /**
         * Tests whether a method is to be mocked.
         *
         * @param method the method
         * @return a flag whether this method is to be mocked
         */
        private boolean shouldMock(String method)
        {
            return mockShutdownMethods && !getMethods().contains(method);
        }
    }

    /**
     * A test thread class that is used for writing data into the buffer. This
     * class can write a given number of test streams into the audio buffer. The
     * length of the streams can be configured and also the buffer size to use
     * when the chunks are written. It is possible to wait until a given number
     * of streams was written.
     */
    class TestWriterThread extends Thread
    {
        /** Stores the number of streams to write. */
        private int streamCount;

        /** Stores the number of streams that have been written. */
        private int streamsWritten;

        /** Stores the length of a test stream. */
        private int streamLength;

        /** Stores the buffer size. */
        private int bufSize;

        /** A flag whether the buffer should be closed after writing all data. */
        private boolean closeBuffer;

        /**
         * Creates a new instance of TestWriterThread and initializes it.
         *
         * @param count the number of streams to write
         * @param len the length of the streams
         * @param size the buffer size
         * @param fClose a flag whether the buffer should be closed at the end
         */
        public TestWriterThread(int count, int len, int size, boolean fClose)
        {
            streamCount = count;
            streamLength = len;
            bufSize = size;
            closeBuffer = fClose;
        }

        /**
         * Creates a new instance of TestWriterThread and initializes it.
         *
         * @param count the number of streams to write
         * @param len the length of the streams
         * @param size the buffer size
         */
        public TestWriterThread(int count, int len, int size)
        {
            this(count, len, size, false);
        }

        /**
         * Executes this thread. Writes data into the audio buffer.
         */
        @Override
        public void run()
        {
            try
            {
                while (getStreamsWritten() < streamCount && !buffer.isClosed())
                {
                    try
                    {
                        writeTestData(getStreamsWritten(), streamLength,
                                bufSize);
                    }
                    catch (IOException ioex)
                    {
                        fail("IO error: " + ioex);
                    }
                    incrementCount();
                }
                if (closeBuffer)
                {
                    buffer.close();
                }
            }
            catch (InterruptedException iex)
            {
                // exit the run() method
            }
            catch (IOException ioex)
            {
                fail(ioex.getMessage());
            }
        }

        /**
         * Returns the number of so far written streams.
         *
         * @return the number of written streams
         */
        public int getStreamsWritten()
        {
            return streamsWritten;
        }

        /**
         * Waits until the test stream with the given index was fully written.
         * If necessary, this operation will block.
         *
         * @param index the index of the desired stream
         */
        public synchronized void waitForStream(int index)
        {
            while (getStreamsWritten() < index)
            {
                try
                {
                    wait();
                }
                catch (InterruptedException iex)
                {
                    // ignore and try again
                }
            }
        }

        /**
         * Increments the stream counter. Notifies waiting objects.
         */
        private synchronized void incrementCount()
        {
            streamsWritten++;
            notify();
        }
    }

    /**
     * A test buffer listener implementation for testing event notifications.
     */
    static class AudioBufferListenerTestImpl implements DataBufferListener
    {
        /** A map for storing the number of received events. */
        private Map<DataBufferEvent.Type, Integer> events;

        /** Stores a reference to the expected event source. */
        private AudioBuffer source;

        /**
         * Creates a new instance of <code>AudioBufferListenerTestImpl</code>.
         *
         * @param buf the monitored buffer
         */
        public AudioBufferListenerTestImpl(AudioBuffer buf)
        {
            source = buf;
            events = new HashMap<DataBufferEvent.Type, Integer>();
        }

        /**
         * The buffer was changed. Record the event by its type.
         *
         * @param event the event
         */
        public synchronized void bufferChanged(DataBufferEvent event)
        {
            assertSame("Wrong event source", source, event.getSource());
            assertSame("Wrong source buffer", source, event.getSourceBuffer());
            Integer count = events.get(event.getType());
            count = (count == null) ? 1 : Integer.valueOf(count.intValue() + 1);
            events.put(event.getType(), count);
        }

        /**
         * Returns the number of events received for the specified type.
         *
         * @param t the event type
         * @return the number of received events of this type
         */
        public synchronized int getEventCount(DataBufferEvent.Type t)
        {
            Integer count = events.get(t);
            return (count != null) ? count.intValue() : 0;
        }

        /**
         * Checks the number of received events for the specified type. If null
         * is passed for the expected count, the method will only check whether
         * events have been received at all.
         *
         * @param t the event type
         * @param expectedCount the number of expected events
         */
        public void checkEventType(DataBufferEvent.Type t, Integer expectedCount)
        {
            int count = getEventCount(t);
            assertTrue("No events received of type " + t.name(), count > 0);
            if (expectedCount != null)
            {
                assertEquals("Wrong number of events",
                        expectedCount.intValue(), count);
            }
        }
    }
}

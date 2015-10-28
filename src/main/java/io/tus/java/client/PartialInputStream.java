package io.tus.java.client;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/**
 * <p>A <code>PartialInputStream</code> allows the amount of data read by an
 * <code>InputStream</code> to be capped. It decorates a given <code>InputStream</code> object
 * such that its contents are read up to the specified <code>maxReadBytes</code>. </p>
 * <p><code>PartialInputStream</code> does not close the underlying <code>InputStream</code> on
 * {@link #close()}. It is the responsibility of callers to externally close any underlying
 * streams.</p>
 */
public class PartialInputStream extends InputStream {
    private long maxReadBytes = Long.MAX_VALUE;
    private long bytesRead = 0l;
    private InputStream inputStream;
    private boolean isClosed = false;

    public PartialInputStream(InputStream inputStream, long maxReadBytes) {
        this.inputStream = inputStream;
        this.setMaxReadBytes(maxReadBytes);
    }

    /**
     * Gets the maximum number of bytes that can be read.
     * @return the maximum number of bytes that can be read from the underlying stream.
     */
    public long getMaxReadBytes() {
        return this.maxReadBytes;
    }

    /**
     * Sets the maximum number of bytes that can be read from a stream.
     * @param maxReadBytes
     */
    public void setMaxReadBytes(long maxReadBytes) {
        if (maxReadBytes > 0) {
            this.maxReadBytes = maxReadBytes;
        } else {
            this.maxReadBytes = Long.MAX_VALUE;
        }
    }

    public int read() throws IOException {
        if (!this.canReadMore()) {
            return -1;
        }
        int data = inputStream.read();
        if (data > -1) {
            this.bytesRead += 1;
        }
        return data;
    }

    boolean canReadMore() throws IOException {
        return this.bytesRead < this.maxReadBytes && !checkClosed();
    }

    long getMaxRemainingBytes() {
        return this.maxReadBytes - this.bytesRead;
    }

    @Override
    public int read(byte[] buf) throws IOException {
        if (!this.canReadMore()) {
            return -1;
        }
        if (buf.length > this.getMaxRemainingBytes()) {
            return this.read(buf, 0, (int) this.getMaxRemainingBytes());
        }
        int read = inputStream.read(buf);
        if (read > -1) {
            this.bytesRead += read;
        }
        return read;
    }

    @Override
    public int read(byte[] buf, int off, int len) throws IOException {
        if (!this.canReadMore()) {
            return -1;
        }
        int read;
        if (len > this.getMaxRemainingBytes()) {
            read = inputStream.read(buf, off, (int) this.getMaxRemainingBytes());
        } else {
            read = inputStream.read(buf, off, len);
        }
        if (read > -1) {
            this.bytesRead += read;
        }
        return read;
    }

    public long skip(long n) throws IOException {
        checkClosed();
        long origBytesRead = this.bytesRead;
        long skipped = inputStream.skip(n);
        this.bytesRead = origBytesRead;
        return skipped;
    }

    /**
     * Closes this stream, but does not close the underlying stream.
     * @throws IOException
     */
    @Override
    public void close() throws IOException {
        super.close();
        this.isClosed = true;
    }

    @Override
    public int available() throws IOException {
        checkClosed();
        return inputStream.available();
    }

    @Override
    public synchronized void mark(int readlimit) {
        if (!isClosed) {
            inputStream.mark(readlimit);
        }
    }

    @Override
    public synchronized void reset() throws IOException {
        inputStream.reset();
    }

    @Override
    public boolean markSupported() {
        return inputStream.markSupported();
    }

    boolean checkClosed() throws IOException {
        if (isClosed) {
            throw new IOException("PartialInputStream is closed.");
        }
        return false;
    }
}

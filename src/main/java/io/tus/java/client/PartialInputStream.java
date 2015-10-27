package io.tus.java.client;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/**
 * A <code>PartialInputStream</code> allows the amount of data read by an
 * <code>InputStream</code> to be capped. When created, the client c
 */
public class PartialInputStream extends InputStream {
    private long maxReadBytes = Long.MAX_VALUE;
    private long bytesRead = 0l;
    private InputStream inputStream;

    public PartialInputStream(InputStream inputStream, long maxReadBytes) {
        this.inputStream = inputStream;
        this.setMaxReadBytes(maxReadBytes);
    }

    public PartialInputStream(File file, long maxReadBytes) throws FileNotFoundException {
        this.inputStream = new FileInputStream(file);
        this.setMaxReadBytes(maxReadBytes);
    }

    public long getMaxReadBytes() {
        return this.maxReadBytes;
    }

    public void setMaxReadBytes(long maxReadBytes) {
        if (maxReadBytes > 0) {
            this.maxReadBytes = maxReadBytes;
        } else {
            this.maxReadBytes = Long.MAX_VALUE;
        }
    }

    long getBytesRead() {
        return this.bytesRead;
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

    boolean canReadMore() {
        return this.bytesRead < this.maxReadBytes;
    }

    long getMaxRemainingBytes() {
        return this.maxReadBytes - this.bytesRead;
    }

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
        long origBytesRead = this.bytesRead;
        long skipped = inputStream.skip(n);
        this.bytesRead = origBytesRead;
        return skipped;
    }

    @Override
    public void close() throws IOException {
        if (this.inputStream != null) {
            this.inputStream.close();
        }
    }

    @Override
    public int available() throws IOException {
        return inputStream.available();
    }

    @Override
    public synchronized void mark(int readlimit) {
        inputStream.mark(readlimit);
    }

    @Override
    public synchronized void reset() throws IOException {
        inputStream.reset();
    }

    @Override
    public boolean markSupported() {
        return inputStream.markSupported();
    }
}

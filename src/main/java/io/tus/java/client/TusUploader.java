package io.tus.java.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.net.URLConnection;

/**
 * This class is used for doing the actual upload of the files. Instances are returned by
 * {@link TusClient#createUpload(TusUpload)}, {@link TusClient#createUpload(TusUpload)} and
 * {@link TusClient#resumeOrCreateUpload(TusUpload)}.
 * <br>
 * After obtaining an instance you can upload a file by following these steps:
 * <ol>
 *  <li>Upload a chunk using {@link #uploadChunk()}</li>
 *  <li>Optionally get the new offset ({@link #getOffset()} to calculate the progress</li>
 *  <li>Repeat step 1 until the {@link #uploadChunk()} returns -1</li>
 *  <li>Close HTTP connection and InputStream using {@link #finish()} to free resources</li>
 * </ol>
 */
public class TusUploader {
    private URL uploadURL;
    private InputStream input;
    private long offset;
    private byte[] buffer;

    private HttpURLConnection connection;
    private OutputStream output;

    /**
     * Begin a new upload request by opening a PATCH request to specified upload URL. After this
     * method returns a connection will be ready and you can upload chunks of the file.
     *
     * @param client Used for preparing a request ({@link TusClient#prepareConnection(URLConnection)}
     * @param uploadURL URL to send the request to
     * @param input Stream to read (and seek) from and upload to the remote server
     * @param offset Offset to read from
     * @throws IOException Thrown if an exception occurs while issuing the HTTP request.
     */
    public TusUploader(TusClient client, URL uploadURL, InputStream input, long offset) throws IOException {
        this.uploadURL = uploadURL;
        this.input = input;
        this.offset = offset;

        input.skip(offset);

        setChunkSize(2 * 1024 * 1024);

        connection = (HttpURLConnection) uploadURL.openConnection();
        client.prepareConnection(connection);
        connection.setRequestProperty("Upload-Offset", Long.toString(offset));
        connection.setRequestProperty("Content-Type", "application/offset+octet-stream");
        try {
            connection.setRequestMethod("PATCH");
            // Check whether we are running on a buggy JRE
        } catch (final ProtocolException pe) {
            connection.setRequestMethod("POST");
            connection.setRequestProperty("X-HTTP-Method-Override", "PATCH");
        }

        connection.setDoOutput(true);
        connection.setChunkedStreamingMode(0);
        output = connection.getOutputStream();
    }

    /**
     * Sets the used chunk size. This number is used by {@link #uploadChunk()} to indicate how
     * much data is uploaded in a single take. When choosing a value for this parameter you need to
     * consider that uploadChunk() will only return once the specified number of bytes has been
     * sent. For slow internet connections this may take a long time. In addition, a buffer with
     * the chunk size is allocated and kept in memory.
     *
     * @param size The new chunk size
     */
    public void setChunkSize(int size) {
        buffer = new byte[size];
    }

    /**
     * Returns the current chunk size set using {@link #setChunkSize(int)}.
     *
     * @return Current chunk size
     */
    public int getChunkSize() {
        return buffer.length;
    }

    /**
     * Upload a part of the file by reading a chunk from the InputStream and writing
     * it to the HTTP request's body. If the number of available bytes is lower than the chunk's
     * size, all available bytes will be uploaded and nothing more.
     * No new connection will be established when calling this method, instead the connection opened
     * in the constructor will be used.
     * The size of the read chunk can be obtained using {@link #getChunkSize()} and changed
     * using {@link #setChunkSize(int)}.
     * In order to obtain the new offset, use {@link #getOffset()} after this method returns.
     *
     * @return Number of bytes read and written.
     * @throws IOException  Thrown if an exception occurs while reading from the source or writing
     *                      to the HTTP request.
     * @throws io.tus.java.client.ProtocolException Thrown if an error code is received while
     *                                              writing to the connection.
     */
    public int uploadChunk() throws IOException, io.tus.java.client.ProtocolException {
        int bytesRead = input.read(buffer);
        if(bytesRead == -1) {
            // No bytes were read since the input stream is empty
            return -1;
        }

        // Do not write the entire buffer to the stream since the array will
        // be filled up with 0x00s if the number of read bytes is lower then
        // the chunk's size.
        output.write(buffer, 0, bytesRead);
        output.flush();

        offset += bytesRead;

        int responseCode = connection.getResponseCode();
        if (responseCode >= 400) {
            throw new io.tus.java.client.ProtocolException("unexpected error code (" + responseCode + ") while uploading chunk", connection);
        }

        return bytesRead;
    }

    /**
     * Upload a part of the file by read a chunks specified size from the InputStream and writing
     * it to the HTTP request's body. If the number of available bytes is lower than the chunk's
     * size, all available bytes will be uploaded and nothing more.
     * No new connection will be established when calling this method, instead the connection opened
     * in the constructor will be used.
     * In order to obtain the new offset, use {@link #getOffset()} after this method returns.
     *
     * @deprecated This method is inefficient and has been replaced by {@link #setChunkSize(int)}
     *             and {@link #uploadChunk()} and should not be used anymore. The reason is, that
     *             this method allocates a new buffer with the supplied chunk size for each time
     *             it's called without reusing it. This results in a high number of memory
     *             allocations and should be avoided. The new methods do not have this issue.
     *
     * @param chunkSize Maximum number of bytes which will be uploaded. When choosing a value
     *                  for this parameter you need to consider that the method call will only
     *                  return once the specified number of bytes have been sent. For slow
     *                  internet connections this may take a long time.
     * @return Number of bytes read and written.
     * @throws IOException  Thrown if an exception occurs while reading from the source or writing
     *                      to the HTTP request.
     * @throws io.tus.java.client.ProtocolException Thrown if an error code is received while
     *                                              writing to the connection.
     */
    @Deprecated public int uploadChunk(int chunkSize) throws IOException, io.tus.java.client.ProtocolException {
        byte[] buf = new byte[chunkSize];
        int bytesRead = input.read(buf);
        if(bytesRead == -1) {
            // No bytes were read since the input stream is empty
            return -1;
        }

        // Do not write the entire buffer to the stream since the array will
        // be filled up with 0x00s if the number of read bytes is lower then
        // the chunk's size.
        output.write(buf, 0, bytesRead);
        output.flush();

        offset += bytesRead;

        int responseCode = connection.getResponseCode();
        if (responseCode >= 400) {
            throw new io.tus.java.client.ProtocolException("unexpected error code (" + responseCode + ") while uploading chunk", connection);
        }

        return bytesRead;
    }

    /**
     * Get the current offset for the upload. This is the number of all bytes uploaded in total and
     * in all requests (not only this one). You can use it in conjunction with
     * {@link TusUpload#getSize()} to calculate the progress.
     *
     * @return The upload's current offset.
     */
    public long getOffset() {
        return offset;
    }

    public URL getUploadURL() {
        return uploadURL;
    }

    /**
     * Finish the request by closing the HTTP connection and the InputStream.
     * You can call this method even before the entire file has been uploaded. Use this behavior to
     * enable pausing uploads.
     *
     * @throws io.tus.java.client.ProtocolException Thrown if the server sends an unexpected status
     * code
     * @throws IOException  Thrown if an exception occurs while cleaning up.
     */
    public void finish() throws io.tus.java.client.ProtocolException, IOException {
        input.close();
        output.close();
        int responseCode = connection.getResponseCode();
        connection.disconnect();

        if(!(responseCode >= 200 && responseCode < 300)) {
            throw new io.tus.java.client.ProtocolException("unexpected status code (" + responseCode + ") while uploading chunk");
        }
    }
}

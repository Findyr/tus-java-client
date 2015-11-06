package io.tus.java.client;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/**
 * This class is used for doing the actual upload of the files. Instances are returned by
 * {@link TusClient#createUpload(TusUpload)}, {@link TusClient#createUpload(TusUpload)} and
 * {@link TusClient#resumeOrCreateUpload(TusUpload)}.
 * <br>
 * After obtaining an instance you can upload a file by following these steps:
 * <ol>
 *  <li>Upload a chunk using {@link #uploadChunk(int)}</li>
 *  <li>Optionally get the new offset ({@link #getOffset()} to calculate the progress</li>
 *  <li>Repeat step 1 until the {@link #uploadChunk(int)} returns the file's size</li>
 * </ol>
 */
public class TusUploader implements Closeable, AutoCloseable {
    private URL uploadURL;
    private long offset;
    private TusClient client;
    private InputStream input;

    /**
     * Begin a new upload request by opening a PATCH request to specified upload URL. After this
     * method returns a connection will be ready and you can upload chunks of the file.
     *
     * @param client Used for preparing a request
     * @param uploadURL URL to send the request to
     * @param upload File or stream to upload.
     * @param offset Offset to read from
     */
    public TusUploader(TusClient client, URL uploadURL, TusUpload upload, long offset) throws
            IOException {
        this.uploadURL = uploadURL;
        this.input = upload.getInputStream();
        this.offset = offset;
        this.client = client;
        long bytesSkipped = input.skip(this.offset);
        if (bytesSkipped != this.offset) {
            throw new IllegalStateException("Could not skip to offset");
        }
    }

    /**
     * Upload a part of the file via a PATCH request. The upload will write up to
     * <code>chunkSize</code> bytes to the HTTP request body.
     *
     * @param chunkSize Maximum number of bytes which will be uploaded. When choosing a value
     *                  for this parameter you need to consider that the method call will only
     *                  return once the specified number of bytes have been sent, and the server
     *                  has sent its response.
     * @return the new offset value for the file.
     * @throws IOException  Thrown if an exception occurs while reading from the source or writing
     *                      to the HTTP request.
     */
    public long uploadChunk(int chunkSize) throws IOException, io.tus.java.client
            .ProtocolException {
        HttpRequest.Builder builder = this.client.getProvider().getRequestBuilder(this.uploadURL);
        this.client.prepareRequest(builder);
        PartialInputStream uploadInput = new PartialInputStream(this.input, chunkSize);
        try {
            uploadInput.mark(chunkSize);
            builder.addHeader("Upload-Offset", Long.toString(offset));
            builder.setBody(uploadInput);
            HttpRequest request = builder.patch();
            HttpResponse response = this.client.getProvider().executeRequest(request);
            int responseCode = response.getResponseCode();
            if (!(responseCode >= 200 && responseCode < 300)) {
                uploadInput.reset();
                throw new io.tus.java.client.ProtocolException("unexpected status code " +
                        responseCode + " while uploading chunk.", responseCode);
            }
            String newOffset = response.getHeader("Upload-Offset");
            offset = Long.parseLong(newOffset);
        } catch (IOException e) {
            uploadInput.reset();
            throw e;
        } finally {
            uploadInput.close();
        }
        return offset;
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

    @Override
    public void close() throws IOException {
        if (this.input != null) {
            this.input.close();
        }
    }
}

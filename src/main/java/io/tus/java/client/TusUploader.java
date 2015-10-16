package io.tus.java.client;

import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.net.URLConnection;

import okio.BufferedSink;
import okio.Okio;
import okio.Source;

/**
 * This class is used for doing the actual upload of the files. Instances are returned by
 * {@link TusClient#createUpload(TusUpload)}, {@link TusClient#createUpload(TusUpload)} and
 * {@link TusClient#resumeOrCreateUpload(TusUpload)}.
 * <br>
 * After obtaining an instance you can upload a file by following these steps:
 * <ol>
 * <li>Upload a chunk using {@link #uploadChunk(int)}</li>
 * <li>Optionally get the new offset ({@link #getOffset()} to calculate the progress</li>
 * <li>Repeat step 1 until the {@link #uploadChunk(int)} returns -1</li>
 * <li>Close HTTP connection and InputStream using {@link #finish()} to free resources</li>
 * </ol>
 */
public class TusUploader {
    private URL uploadURL;
    private InputStream input;
    private long offset;
    private OkHttpClient httpClient;

    private Request.Builder requestBuilder;

    /**
     * Begin a new upload request by opening a PATCH request to specified upload URL. After this
     * method returns a connection will be ready and you can upload chunks of the file.
     *
     * @param client    Used for preparing a request ({@link TusClient#prepareRequest(Request.Builder)}
     * @param uploadURL URL to send the request to
     * @param input     Stream to read (and seek) from and upload to the remote server
     * @param offset    Offset to read from
     * @throws IOException Thrown if an exception occurs while issuing the HTTP request.
     */
    public TusUploader(TusClient client, URL uploadURL, InputStream input, long offset) throws
            IOException {
        this.uploadURL = uploadURL;
        this.input = input;
        this.offset = offset;
        this.httpClient = client.okHttpClient;

        input.skip(offset);
        this.requestBuilder = new Request.Builder().url(uploadURL);
        client.prepareRequest(this.requestBuilder);
    }

    /**
     * Upload a part of the file by read a chunks specified size from the InputStream and writing
     * it to the HTTP request's body. If the number of available bytes is lower than the chunk's
     * size, all available bytes will be uploaded and nothing more.
     * No new connection will be established when calling this method, instead the connection opened
     * in the constructor will be used.
     * In order to obtain the new offset, use {@link #getOffset()} after this method returns.
     *
     * @param chunkSize Maximum number of bytes which will be uploaded. When choosing a value
     *                  for this parameter you need to consider that the method call will only
     *                  return once the specified number of bytes have been sent. For slow
     *                  internet connections this may take a long time.
     * @return Number of bytes read and written.
     * @throws IOException Thrown if an exception occurs while reading from the source or writing
     *                     to the HTTP request.
     */
    public int uploadChunk(int chunkSize) throws IOException, io.tus.java.client.ProtocolException {
        // TODO: is it safe to use available???
        int length = Math.min(chunkSize, input.available());
        RequestBody body = new RequestBody() {
            @Override
            public MediaType contentType() {
                return MediaType.parse("application/octet-stream");
            }

            @Override
            public long contentLength() throws IOException {
                return length;
            }

            @Override
            public void writeTo(BufferedSink sink) throws IOException {
                Source source = Okio.source(input);
                sink.write(source, length);
            }
        };
        this.requestBuilder.addHeader("Upload-Offset", Long.toString(offset));
        this.requestBuilder.patch(body);
        Response response = this.httpClient.newCall(requestBuilder.build()).execute();
        int responseCode = response.code();
        if (!(responseCode >= 200 && responseCode < 300)) {
            throw new io.tus.java.client.ProtocolException(
                    "unexpected status code (" + responseCode + ") while uploading chunk");
        }
        String offsetStr = response.header("Upload-Offset", "");
        if (offsetStr.length() == 0) {
            throw new io.tus.java.client.ProtocolException(
                    "missing upload offset in response for resuming upload");
        }
        this.offset = Long.parseLong(offsetStr);
        return length;
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
     *                                              code
     * @throws IOException                          Thrown if an exception occurs while cleaning up.
     */
    public void finish() throws IOException {
        input.close();
    }
}

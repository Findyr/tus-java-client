package io.tus.java.client;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

/**
 * This class is used for creating or resuming uploads.
 */
public class TusClient {
    /**
     * Version of the tus protocol used by the client. The remote server needs to support this
     * version, too.
     */
    public final static String TUS_VERSION = "1.0.0";

    private URL uploadCreationURL;
    private boolean resumingEnabled;
    private TusURLStore urlStore;
    OkHttpClient okHttpClient;

    /**
     * Create a new tus client.
     */
    public TusClient() {
        this.okHttpClient = new OkHttpClient();
    }

    /**
     * Set the URL used for creating new uploads. This is required if you want to initiate new
     * uploads using {@link #createUpload} or {@link #resumeOrCreateUpload} but is not used if you
     * only resume existing uploads.
     *
     * @param uploadCreationURL Absolute upload creation URL
     */
    public void setUploadCreationURL(URL uploadCreationURL) {
        this.uploadCreationURL = uploadCreationURL;
    }

    /**
     * Get the current upload creation URL
     *
     * @return Current upload creation URL
     */
    public URL getUploadCreationURL() {
        return uploadCreationURL;
    }

    /**
     * Enable resuming already started uploads. This step is required if you want to use
     * {@link #resumeUpload(TusUpload)}.
     *
     * @param urlStore Storage used to save and retrieve upload URLs by its fingerprint.
     */
    public void enableResuming(TusURLStore urlStore) {
        resumingEnabled = true;
        this.urlStore = urlStore;
    }

    /**
     * Disable resuming started uploads.
     *
     * @see #enableResuming(TusURLStore)
     */
    public void disableResuming() {
        resumingEnabled = false;
        this.urlStore = null;
    }

    /**
     * Get the current status if resuming.
     *
     * @see #enableResuming(TusURLStore)
     * @see #disableResuming()
     *
     * @return True if resuming has been enabled using {@link #enableResuming(TusURLStore)}
     */
    public boolean resumingEnabled() {
        return resumingEnabled;
    }

    /**
     * Create a new upload using the Creation extension. Before calling this function, an "upload
     * creation URL" must be defined using {@link #setUploadCreationURL(URL)} or else this
     * function will fail.
     * In order to create the upload a POST request will be issued. The file's chunks must be
     * uploaded manually using the returned {@link TusUploader} object.
     *
     * @param upload The file for which a new upload will be created
     * @return Use {@link TusUploader} to upload the file's chunks.
     * @throws ProtocolException Thrown if the remote server sent an unexpected response, e.g.
     * wrong status codes or missing/invalid headers.
     * @throws IOException Thrown if an exception occurs while issuing the HTTP request.
     */
    public TusUploader createUpload(TusUpload upload) throws ProtocolException, IOException {
        RequestBody emptyBody = RequestBody.create(null, new byte[0]);
        Request.Builder requestBuilder = new Request.Builder().url(uploadCreationURL).post
                (emptyBody);
        prepareRequest(requestBuilder);

        String encodedMetadata = upload.getEncodedMetadata();
        if(encodedMetadata.length() > 0) {
            requestBuilder.addHeader("Upload-Metadata", encodedMetadata);
        }

        requestBuilder.addHeader("Upload-Length", Long.toString(upload.getSize()));
        Response response = okHttpClient.newCall(requestBuilder.build()).execute();

        int responseCode = response.code();
        if(!(responseCode >= 200 && responseCode < 300)) {
            throw new ProtocolException("unexpected status code (" + responseCode + ") while creating upload");
        }

        String urlStr = response.header("Location", "");
        if(urlStr.length() == 0) {
            throw new ProtocolException("missing upload URL in response for creating upload");
        }

        URL uploadURL = new URL(urlStr);

        if(resumingEnabled) {
            urlStore.set(upload.getFingerprint(), uploadURL);
        }

        return new TusUploader(this, uploadURL, upload.getInputStream(), 0);
    }

    /**
     * Try to resume an already started upload. Before call this function, resuming must be
     * enabled using {@link #enableResuming(TusURLStore)}. This method will look up the URL for this
     * upload in the {@link TusURLStore} using the upload's fingerprint (see
     * {@link TusUpload#getFingerprint()}). After a successful lookup a HEAD request will be issued
     * to find the current offset without uploading the file, yet.
     *
     * @param upload The file for which an upload will be resumed
     * @return Use {@link TusUploader} to upload the remaining file's chunks.
     * @throws FingerprintNotFoundException Thrown if no matching fingerprint has been found in
     * {@link TusURLStore}. Use {@link #createUpload(TusUpload)} to create a new upload.
     * @throws ResumingNotEnabledException Throw if resuming has not been enabled using {@link
     * #enableResuming(TusURLStore)}.
     * @throws ProtocolException Thrown if the remote server sent an unexpected response, e.g.
     * wrong status codes or missing/invalid headers.
     * @throws IOException Thrown if an exception occurs while issuing the HTTP request.
     */
    public TusUploader resumeUpload(TusUpload upload) throws FingerprintNotFoundException, ResumingNotEnabledException, ProtocolException, IOException {
        if(!resumingEnabled) {
            throw new ResumingNotEnabledException();
        }

        URL uploadURL = urlStore.get(upload.getFingerprint());
        if(uploadURL == null) {
            throw new FingerprintNotFoundException(upload.getFingerprint());
        }
        Request.Builder requestBuilder = new Request.Builder().url(uploadURL).head();
        prepareRequest(requestBuilder);

        Response response = okHttpClient.newCall(requestBuilder.build()).execute();

        int responseCode = response.code();
        if(!(responseCode >= 200 && responseCode < 300)) {
            throw new ProtocolException("unexpected status code (" + responseCode + ") while resuming upload");
        }

        String offsetStr = response.header("Upload-Offset", "");
        if(offsetStr.length() == 0) {
            throw new ProtocolException("missing upload offset in response for resuming upload");
        }
        long offset = Long.parseLong(offsetStr);

        return new TusUploader(this, uploadURL, upload.getInputStream(), offset);
    }

    /**
     * Try to resume an upload using {@link #resumeUpload(TusUpload)}. If the method call throws
     * an {@link ResumingNotEnabledException} or {@link FingerprintNotFoundException}, a new upload
     * will be created using {@link #createUpload(TusUpload)}.
     *
     * @param upload The file for which an upload will be resumed
     * @throws ProtocolException Thrown if the remote server sent an unexpected response, e.g.
     * wrong status codes or missing/invalid headers.
     * @throws IOException Thrown if an exception occurs while issuing the HTTP request.
     */
    public TusUploader resumeOrCreateUpload(TusUpload upload) throws ProtocolException, IOException {
        try {
            return resumeUpload(upload);
        } catch(FingerprintNotFoundException e) {
            return createUpload(upload);
        } catch(ResumingNotEnabledException e) {
            return createUpload(upload);
        }
    }

    /**
     * Set headers used for every HTTP request.
     * @param requestBuilder the HTTP request currently being built.
     */
    public void prepareRequest(Request.Builder requestBuilder) {
        requestBuilder.addHeader("Tus-Resumable", TUS_VERSION);
    }
}

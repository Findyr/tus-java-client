package io.tus.java.client;

import java.io.IOException;
import java.net.URL;

import io.tus.java.client.provider.HttpUrlConnectionProvider;

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
    private HttpProvider provider;

    /**
     * Create a new tus client.
     */
    public TusClient() {
        this.provider = new HttpUrlConnectionProvider();
    }

    public TusClient(HttpProvider provider) {
        this.provider = provider;
    }

    public HttpProvider getProvider() {
        return this.provider;
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
     * Get the <code>TusURLStore</code> used for resuming uploads. If upload resuming is not
     * enabled, this will return <code>null</code>
     * @return the <code>TusURLStore</code> used for resuming uploads.
     */
    public TusURLStore getUrlStore() {
        return this.urlStore;
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
        HttpRequest.Builder builder = provider.getRequestBuilder(uploadCreationURL);
        this.prepareRequest(builder);

        String encodedMetadata = upload.getEncodedMetadata();
        if(encodedMetadata.length() > 0) {
            builder.addHeader("Upload-Metadata", encodedMetadata);
        }

        builder.addHeader("Upload-Length", Long.toString(upload.getSize()));
        HttpRequest request = builder.post();
        HttpResponse response = provider.executeRequest(request);

        int responseCode = response.getResponseCode();
        if(!(responseCode >= 200 && responseCode < 300)) {
            throw new ProtocolException("unexpected status code (" + responseCode + ") while " +
                    "creating upload", responseCode);
        }

        String urlStr = response.getHeader("Location");
        if(urlStr.length() == 0) {
            throw new ProtocolException("missing upload URL in response for creating upload",
                    responseCode);
        }

        URL uploadURL = new URL(urlStr);

        if(resumingEnabled) {
            urlStore.set(upload.getFingerprint(), uploadURL);
        }

        return new TusUploader(this, uploadURL, upload, 0);
    }

    public void prepareRequest(HttpRequest.Builder builder) {
        builder.addHeader("Tus-Resumable", TUS_VERSION);
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
        HttpRequest.Builder builder = this.provider.getRequestBuilder(uploadURL);
        this.prepareRequest(builder);
        HttpRequest request = builder.head();
        HttpResponse response = this.provider.executeRequest(request);

        int responseCode = response.getResponseCode();
        if(!(responseCode >= 200 && responseCode < 300)) {
            throw new ProtocolException("unexpected status code (" + responseCode + ") while " +
                    "resuming upload", responseCode);
        }

        String offsetStr = response.getHeader("Upload-Offset");
        if(offsetStr.length() == 0) {
            throw new ProtocolException("missing upload offset in response for resuming upload",
                    responseCode);
        }
        long offset = Long.parseLong(offsetStr);

        return new TusUploader(this, uploadURL, upload, offset);
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
}

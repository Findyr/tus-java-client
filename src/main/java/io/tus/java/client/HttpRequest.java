package io.tus.java.client;

import java.io.IOException;
import java.io.InputStream;

/**
 * <code>HttpRequest</code> represents an HTTP request that is sent by an {@link HttpProvider}.
 */
public interface HttpRequest {

    /**
     * Gets a stream representing the request body.
     * @return an <code>InputStream</code> containing the request body.
     */
    InputStream getBody();

    /**
     * Gets the value for the given HTTP header
     * <br>
     * Example: "Accept" for HTTP "Accept: " header.
     * @param key - key for the HTTP request header.
     * @return - the value for the HTTP request header.
     */
    String getHeader(String key);

    /**
     * <code>Builder</code> objects are used to create new HTTP requests.
     */
    interface Builder {

        /**
         * Adds header data to the HttpRequest
         * @param key - key for header
         * @param value - value for header
         */
        Builder addHeader(String key, String value);

        /**
         * Sets the <code>InputStream</code> for the request body.
         * @param body - the <code>InputStream</code> containing the request body.
         */
        Builder setBody(InputStream body);

        /**
         * Sets the request method for the request.
         * <br/>
         * Implementations should ensure that the client or network supports the request method
         * requests. If the request method is not supported, the request method should be set to
         * POST, and the "X-HTTP-Method-Override" header should be set to the desired request
         * method.
         * @param method the HTTP method to set for the request.
         */
        Builder setRequestMethod(String method);

        /**
         * Builds an HTTP GET request
         * @return an <code>HttpRequest</code> with the GET request method.
         * @throws IOException
         */
        HttpRequest get() throws IOException;

        /**
         * Builds an HTTP HEAD request.
         * @return an <code>HttpRequest</code> with the HEAD request method.
         * @throws IOException
         */
        HttpRequest head() throws IOException;

        /**
         * Builds an HTTP OPTIONS request.
         * @return an <code>HttpRequest</code> with the OPTIONS request method.
         * @throws IOException
         */
        HttpRequest options() throws IOException;

        /**
         * Builds an HTTP POST request.
         * @return an <code>HttpRequest</code> with the POST request method.
         * @throws IOException
         */
        HttpRequest post() throws IOException;

        /**
         * Builds an HTTP PUT request.
         * <br/>
         * Implementations should ensure that server or network supports PUT requests. If PUT
         * requests are not supported, the returned request should use the POST method, and
         * set the "X-HTTP-Method-Override" header to "PUT"
         * @return an <code>HttpRequest</code> that represents a PUT request.
         * @throws IOException
         */
        HttpRequest put() throws IOException;

        /**
         * Builds an HTTP PATCH request.
         * <br/>
         * Implementations should ensure that client or network supports PATCH requests. If PATCH
         * requests are not supported, the returned request should use the POST method, and
         * set the "X-HTTP-Method-Override" header to "PATCH"
         * @return an <code>HttpRequest</code> that represents a PATCH request.
         * @throws IOException
         */
        HttpRequest patch() throws IOException;

        /**
         * Creates an HTTP request from the builder data.
         * @return
         * @throws IOException
         */
        HttpRequest build() throws IOException;

    }
}

package io.tus.java.client;

import java.io.IOException;
import java.io.InputStream;

/**
 * <code>HttpRequest</code> represents an HTTP request that is sent by the {@link HttpProvider}.
 */
public interface HttpRequest {

    interface Builder {

        /**
         * Adds header data to the HttpRequest
         * @param key - key for header
         * @param value - value for header
         */
        void addHeader(String key, String value);

        void setBody(InputStream body);

        HttpRequest get() throws IOException;

        HttpRequest head() throws IOException;

        HttpRequest options() throws IOException;

        HttpRequest post() throws IOException;

        HttpRequest put() throws IOException;

        HttpRequest patch() throws IOException;

        HttpRequest build() throws IOException;

    }
}

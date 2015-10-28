package io.tus.java.client.provider;

import java.io.IOException;
import java.net.HttpURLConnection;

import io.tus.java.client.HttpResponse;

/**
 * Created by findyr-akaplan on 10/27/15.
 */
public class HttpUrlResponse implements HttpResponse {

    private final HttpURLConnection connection;

    public HttpUrlResponse(HttpURLConnection connection) {
        this.connection = connection;
    }

    @Override
    public int getResponseCode() {
        try {
            return this.connection.getResponseCode();
        } catch (IOException e) {
            return 0;
        }
    }

    @Override
    public String getHeader(String key) {
        return this.connection.getHeaderField(key);
    }
}

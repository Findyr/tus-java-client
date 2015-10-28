package io.tus.java.client.provider;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import io.tus.java.client.HttpRequest;

/**
 * <code>HttpUrlRequest</code> implements the {@link HttpRequest} interface using {@link
 * HttpURLConnection} connections.
 */
public class HttpUrlRequest implements HttpRequest {

    private HttpURLConnection connection;

    private InputStream body;

    public HttpUrlRequest(HttpURLConnection connection, InputStream body) {
        this.connection = connection;
        this.body = body;
    }

    @Override
    public InputStream getBody() {
        return this.body;
    }

    @Override
    public String getHeader(String key) {
        return this.connection.getRequestProperty(key);
    }

    HttpURLConnection getConnection() {
        return this.connection;
    }

    public static class Builder implements HttpRequest.Builder {
        URL url;
        Map<String, String> headers;
        String requestMethod;
        InputStream body;

        public Builder(URL url) {
            this.url = url;
            this.headers = new HashMap<>();
        }

        @Override
        public HttpRequest.Builder addHeader(String header, String data) {
            this.headers.put(header, data);
            return this;
        }

        @Override
        public HttpRequest.Builder setBody(InputStream body) {
            this.body = body;
            return this;
        }

        @Override
        public HttpRequest.Builder setRequestMethod(String method) {
            this.requestMethod = method;
            return this;
        }

        @Override
        public HttpRequest get() throws IOException {
            this.requestMethod = "GET";
            return this.build();
        }

        @Override
        public HttpRequest head() throws IOException {
            this.requestMethod = "HEAD";
            return this.build();
        }

        @Override
        public HttpRequest options() throws IOException {
            this.requestMethod = "OPTIONS";
            return this.build();
        }

        public HttpRequest post() throws IOException{
            this.requestMethod = "POST";
            return this.build();
        }

        @Override
        public HttpRequest put() throws IOException {
            this.requestMethod = "PUT";
            return this.build();
        }

        @Override
        public HttpRequest patch() throws IOException {
            this.requestMethod = "PATCH";
            return this.build();
        }

        public HttpRequest build() throws IOException {
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            for (Map.Entry<String, String> entry : this.headers.entrySet()) {
                connection.addRequestProperty(entry.getKey(), entry.getValue());
            }
            try {
                connection.setRequestMethod(this.requestMethod);
            } catch (ProtocolException e) {
                connection.setRequestMethod("POST");
                connection.addRequestProperty("X-HTTP-Method-Override", this.requestMethod);
            }
            return new HttpUrlRequest(connection, this.body);
        }
    }
}

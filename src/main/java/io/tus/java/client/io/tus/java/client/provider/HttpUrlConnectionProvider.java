package io.tus.java.client.io.tus.java.client.provider;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import io.tus.java.client.HttpProvider;
import io.tus.java.client.HttpRequest;
import io.tus.java.client.HttpResponse;

/**
 * Created by findyr-akaplan on 10/27/15.
 */
public class HttpUrlConnectionProvider implements HttpProvider {

    @Override
    public HttpRequest.Builder getRequestBuilder(URL url) {
        return new HttpUrlRequest.Builder(url);
    }

    @Override
    public HttpResponse executeRequest(HttpRequest request) throws IOException {
        if (request instanceof HttpUrlRequest) {
            HttpUrlRequest urlRequest = (HttpUrlRequest) request;
            HttpURLConnection connection = urlRequest.getConnection();
            if (urlRequest.getBody() != null) {
                byte[] buffer = new byte[1024];
                int bytesRead = 0;
                OutputStream out = new BufferedOutputStream(connection.getOutputStream());
                while (bytesRead > -1) {
                    bytesRead = urlRequest.getBody().read(buffer);
                    out.write(buffer, 0, bytesRead);
                    out.flush();
                }
            }
            connection.connect();
            return new HttpUrlResponse(connection);
        } else {
            throw new IllegalArgumentException("request is not an instance of HttpUrlRequest");
        }
    }
}

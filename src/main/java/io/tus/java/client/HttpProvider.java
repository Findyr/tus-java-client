package io.tus.java.client;

import java.io.IOException;
import java.net.URL;

/**
 * An <code>HttpProvider</code> represents an HTTP client that can submit HTTP requests and read
 * HTTP responses. Providers must be capable of creating an {@link io.tus.java.client.HttpRequest
 * .Builder} to facilitate the creation of HTTP requests.
 */
public interface HttpProvider {

    HttpRequest.Builder getRequestBuilder(URL url);

    HttpResponse executeRequest(HttpRequest request) throws IOException;
}

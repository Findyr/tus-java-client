package io.tus.java.client;

import java.io.IOException;
import java.net.URL;

/**
 * Created by findyr-akaplan on 10/27/15.
 */
public interface HttpProvider {

    HttpRequest.Builder getRequestBuilder(URL url);

    HttpResponse executeRequest(HttpRequest request) throws IOException;
}

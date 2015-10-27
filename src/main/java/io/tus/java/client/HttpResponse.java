package io.tus.java.client;

/**
 * Created by findyr-akaplan on 10/27/15.
 */
public interface HttpResponse {

    int getResponseCode();

    String getHeader(String location);
}
